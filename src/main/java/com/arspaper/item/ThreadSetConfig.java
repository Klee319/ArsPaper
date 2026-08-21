package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * thread-sets.yml を読み込み、スレッドの【同種合計個数】に応じた累積セット効果ボーナスを供給する。
 *
 * <p><b>2026-08-21 追加: 乗算モード。</b> しきい値の1ステは次の2つの書き方を取れる。
 * <pre>
 *   crit-chance: 0.03                              # 加算モード(従来): 総合値へ +0.03
 *   attack-power: { mode: multiply, value: 0.10 }  # 乗算モード: 総合値を ×1.10
 * </pre>
 * 加算分は {@link #cumulativeBonus}、乗算分は {@link #cumulativeMultiplier} が返す。
 * <b>攻撃力のように帯(進行度)で桁が変わるステは必ず乗算モードで配ること</b> ——
 * 固定値で配ると装備の弱い低帯ほど相対的に巨大になり、TF 側
 * {@code ShippedThreadBandIndependenceTest} の「スレッド1本ぶんのダメージ倍率は帯に依らず一定」
 * という性質を壊す。乗算値は TF の既存乗算レイヤ({@code PlayerCombatAggregate#multiplierFor})
 * へ１レイヤとして合流するので、加算合算 → 乗算 → stat-cap の順序は既存のまま。
 *
 * <p>各スレッド1個ごとの個別ステは TrinityForge の stats/item-stats.yml (MATERIAL#CMD) 側にあり、ここでは
 * 扱わない。ここは「同種スレッドがN個 → 累積しきい値ボーナス」だけを定義する。加算モデルは【累積しきい値式】:
 * しきい値 &le; N の全ボーナスを合算する({@link #cumulativeBonus})。
 *
 * <p>ステキーは canonical 化せず YAML 記載のまま(生キー)保持する ── canonical 化と最終合算は書込み時に
 * TrinityForge 側の {@code AddonCombatStats.encode}/{@code parse} が行うため、この loader は TrinityForge
 * クラスへ一切依存しない(TF 未ロードでも安全にロード可能)。
 */
public class ThreadSetConfig {

    public static final String FILE_NAME = "thread-sets.yml";
    private static final String ROOT = "thread-sets";

    private final JavaPlugin plugin;
    /** 乗算モードを表す YAML キー(値は {@code "multiply"})。省略/その他は加算モード。 */
    private static final String MODE_KEY = "mode";
    private static final String VALUE_KEY = "value";
    private static final String MODE_MULTIPLY = "multiply";

    // threadId -> (個数しきい値 昇順 -> そのしきい値で付与する 生キー ステMap)
    private final Map<String, NavigableMap<Integer, Map<String, Double>>> sets = new HashMap<>();
    // 同上の乗算モード版(値は倍率の増分。0.1 = +10%)
    private final Map<String, NavigableMap<Integer, Map<String, Double>>> multiplierSets = new HashMap<>();

    public ThreadSetConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        sets.clear();
        multiplierSets.clear();
        load();
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root == null) {
            return;
        }
        for (String threadId : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(threadId);
            if (entry == null) {
                continue;
            }
            ConfigurationSection thresholds = entry.getConfigurationSection("thresholds");
            if (thresholds == null) {
                continue;
            }
            NavigableMap<Integer, Map<String, Double>> tiers = new TreeMap<>();
            NavigableMap<Integer, Map<String, Double>> multiplierTiers = new TreeMap<>();
            for (String countKey : thresholds.getKeys(false)) {
                int count;
                try {
                    count = Integer.parseInt(countKey.trim());
                } catch (NumberFormatException notInt) {
                    plugin.getLogger().warning("[" + FILE_NAME + "] " + threadId
                            + ": しきい値 '" + countKey + "' が整数でないためスキップ");
                    continue;
                }
                if (count <= 0) {
                    continue;
                }
                ConfigurationSection statSection = thresholds.getConfigurationSection(countKey);
                if (statSection == null) {
                    continue;
                }
                Map<String, Double> stats = new LinkedHashMap<>();
                Map<String, Double> multipliers = new LinkedHashMap<>();
                for (String stat : statSection.getKeys(false)) {
                    double value;
                    boolean multiply = false;
                    if (statSection.isDouble(stat) || statSection.isInt(stat)) {
                        value = statSection.getDouble(stat);
                    } else {
                        // 乗算モード: { mode: multiply, value: 0.10 }。mode 省略なら加算モード扱い。
                        ConfigurationSection modeSection = statSection.getConfigurationSection(stat);
                        if (modeSection == null
                                || !(modeSection.isDouble(VALUE_KEY) || modeSection.isInt(VALUE_KEY))) {
                            plugin.getLogger().warning("[" + FILE_NAME + "] " + threadId + "/" + count
                                    + ": ステ '" + stat + "' が数値でも { mode, value } でもないためスキップ");
                            continue;
                        }
                        value = modeSection.getDouble(VALUE_KEY);
                        multiply = MODE_MULTIPLY.equalsIgnoreCase(modeSection.getString(MODE_KEY, ""));
                    }
                    if (!Double.isFinite(value) || value == 0.0) {
                        continue;
                    }
                    (multiply ? multipliers : stats).put(stat, value);
                }
                if (!stats.isEmpty()) {
                    tiers.put(count, stats);
                }
                if (!multipliers.isEmpty()) {
                    multiplierTiers.put(count, multipliers);
                }
            }
            if (!tiers.isEmpty()) {
                sets.put(threadId, tiers);
            }
            if (!multiplierTiers.isEmpty()) {
                multiplierSets.put(threadId, multiplierTiers);
            }
        }
    }

    /**
     * 同種スレッドが {@code count} 個装備されているときの累積セット効果(生キー stat -&gt; 合計値)。
     * しきい値 &le; count の全ティアを合算する。該当なし/未設定/count&le;0 は空Map。
     */
    public Map<String, Double> cumulativeBonus(String threadId, int count) {
        return cumulative(sets, threadId, count);
    }

    /**
     * 同種スレッドが {@code count} 個装備されているときの累積<b>乗算</b>セット効果
     * (生キー stat -&gt; 倍率の増分の合計。0.1 = +10%)。{@link #cumulativeBonus} と同じ
     * 累積しきい値式で、しきい値 &le; count の全ティアを<b>加算</b>で合算する
     * (レイヤ内は Σ(v-1) を足す、という TF 側 {@code PlayerCombatAggregate} の合成規則に合わせる)。
     */
    public Map<String, Double> cumulativeMultiplier(String threadId, int count) {
        return cumulative(multiplierSets, threadId, count);
    }

    /**
     * 表示用: このスレッドに定義されている全しきい値(個数)を昇順で返す。累積ではなく
     * <b>「その段で新たに足される分」</b>を lore に出すための入口 ——
     * 累積値を出すと「3個: +3% / 5個: +8%」のように読み手が差分を暗算する羽目になる。
     * 未定義スレッドは空。
     */
    public java.util.List<Integer> thresholds(String threadId) {
        java.util.SortedSet<Integer> counts = new java.util.TreeSet<>();
        NavigableMap<Integer, Map<String, Double>> add = sets.get(threadId);
        if (add != null) {
            counts.addAll(add.keySet());
        }
        NavigableMap<Integer, Map<String, Double>> mul = multiplierSets.get(threadId);
        if (mul != null) {
            counts.addAll(mul.keySet());
        }
        return java.util.List.copyOf(counts);
    }

    /** 表示用: しきい値 {@code count} の段<b>単体</b>の加算ステ(累積ではない)。 */
    public Map<String, Double> bonusAt(String threadId, int count) {
        return tierAt(sets, threadId, count);
    }

    /** 表示用: しきい値 {@code count} の段<b>単体</b>の乗算ステ(倍率の増分。0.1 = +10%)。 */
    public Map<String, Double> multiplierAt(String threadId, int count) {
        return tierAt(multiplierSets, threadId, count);
    }

    private static Map<String, Double> tierAt(
            Map<String, NavigableMap<Integer, Map<String, Double>>> source, String threadId, int count) {
        NavigableMap<Integer, Map<String, Double>> tiers = source.get(threadId);
        if (tiers == null) {
            return Map.of();
        }
        Map<String, Double> tier = tiers.get(count);
        return tier == null ? Map.of() : tier;
    }

    private static Map<String, Double> cumulative(
            Map<String, NavigableMap<Integer, Map<String, Double>>> source, String threadId, int count) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (threadId == null || count <= 0) {
            return out;
        }
        NavigableMap<Integer, Map<String, Double>> tiers = source.get(threadId);
        if (tiers == null) {
            return out;
        }
        for (Map<String, Double> tier : tiers.headMap(count, true).values()) {
            tier.forEach((key, value) -> out.merge(key, value, Double::sum));
        }
        return out;
    }
}
