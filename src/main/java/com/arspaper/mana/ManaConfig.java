package com.arspaper.mana;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * マナシステムの設定値。
 * config.ymlから読み込む。
 *
 * <p>2026-07-25 (config editor T2): {@code default-max} / {@code default-regen-rate} /
 * {@code regen-interval-ticks} / {@code recovery.*}(on-hit/on-attack/idle 系7項目)は
 * {@code config.yml} から削除し、TrinityForge の「プレイヤー基礎ステータス」
 * ({@code combat/base-stats.yml})へ移設した。読み出しは {@link ManaManager}/
 * {@link ManaRecoveryListener} が {@link ManaBaseStats} 経由で都度取得する
 * (TF側の {@code /trinityforge reload} に即追随させるため、この record には焼き込まない)。
 */
public record ManaConfig(
    int manaPerGlyphUnlock,
    // マナ最大値%上昇の上限（%）
    int maxPercentCap,
    // 要件⑥ source-auto-consume: マナ不足時にインベントリから消費してマナへ変換するアイテム。
    // itemId(Arsカスタムid または TFカタログid) -> マナ変換量とCT。
    Map<String, SourceAutoConsumeItem> sourceAutoConsumeItems,
    // 2026-08-14 追加: 自動消費のクールタイム(秒)。0以下でCT無し(従来挙動)。
    // スキルツリー ars_smithing.yml A-2「ソースベリー活用」の説明文は当初から「100マナ/10CT」と
    // 書いてあったが、CT判定は一度も実装されておらずマナ不足のたびに無制限に変換できていた。
    // アイテム側で cooldown-seconds を書いていない場合の既定値として使う。
    int sourceAutoConsumeCooldownSeconds
) {
    /** CT未設定時の既定値(秒)。ノード説明「100マナ/10CT」の 10 をそのまま秒として採る。 */
    public static final int DEFAULT_SOURCE_AUTO_CONSUME_COOLDOWN_SECONDS = 10;

    /**
     * 自動消費アイテム1件分の設定。
     *
     * <p>2026-08-14: CTを全体1本からアイテム単位へ拡張した(ユーザー指示「マナ回復量とCTが
     * それぞれ設定できるべき」)。CTはアイテムごとに独立して進むので、ソースベリーを使った直後でも
     * 別アイテムは即使える。
     *
     * @param manaPerItem     1個あたりのマナ変換量(正の整数)
     * @param cooldownSeconds このアイテム専用のCT(秒)。{@code null} なら全体既定
     *                        ({@link #sourceAutoConsumeCooldownSeconds})を使う。
     *                        <b>0 は「CT無し」という別の意味</b>なので null と混同しないこと。
     */
    public record SourceAutoConsumeItem(int manaPerItem, Integer cooldownSeconds) {
        /** 全体既定を当てはめた実効CT(秒)。 */
        public int effectiveCooldownSeconds(int defaultCooldownSeconds) {
            return cooldownSeconds != null ? Math.max(0, cooldownSeconds) : Math.max(0, defaultCooldownSeconds);
        }
    }

    public static ManaConfig fromConfig(FileConfiguration config) {
        return new ManaConfig(
            config.getInt("mana.per-glyph-unlock-bonus", 5),
            // 既存挙動を変えない安全デフォルト（上昇上限100%）。
            clampPercent(config.getInt("mana.max-percent-cap", 100)),
            parseSourceAutoConsumeItems(config),
            // 負値は0(CT無し)として扱う。上限は設けない(運用で長いCTを置きたい場合がある)。
            Math.max(0, config.getInt("mana.source-auto-consume.cooldown-seconds",
                DEFAULT_SOURCE_AUTO_CONSUME_COOLDOWN_SECONDS))
        );
    }

    /**
     * {@code mana.source-auto-consume.items} をパースする。
     * 非正値/id空欄のエントリは警告してスキップする（設定ミスで消費0個変換が成立しないよう防ぐ）。
     *
     * <p>2種類の記法を読む。<b>旧記法(数値のみ)を読めなくすると、既存 config.yml の自動消費が
     * まるごと無効化される</b>ので必ず両方扱う:
     * <pre>
     *   source_berry: 100                 # 旧記法: マナ変換量のみ。CTは全体既定
     *   source_berry:                     # 新記法(2026-08-14): アイテムごとにCTを持てる
     *     mana: 100
     *     cooldown-seconds: 5             # 省略時は全体既定。0 は「CT無し」
     * </pre>
     */
    private static Map<String, SourceAutoConsumeItem> parseSourceAutoConsumeItems(FileConfiguration config) {
        Map<String, SourceAutoConsumeItem> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("mana.source-auto-consume.items");
        if (section == null) {
            return result;
        }
        for (String rawId : section.getKeys(false)) {
            if (rawId == null || rawId.isBlank()) {
                Bukkit.getLogger()
                    .warning("[ArsPaper] mana.source-auto-consume.items に空のidが指定されました。スキップします。");
                continue;
            }
            String id = normalizeItemId(rawId);
            if (id.isEmpty()) {
                Bukkit.getLogger().warning(
                    "[ArsPaper] mana.source-auto-consume.items の '" + rawId + "' は id が空です。スキップします。");
                continue;
            }
            if (result.containsKey(id)) {
                Bukkit.getLogger().warning(
                    "[ArsPaper] mana.source-auto-consume.items に '" + id
                        + "' が重複しています(custom: 有無の違いを含む)。先に書かれた方を使います: " + rawId);
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(rawId);
            int manaPerItem = entry != null ? entry.getInt("mana", 0) : section.getInt(rawId, 0);
            if (manaPerItem <= 0) {
                Bukkit.getLogger().warning(
                    "[ArsPaper] mana.source-auto-consume.items." + id
                        + " のマナ変換量は正の整数である必要があります。スキップします: " + manaPerItem);
                continue;
            }
            // キーが無い(=全体既定に従う)のと 0(=CT無し)を区別するため contains で見る。
            Integer cooldownSeconds = null;
            if (entry != null && entry.contains("cooldown-seconds")) {
                int raw = entry.getInt("cooldown-seconds", DEFAULT_SOURCE_AUTO_CONSUME_COOLDOWN_SECONDS);
                if (raw < 0) {
                    Bukkit.getLogger().warning(
                        "[ArsPaper] mana.source-auto-consume.items." + id
                            + ".cooldown-seconds は0以上である必要があります。0(CT無し)として扱います: " + raw);
                }
                cooldownSeconds = Math.max(0, raw);
            }
            result.put(id, new SourceAutoConsumeItem(manaPerItem, cooldownSeconds));
        }
        return result;
    }

    /**
     * 設定キーのアイテムidを、実行時に照合する形へ正規化する。
     *
     * <p><b>2026-08-14 バグ修正</b>: 出荷 config.yml は {@code custom:source_berry: 100} と書かれていたが、
     * 照合側の {@code PdcHelper#getCrossPluginItemId} が返すのは PDC に入っている素のid
     * ({@code source_berry}) なので<b>1件も一致せず、ソース自動消費は一度も発動していなかった</b>。
     * 設定エディタのアイテム選択UI(materialInput)がカスタム品を {@code custom:} 付きで書き出すため、
     * 人手で直しても編集し直すたびに戻る。読み込み側で落とすのが唯一の恒久策。
     *
     * <p>{@code list:} は落とさない ── 互換リストはレシピ素材の語彙で、ここでは1個のアイテムidしか
     * 意味を持たないため、書かれていたら「一致しないid」として扱う方が誤爆しない。
     */
    public static String normalizeItemId(String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.regionMatches(true, 0, "custom:", 0, "custom:".length())) {
            id = id.substring("custom:".length()).trim();
        }
        return id;
    }

    /** %系設定を 0..1000 にクランプする。 */
    private static int clampPercent(int value) {
        return Math.max(0, Math.min(1000, value));
    }
}
