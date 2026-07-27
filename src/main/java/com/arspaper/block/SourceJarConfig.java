package com.arspaper.block;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * sourcejars.yml からソースジャーの容量・見た目定義を読む。
 */
public final class SourceJarConfig {

    public static final int FALLBACK_CAPACITY = 10000;

    public record JarDef(
            String id,
            Material material,
            String displayName,
            int customModelData,
            int capacity,
            List<String> lore) {

        /** capacity &lt; 0 → 無限。 */
        public boolean infinite() {
            return capacity < 0;
        }

        public int effectiveCapacity() {
            return infinite() ? Integer.MAX_VALUE : Math.max(0, capacity);
        }
    }

    private final JavaPlugin plugin;
    private Map<String, JarDef> jars = Map.of();

    public SourceJarConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "sourcejars.yml");
        if (!file.exists()) {
            plugin.saveResource("sourcejars.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Logger log = plugin.getLogger();
        ConfigurationSection root = yaml.getConfigurationSection("jars");
        Map<String, JarDef> parsed = new LinkedHashMap<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) {
                    continue;
                }
                try {
                    Material mat = Material.valueOf(sec.getString("material", "DECORATED_POT")
                            .trim().toUpperCase(Locale.ROOT));
                    int capacity = sec.getInt("capacity", FALLBACK_CAPACITY);
                    parsed.put(id, new JarDef(
                            id,
                            mat,
                            sec.getString("display-name", id),
                            sec.getInt("custom-model-data", 0),
                            capacity,
                            List.copyOf(sec.getStringList("lore"))));
                } catch (IllegalArgumentException ex) {
                    log.warning("[sourcejars.yml] jar '" + id + "' invalid: " + ex.getMessage());
                }
            }
        }
        if (parsed.isEmpty()) {
            parsed.put("source_jar", new JarDef("source_jar", Material.DECORATED_POT,
                    "ソースジャー", 200002, FALLBACK_CAPACITY, List.of("魔法のソースエネルギーを貯蔵")));
            parsed.put("creative_source_jar", new JarDef("creative_source_jar", Material.DECORATED_POT,
                    "クリエイティブソースジャー", 200003, -1, List.of("無限のソースエネルギーを供給")));
        }
        this.jars = Collections.unmodifiableMap(parsed);
        log.info("[sourcejars.yml] loaded " + jars.size() + " jar definition(s)");
    }

    public Optional<JarDef> get(String id) {
        return Optional.ofNullable(jars.get(id));
    }

    public int capacityOf(String id) {
        return get(id).map(JarDef::effectiveCapacity).orElse(FALLBACK_CAPACITY);
    }

    public Map<String, JarDef> all() {
        return jars;
    }
}
