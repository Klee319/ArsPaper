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
    // threadId -> (個数しきい値 昇順 -> そのしきい値で付与する 生キー ステMap)
    private final Map<String, NavigableMap<Integer, Map<String, Double>>> sets = new HashMap<>();

    public ThreadSetConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        sets.clear();
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
                for (String stat : statSection.getKeys(false)) {
                    if (!statSection.isDouble(stat) && !statSection.isInt(stat)) {
                        plugin.getLogger().warning("[" + FILE_NAME + "] " + threadId + "/" + count
                                + ": ステ '" + stat + "' が数値でないためスキップ");
                        continue;
                    }
                    double value = statSection.getDouble(stat);
                    if (!Double.isFinite(value) || value == 0.0) {
                        continue;
                    }
                    stats.put(stat, value);
                }
                if (!stats.isEmpty()) {
                    tiers.put(count, stats);
                }
            }
            if (!tiers.isEmpty()) {
                sets.put(threadId, tiers);
            }
        }
    }

    /**
     * 同種スレッドが {@code count} 個装備されているときの累積セット効果(生キー stat -&gt; 合計値)。
     * しきい値 &le; count の全ティアを合算する。該当なし/未設定/count&le;0 は空Map。
     */
    public Map<String, Double> cumulativeBonus(String threadId, int count) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (threadId == null || count <= 0) {
            return out;
        }
        NavigableMap<Integer, Map<String, Double>> tiers = sets.get(threadId);
        if (tiers == null) {
            return out;
        }
        for (Map<String, Double> tier : tiers.headMap(count, true).values()) {
            tier.forEach((key, value) -> out.merge(key, value, Double::sum));
        }
        return out;
    }
}
