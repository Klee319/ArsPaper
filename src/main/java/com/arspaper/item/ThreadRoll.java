package com.arspaper.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * スレッド1個の厳選結果（主ステ1つ + サブステ0〜4つ + レア度）。
 *
 * <p><b>なぜアイテム側に焼き込むのか</b>: スレッドの戦闘ステはこれまで
 * {@code TrinityForgeBridge.resolveItemStats(material, cmd)} だけで決まっていた ── つまり同じ種類の
 * スレッドは全部まったく同じ性能だった。個体差を出すには「そのアイテム固有の値」を持つしかないので、
 * 生成時に PDC へ書き込む。<b>設定（thread-rolls.yml）の min/max を後から変えても既存個体は変わらない</b>
 * （焼き込み方式の必然。振り直したいときは儀式 {@code thread_reroll} を使う）。
 *
 * <p><b>保存形式</b>: {@code "<rarityId>|<mainKey>=<value>|<subKey>=<value>;<subKey>=<value>"}。
 * JSON にしないのは、防具側が同じ文字列をスロット数ぶん配列で持つため（{@link ItemKeys#THREAD_SLOT_ROLLS}）
 * 短いほうが PDC が膨らまないから。区切り文字（{@code | ; =}）は canonical なステキーに現れない。
 *
 * <p>壊れた文字列は<b>例外を投げず空として扱う</b>（fail-open）。厳選が読めないだけで防具の
 * マナ/ポーション/飛行が止まってはいけない。
 */
public record ThreadRoll(String rarityId, Map<String, Double> mainStat, Map<String, Double> subStats) {

    private static final char PART = '|';
    private static final char ENTRY = ';';
    private static final char ASSIGN = '=';

    public ThreadRoll {
        mainStat = Map.copyOf(mainStat);
        subStats = Map.copyOf(subStats);
    }

    /** 主ステ + サブステを1つのマップへ（同じキーは合算。抽選は排他なので通常は起こらない）。 */
    public Map<String, Double> allStats() {
        Map<String, Double> out = new LinkedHashMap<>(mainStat);
        subStats.forEach((key, value) -> out.merge(key, value, Double::sum));
        return out;
    }

    public String encode() {
        return rarityId + PART + encodeStats(mainStat) + PART + encodeStats(subStats);
    }

    private static String encodeStats(Map<String, Double> stats) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            if (entry.getValue() == null || !Double.isFinite(entry.getValue()) || entry.getValue() == 0.0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(ENTRY);
            }
            out.append(entry.getKey()).append(ASSIGN).append(entry.getValue());
        }
        return out.toString();
    }

    /** 壊れていれば {@link Optional#empty()}。部分的に壊れたトークンはそのトークンだけ捨てる。 */
    public static Optional<ThreadRoll> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        String[] parts = encoded.split("\\" + PART, -1);
        if (parts.length < 3) {
            return Optional.empty();
        }
        String rarity = parts[0].trim();
        if (rarity.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ThreadRoll(rarity, decodeStats(parts[1]), decodeStats(parts[2])));
    }

    private static Map<String, Double> decodeStats(String raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String token : raw.split(String.valueOf(ENTRY))) {
            int at = token.indexOf(ASSIGN);
            if (at <= 0 || at == token.length() - 1) {
                continue;
            }
            String key = token.substring(0, at).trim();
            if (key.isEmpty()) {
                continue;
            }
            try {
                double value = Double.parseDouble(token.substring(at + 1).trim());
                if (Double.isFinite(value) && value != 0.0) {
                    out.merge(key, value, Double::sum);
                }
            } catch (NumberFormatException malformed) {
                // このトークンだけ捨てる（他のステは活かす）。
            }
        }
        return out;
    }

    /**
     * 集計専用の高速経路。{@link #decode} してから {@link #allStats} を呼ぶのと同じだが、
     * 壊れていても常にマップを返すので呼び出し側で Optional を開かずに済む。
     */
    public static Map<String, Double> statsOf(String encoded) {
        return decode(encoded).map(ThreadRoll::allStats).orElseGet(LinkedHashMap::new);
    }

    // === PDC ===

    /** スレッドアイテムへ厳選結果を書き込む（{@code null} 指定でキーを消す）。 */
    public static void write(PersistentDataContainer pdc, ThreadRoll roll) {
        if (roll == null) {
            pdc.remove(ItemKeys.THREAD_ROLL);
            return;
        }
        pdc.set(ItemKeys.THREAD_ROLL, PersistentDataType.STRING, roll.encode());
    }

    /** スレッドアイテムの厳選結果（未厳選/壊れている場合は空文字）。 */
    public static String rawOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.THREAD_ROLL, PersistentDataType.STRING);
        return raw == null ? "" : raw;
    }

    // === 表示 ===

    /**
     * lore 行を作る。{@code percentKeys} は thread-rolls.yml の {@code percent: true} 集合
     * （率として表示するキー）。config が読めないときは空集合を渡せばよい（素の数値で出る）。
     */
    public List<Component> lore(NamedTextColor rarityColor, String rarityLabel, java.util.Set<String> percentKeys) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("[" + rarityLabel + "]", rarityColor)
                .decoration(TextDecoration.ITALIC, false));
        mainStat.forEach((key, value) -> lore.add(
                Component.text("  ◆ " + label(key) + " " + format(key, value, percentKeys), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));
        subStats.forEach((key, value) -> lore.add(
                Component.text("  ・ " + label(key) + " " + format(key, value, percentKeys), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        return lore;
    }

    private static String format(String key, double value, java.util.Set<String> percentKeys) {
        if (percentKeys.contains(key)) {
            return String.format(Locale.ROOT, "+%.1f%%", value * 100.0);
        }
        return value == Math.rint(value)
                ? String.format(Locale.ROOT, "+%.0f", value)
                : String.format(Locale.ROOT, "+%.1f", value);
    }

    /** ステキーの日本語表示名。未知キーはキーのまま出す（設定に新しいキーを書いても壊れない）。 */
    private static String label(String key) {
        return switch (key) {
            case "attack-power" -> "攻撃力";
            case "flat-bonus-damage" -> "追加ダメージ";
            case "percent-bonus-damage" -> "追加ダメージ率";
            case "crit-chance" -> "会心率";
            case "crit-damage" -> "会心ダメージ";
            case "penetration" -> "貫通";
            case "bleed-chance" -> "出血率";
            case "bleed-damage" -> "出血ダメージ";
            case "phys-resistance" -> "物理耐性";
            case "magic-resistance" -> "魔法耐性";
            case "phys-flat-defense" -> "物理守備力";
            case "magic-flat-defense" -> "魔法守備力";
            case "damage-reduction" -> "被ダメージ軽減";
            case "dodge-chance" -> "回避率";
            case "armor-strength" -> "防具強度";
            default -> key;
        };
    }
}
