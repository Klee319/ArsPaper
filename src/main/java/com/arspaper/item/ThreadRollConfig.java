package com.arspaper.item;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * thread-rolls.yml を読み、スレッド1個ぶんの厳選結果（{@link ThreadRoll}）を抽選する。
 *
 * <p>抽選は「レア度 → 主ステ1つ → サブ本数 → サブを重複なしで本数分」の順。レア度の
 * {@code multiplier} は<b>主ステだけ</b>に掛かる（サブにも掛けると当たり個体の格差が二乗になる）。
 *
 * <p>設定が壊れている / {@code enabled: false} / 候補が空のときは {@link #roll(Random)} が
 * {@link Optional#empty()} を返す ── そのときスレッドは<b>厳選なしの従来どおりの挙動</b>になる
 * （厳選が読めないだけでスレッド自体が作れなくなってはいけない）。
 */
public class ThreadRollConfig {

    public static final String FILE_NAME = "thread-rolls.yml";

    /** 抽選候補1件。{@code percent} は表示用（保存値は常に生の数値）。 */
    public record StatDef(String key, int weight, double min, double max, boolean percent) {
    }

    /** レア度1件。{@code multiplier} は主ステの値だけに掛かる。 */
    public record Rarity(String id, int weight, double multiplier, String label, NamedTextColor color) {
    }

    private final JavaPlugin plugin;
    private boolean enabled;
    private final List<Rarity> rarities = new ArrayList<>();
    private final List<StatDef> mainStats = new ArrayList<>();
    private final List<StatDef> subStats = new ArrayList<>();
    /** サブ本数 -> weight。 */
    private final Map<Integer, Integer> subCount = new LinkedHashMap<>();
    /** 率として表示するキー（main/sub のどちらかで percent: true なら入る）。 */
    private final Set<String> percentKeys = new HashSet<>();

    public ThreadRollConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        enabled = false;
        rarities.clear();
        mainStats.clear();
        subStats.clear();
        subCount.clear();
        percentKeys.clear();
        load();
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("enabled", true);

        ConfigurationSection rarSection = config.getConfigurationSection("rarities");
        if (rarSection != null) {
            for (String id : rarSection.getKeys(false)) {
                ConfigurationSection entry = rarSection.getConfigurationSection(id);
                if (entry == null) {
                    continue;
                }
                int weight = entry.getInt("weight", 0);
                if (weight <= 0) {
                    continue;
                }
                double multiplier = entry.getDouble("multiplier", 1.0);
                if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
                    multiplier = 1.0;
                }
                rarities.add(new Rarity(id, weight, multiplier,
                        entry.getString("label", id), parseColor(entry.getString("color"))));
            }
        }

        readStats(config.getConfigurationSection("main-stats"), mainStats, "main-stats");
        readStats(config.getConfigurationSection("sub-stats"), subStats, "sub-stats");

        ConfigurationSection countSection = config.getConfigurationSection("sub-count");
        if (countSection != null) {
            for (String key : countSection.getKeys(false)) {
                int count;
                try {
                    count = Integer.parseInt(key.trim());
                } catch (NumberFormatException notInt) {
                    plugin.getLogger().warning("[" + FILE_NAME + "] sub-count: '" + key
                            + "' が整数でないためスキップ");
                    continue;
                }
                int weight = countSection.getInt(key, 0);
                if (count >= 0 && weight > 0) {
                    subCount.put(count, weight);
                }
            }
        }

        if (enabled && (rarities.isEmpty() || mainStats.isEmpty())) {
            plugin.getLogger().warning("[" + FILE_NAME + "] rarities か main-stats が空なので厳選を無効化します"
                    + "（スレッドは従来どおり個体差なしで作られます）");
            enabled = false;
        }
    }

    private void readStats(ConfigurationSection section, List<StatDef> out, String label) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            int weight = entry.getInt("weight", 0);
            double min = entry.getDouble("min", 0.0);
            double max = entry.getDouble("max", 0.0);
            if (weight <= 0 || !Double.isFinite(min) || !Double.isFinite(max)) {
                continue;
            }
            if (max < min) {
                plugin.getLogger().warning("[" + FILE_NAME + "] " + label + "." + key
                        + ": max < min なので入れ替えて扱います");
                double swap = min;
                min = max;
                max = swap;
            }
            if (min == 0.0 && max == 0.0) {
                continue;
            }
            boolean percent = entry.getBoolean("percent", false);
            out.add(new StatDef(key, weight, min, max, percent));
            if (percent) {
                percentKeys.add(key);
            }
        }
    }

    private static NamedTextColor parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return NamedTextColor.GRAY;
        }
        NamedTextColor color = NamedTextColor.NAMES.value(raw.trim().toLowerCase(Locale.ROOT));
        return color == null ? NamedTextColor.GRAY : color;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 率として表示するキー集合（lore 整形用）。 */
    public Set<String> percentKeys() {
        return Set.copyOf(percentKeys);
    }

    public Optional<Rarity> rarity(String id) {
        return rarities.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** スレッド1個ぶんの厳選。無効化されていれば空。 */
    public Optional<ThreadRoll> roll(Random random) {
        if (!enabled) {
            return Optional.empty();
        }
        Rarity rarity = pick(rarities, Rarity::weight, random);
        StatDef main = pick(mainStats, StatDef::weight, random);
        if (rarity == null || main == null) {
            return Optional.empty();
        }
        Map<String, Double> mainStat = new LinkedHashMap<>();
        mainStat.put(main.key(), round(value(main, random) * rarity.multiplier()));

        Map<String, Double> subs = new LinkedHashMap<>();
        int wanted = pickSubCount(random);
        List<StatDef> pool = new ArrayList<>(subStats);
        pool.removeIf(def -> def.key().equals(main.key()));
        for (int i = 0; i < wanted && !pool.isEmpty(); i++) {
            StatDef picked = pick(pool, StatDef::weight, random);
            if (picked == null) {
                break;
            }
            pool.remove(picked);
            subs.put(picked.key(), round(value(picked, random)));
        }
        return Optional.of(new ThreadRoll(rarity.id(), mainStat, subs));
    }

    private int pickSubCount(Random random) {
        if (subCount.isEmpty()) {
            return 0;
        }
        int total = subCount.values().stream().mapToInt(Integer::intValue).sum();
        int cursor = random.nextInt(total);
        for (Map.Entry<Integer, Integer> entry : subCount.entrySet()) {
            cursor -= entry.getValue();
            if (cursor < 0) {
                return entry.getKey();
            }
        }
        return 0;
    }

    private static <T> T pick(List<T> pool, java.util.function.ToIntFunction<T> weight, Random random) {
        int total = 0;
        for (T candidate : pool) {
            total += Math.max(0, weight.applyAsInt(candidate));
        }
        if (total <= 0) {
            return null;
        }
        int cursor = random.nextInt(total);
        for (T candidate : pool) {
            cursor -= Math.max(0, weight.applyAsInt(candidate));
            if (cursor < 0) {
                return candidate;
            }
        }
        return null;
    }

    private static double value(StatDef def, Random random) {
        if (def.max() == def.min()) {
            return def.min();
        }
        return def.min() + random.nextDouble() * (def.max() - def.min());
    }

    /**
     * 有効数字を落として保存する。率は小数4桁、実数値は小数2桁 ──
     * PDC 文字列が {@code 0.023456789012} のような長さになると、スロット数ぶん配列で持つ
     * 防具側の PDC が無駄に膨らむ。
     */
    private static double round(double raw) {
        double scale = Math.abs(raw) < 1.0 ? 10_000.0 : 100.0;
        return Math.round(raw * scale) / scale;
    }
}
