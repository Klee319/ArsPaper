package com.arspaper.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * materials.ymlからカスタム中間素材定義を読み込む。
 */
public class MaterialConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, MaterialConfig> materials = new LinkedHashMap<>();

    public MaterialConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        materials.clear();

        File file = new File(plugin.getDataFolder(), "materials.yml");
        if (!file.exists()) {
            plugin.saveResource("materials.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("materials");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection matSection = section.getConfigurationSection(id);
            if (matSection == null) continue;

            try {
                String displayName = matSection.getString("display_name", id);
                String nameColor = matSection.getString("name_color", "&f");
                Material baseMaterial = Material.matchMaterial(
                    matSection.getString("base_material", "STONE"));
                if (baseMaterial == null) {
                    plugin.getLogger().warning("Unknown base_material for material: " + id);
                    continue;
                }
                int customModelData = matSection.getInt("custom_model_data", 0);
                List<String> lore = matSection.getStringList("lore");

                // レシピ
                String coreItem = null;
                List<String> pedestalItems = Collections.emptyList();
                int source = 0;

                ConfigurationSection recipeSection = matSection.getConfigurationSection("recipe");
                if (recipeSection != null) {
                    coreItem = recipeSection.getString("core-item", null);
                    pedestalItems = recipeSection.getStringList("pedestal-items");
                    source = recipeSection.getInt("source", 0);
                }

                materials.put(id, new MaterialConfig(
                    id, displayName, nameColor, baseMaterial, customModelData,
                    lore, coreItem, pedestalItems, source));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load material: " + id + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + materials.size() + " materials from materials.yml");
    }

    public void reload() {
        load();
    }

    public Optional<MaterialConfig> get(String id) {
        return Optional.ofNullable(materials.get(id));
    }

    public Collection<MaterialConfig> getAll() {
        return materials.values();
    }
}
