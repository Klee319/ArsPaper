package com.arspaper.integration;

import com.trinityforge.TrinityForge;
import com.trinityforge.config.domains.ItemStatsConfig;
import com.trinityforge.stats.DerivedItemStats;
import com.trinityforge.stats.QualityRollModel;
import com.trinityforge.stats.StatDisplaySpec;
import com.trinityforge.stats.StatKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * レシピ一覧（{@code RecipeBrowserGui}）向けに、TrinityForge の item-stats から
 * <b>最低品質でのステータス下限</b>と<b>最上位カテゴリ</b>を取り出す読み取り専用ブリッジ（N4）。
 *
 * <p><b>「最低ステータス」の定義（TF の実装から）</b>:
 * item-stats のステは {@code fixed}（固定）＋ {@code per-quality × 品質}＋ {@code random}
 * （{@code min..max} を品質依存の分布で抽選）の3層。したがって取り得る最小値は
 * <b>品質0 かつ抽選が下限に張り付いたとき</b>で、{@code fixed + random.min} になる。
 * これを {@link DerivedItemStats#profileStats} に
 * 「品質0・ばらつき0・中心オフセット0」の {@link QualityRollModel} を渡して求める
 * （σ=0 なら {@code reach = mode = 0} に固定され、{@code range.valueAt(0) = min} になる）。
 * 抽選の乱数(rollSeed)には依存しないので、誰が見ても同じ値になる。
 *
 * <p>TF 未ロード・アイテム未登録・例外はすべて「表示しない」に倒す（fail-soft）。
 * ArsPaper は TrinityForge 無しでも動く必要があるため、ここで落ちてはいけない。
 */
public final class TrinityForgeStatPreview {

    /**
     * 残り行数を渡さずに呼んだときの上限。<b>ツールチップの余白を知っているのは呼び出し側</b>なので、
     * GUI からは {@link #minimumStatLines(ItemStack, int)} を使うこと。
     *
     * <p>出荷 item-stats で表示対象になるステが一番多いアイテムでも 17 件なので、
     * この既定値は「短い lore なら大半が出る」程度の保険にすぎない。
     */
    private static final int DEFAULT_MAX_LINES = 12;

    /**
     * lore がどれだけ長くても最低これだけは出す。
     *
     * <p>0 まで許すと<b>見出しだけ出して中身が1行も無い</b>ブロックができる
     * (呼び出し側は「1行も出ない」ではなく「見出し＋…ほか」を作ってしまう)。
     */
    private static final int MIN_LINES = 4;

    private TrinityForgeStatPreview() {
    }

    /**
     * TF item-stats の最上位カテゴリ（{@code weapon/armor/tool/catalyst/spellbook/thread/other}）。
     * 未登録・TF未ロードなら null。
     */
    public static String topLevelCategory(ItemStack probe) {
        if (probe == null || probe.getType().isAir()) {
            return null;
        }
        try {
            ItemStatsConfig itemStats = itemStats();
            if (itemStats == null) {
                return null;
            }
            return itemStats.topLevelCategoryFor(probe.getType(), customModelDataOf(probe))
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 最低品質（品質0）で保証されるステータスの lore 行。付くステが1つも無ければ空リスト。
     * 1行目は「どの品質の話か」の見出しで、以降が {@code アイコン 表示名 値} の行。
     */
    public static List<Component> minimumStatLines(ItemStack probe) {
        return minimumStatLines(probe, DEFAULT_MAX_LINES);
    }

    /**
     * 同上。<b>ステ行に使ってよい行数</b>を呼び出し側が渡す版(2026-08-22 ユーザー報告
     * 「まだloreの長さに余裕があるのに省略されてしまい、スレッド枠やマナ回復量などが出ていない」)。
     *
     * <p>以前は固定で 8 行だった。ところがツールチップの高さを決めるのは<b>lore 全体</b>なので、
     * 短い lore のアイテムでも 8 行で頭打ちになり、画面に十分な余白があるのに
     * {@code thread-slots} / {@code mana-bonus} / {@code mana-regen} のように
     * 表示順が後ろのステだけが常に「…ほか」へ落ちていた
     * (表示順は lore.yml の category → order なので、同じステが毎回こぼれる)。
     *
     * @param maxLines ステ行に使える残り行数。{@link #MIN_LINES} 未満は切り上げる。
     */
    public static List<Component> minimumStatLines(ItemStack probe, int maxLines) {
        Map<String, Double> floor = minimumStats(probe);
        if (floor.isEmpty()) {
            return List.of();
        }
        int limit = lineLimit(maxLines);
        List<Component> lines = new ArrayList<>();
        lines.add(header());
        int shown = 0;
        for (StatDisplaySpec spec : orderedSpecs()) {
            Double value = floor.get(StatKeys.canonical(spec.statKey()));
            if (value == null || !Double.isFinite(value)) continue;
            if (spec.hideWhenZero() && value == 0.0) continue;
            if (shown >= limit) {
                lines.add(text("  …ほか", NamedTextColor.DARK_GRAY));
                break;
            }
            String icon = spec.icon().isBlank() ? "" : spec.icon() + " ";
            lines.add(text("  " + icon + spec.displayName() + " " + spec.renderValue(value),
                    NamedTextColor.GRAY));
            shown++;
        }
        // 見出ししか出せなかった（lore.yml に表示定義が無いステだけだった）なら何も出さない
        return shown == 0 ? List.of() : lines;
    }

    /** 実際に使う上限。{@link #MIN_LINES} 未満は切り上げる(理由はその javadoc)。 */
    public static int lineLimit(int maxLines) {
        return Math.max(MIN_LINES, maxLines);
    }

    /**
     * 最低品質でのステ下限（canonical key → 値）。TF未ロード・未登録・例外時は空 Map。
     * 公開しているのはテストと将来の再利用のため。
     */
    public static Map<String, Double> minimumStats(ItemStack probe) {
        if (probe == null || probe.getType().isAir()) {
            return Map.of();
        }
        try {
            ItemStatsConfig itemStats = itemStats();
            if (itemStats == null) {
                return Map.of();
            }
            Material material = probe.getType();
            Integer cmd = customModelDataOf(probe);
            if (itemStats.profileFor(material, cmd).isEmpty()) {
                return Map.of();
            }
            Map<String, Double> stats = DerivedItemStats.profileStats(
                    material, cmd, 0, 0L, itemStats, floorRollModel(itemStats));
            return stats == null ? Map.of() : new LinkedHashMap<>(stats);
        } catch (Throwable ignored) {
            return Map.of();
        }
    }

    /**
     * 抽選が必ず下限に張り付くロールモデル。上下のばらつき σ=0・中心オフセット0 なので
     * 品質0での {@code reach} は常に 0 になる（＝{@code random} 層は {@code min} をそのまま加算）。
     * 段数({@code maxQuality})だけは実 config から引き継ぐ。
     */
    private static QualityRollModel floorRollModel(ItemStatsConfig itemStats) {
        QualityRollModel live = itemStats.rollModel();
        int maxQuality = live == null ? 0 : live.maxQuality();
        return new QualityRollModel(maxQuality, 0.0, 0.0, 0.0);
    }

    /** 見出し行。品質ティア名（既定では「劣悪」）は quality-tiers.yml の先頭から引く。 */
    private static Component header() {
        String tier = lowestTierName();
        String label = tier == null ? "最低品質" : "【" + tier + "】品質";
        return text(label + "でも保証されるステータス", NamedTextColor.DARK_AQUA);
    }

    private static String lowestTierName() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) return null;
            return tf.config().qualityTiers().tierFor(0).map(t -> t.name()).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** lore.yml の表示定義を「カテゴリ → order → キー」の順に並べたもの。 */
    private static List<StatDisplaySpec> orderedSpecs() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) return List.of();
            List<StatDisplaySpec> specs = new ArrayList<>(tf.config().lore().displayTable().values());
            specs.sort(Comparator.<StatDisplaySpec, Integer>comparing(s -> s.category().ordinal())
                    .thenComparingInt(StatDisplaySpec::order)
                    .thenComparing(StatDisplaySpec::statKey));
            return specs;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static ItemStatsConfig itemStats() {
        TrinityForge tf = TrinityForge.getInstance();
        return tf == null ? null : tf.config().itemStats();
    }

    private static Integer customModelDataOf(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? null : DerivedItemStats.customModelDataOf(meta);
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
