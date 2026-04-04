package com.arspaper.source.sourcelink;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * sourcelinks.yml から各ソースリンクのマテリアル値マップを読み込む。
 * 設定ファイルが存在しない/セクションが空の場合はハードコードのデフォルト値を使用。
 */
public class SourcelinkConfig {

    private final JavaPlugin plugin;
    private Map<Material, Integer> volcanicMaterials;
    private Map<Material, Integer> mycelialMaterials;
    private Map<Material, Integer> alchemicalMaterials;

    public SourcelinkConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * 設定ファイルを読み込み（または再読み込み）する。
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "sourcelinks.yml");
        if (!file.exists()) {
            plugin.getLogger().info("sourcelinks.yml not found, using default values");
            volcanicMaterials = VolcanicSourcelink.getDefaultFuelValues();
            mycelialMaterials = MycelialSourcelink.getDefaultFoodValues();
            alchemicalMaterials = AlchemicalSourcelink.getDefaultAlchemyValues();
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Logger logger = plugin.getLogger();

        volcanicMaterials = loadSection(config, "volcanic.materials",
                VolcanicSourcelink.getDefaultFuelValues(), logger);
        mycelialMaterials = loadSection(config, "mycelial.materials",
                MycelialSourcelink.getDefaultFoodValues(), logger);
        alchemicalMaterials = loadSection(config, "alchemical.materials",
                AlchemicalSourcelink.getDefaultAlchemyValues(), logger);

        logger.info("Sourcelink config loaded: volcanic=" + volcanicMaterials.size()
                + ", mycelial=" + mycelialMaterials.size()
                + ", alchemical=" + alchemicalMaterials.size());
    }

    private Map<Material, Integer> loadSection(YamlConfiguration config, String path,
                                                Map<Material, Integer> defaults, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return defaults;
        }

        Map<Material, Integer> result = new EnumMap<>(Material.class);
        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                int value = section.getInt(key);
                if (value > 0) {
                    result.put(mat, value);
                }
            } catch (IllegalArgumentException e) {
                logger.warning("sourcelinks.yml: Unknown material '" + key + "' in " + path);
            }
        }

        return result.isEmpty() ? defaults : Collections.unmodifiableMap(result);
    }

    public Map<Material, Integer> getVolcanicMaterials() {
        return volcanicMaterials;
    }

    public Map<Material, Integer> getMycelialMaterials() {
        return mycelialMaterials;
    }

    public Map<Material, Integer> getAlchemicalMaterials() {
        return alchemicalMaterials;
    }
}
