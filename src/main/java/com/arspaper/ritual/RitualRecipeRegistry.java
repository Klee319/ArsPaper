package com.arspaper.ritual;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
                // craft-method: ritual, workbench, or both (default: ritual)
                String craftMethod = section.getString("craft-method", "ritual").toLowerCase();
                if ("workbench".equals(craftMethod)) {
                    // 儀式レシピとしては登録しない
                    continue;
                }

                String name = section.getString("name", key);
                int sourceRequired = section.getInt("source", 0);

                List<RitualIngredient> pedestalItems = new ArrayList<>();
                for (String itemName : section.getStringList("pedestal-items")) {
                    if (itemName.startsWith("custom:")) {
                        String customId = itemName.substring("custom:".length());
                        pedestalItems.add(RitualIngredient.ofCustom(customId));
                    } else {
                        Material mat = Material.matchMaterial(itemName);
                        if (mat != null) {
                            pedestalItems.add(RitualIngredient.ofMaterial(mat));
                        } else {
                            plugin.getLogger().warning("Unknown material: " + itemName + " in ritual " + key);
                        }
                    }
                }

                // core-item（コアに置くアイテム、任意）
                RitualIngredient coreItem = null;
                String coreItemStr = section.getString("core-item", null);
                if (coreItemStr != null && !coreItemStr.isEmpty()) {
                    if (coreItemStr.startsWith("custom:")) {
                        coreItem = RitualIngredient.ofCustom(coreItemStr.substring("custom:".length()));
                    } else {
                        Material coreMat = Material.matchMaterial(coreItemStr);
                        if (coreMat != null) {
                            coreItem = RitualIngredient.ofMaterial(coreMat);
                        } else {
                            plugin.getLogger().warning("Unknown core-item material: " + coreItemStr + " in ritual " + key);
                        }
                    }
                }

                // effect-type / effect-params 読み込み
                String effectType = section.getString("effect-type", "craft");
                Map<String, String> effectParams = new HashMap<>();
                ConfigurationSection paramsSection = section.getConfigurationSection("effect-params");
                if (paramsSection != null) {
                    for (String paramKey : paramsSection.getKeys(false)) {
                        effectParams.put(paramKey, paramsSection.getString(paramKey, ""));
                    }
                }

                String resultStr = section.getString("result", "");
                String resultId = null;
                Material resultMaterial = null;

                if (resultStr.startsWith("custom:")) {
                    resultId = resultStr.substring("custom:".length());
                } else if (!resultStr.isEmpty()) {
                    resultMaterial = Material.matchMaterial(resultStr);
                    if (resultMaterial == null && "craft".equals(effectType)) {
                        plugin.getLogger().warning("Unknown result material: " + resultStr + " in ritual " + key);
                        continue;
                    }
                } else if ("craft".equals(effectType)) {
                    plugin.getLogger().warning("No result specified for craft ritual: " + key);
                    continue;
                }

                RitualRecipe recipe = new RitualRecipe(key, name, coreItem, pedestalItems, sourceRequired, resultId, resultMaterial, effectType, effectParams);
                recipes.put(key, recipe);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load ritual: " + key, e);
            }
        }

        plugin.getLogger().info("Loaded " + recipes.size() + " ritual recipes from rituals.yml");
    }

    public Optional<RitualRecipe> findMatch(RitualIngredient coreItem, List<RitualIngredient> pedestalIngredients) {
        return recipes.values().stream()
            .filter(r -> r.matches(coreItem, pedestalIngredients))
            .findFirst();
    }

    public Optional<RitualRecipe> get(String id) {
        return Optional.ofNullable(recipes.get(id));
    }

    public Collection<RitualRecipe> getAll() {
        return recipes.values();
    }
}
