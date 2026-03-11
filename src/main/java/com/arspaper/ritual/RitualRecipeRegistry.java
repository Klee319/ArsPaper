package com.arspaper.ritual;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * 儀式レシピの登録・検索を管理。
 * rituals.ymlから設定を読み込む。
 */
public class RitualRecipeRegistry {

    private final JavaPlugin plugin;
    private final Map<String, RitualRecipe> recipes = new LinkedHashMap<>();

    public RitualRecipeRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadRecipes() {
        recipes.clear();

        File file = new File(plugin.getDataFolder(), "rituals.yml");
        if (!file.exists()) {
            plugin.saveResource("rituals.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ritualsSection = config.getConfigurationSection("rituals");
        if (ritualsSection == null) {
            plugin.getLogger().info("No rituals found in rituals.yml");
            return;
        }

        for (String key : ritualsSection.getKeys(false)) {
            ConfigurationSection section = ritualsSection.getConfigurationSection(key);
            if (section == null) continue;

            try {
                String name = section.getString("name", key);
                int sourceRequired = section.getInt("source", 0);

                List<Material> pedestalItems = new ArrayList<>();
                for (String matName : section.getStringList("pedestal-items")) {
                    Material mat = Material.matchMaterial(matName);
                    if (mat != null) {
                        pedestalItems.add(mat);
                    } else {
                        plugin.getLogger().warning("Unknown material: " + matName + " in ritual " + key);
                    }
                }

                String resultStr = section.getString("result", "");
                String resultId = null;
                Material resultMaterial = null;

                if (resultStr.startsWith("custom:")) {
                    resultId = resultStr.substring("custom:".length());
                } else {
                    resultMaterial = Material.matchMaterial(resultStr);
                    if (resultMaterial == null) {
                        plugin.getLogger().warning("Unknown result material: " + resultStr + " in ritual " + key);
                        continue;
                    }
                }

                RitualRecipe recipe = new RitualRecipe(key, name, pedestalItems, sourceRequired, resultId, resultMaterial);
                recipes.put(key, recipe);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load ritual: " + key, e);
            }
        }

        plugin.getLogger().info("Loaded " + recipes.size() + " ritual recipes from rituals.yml");
    }

    public Optional<RitualRecipe> findMatch(List<Material> pedestalMaterials) {
        return recipes.values().stream()
            .filter(r -> r.matches(pedestalMaterials))
            .findFirst();
    }

    public Optional<RitualRecipe> get(String id) {
        return Optional.ofNullable(recipes.get(id));
    }

    public Collection<RitualRecipe> getAll() {
        return recipes.values();
    }
}
