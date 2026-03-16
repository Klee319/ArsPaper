package com.arspaper.recipe;

import com.arspaper.ArsPaper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * recipes.ymlからクラフトレシピを読み込み、サーバーに登録する。
 * Shaped / Shapeless レシピをサポート。
 * 結果がカスタムアイテムの場合はCustomItemRegistry経由でItemStackを生成。
 */
public class RecipeManager {

    private final JavaPlugin plugin;
    private final Map<NamespacedKey, Object> registeredRecipes = new HashMap<>();

    public RecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadRecipes() {
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) {
            plugin.saveResource("recipes.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            plugin.getLogger().info("No recipes found in recipes.yml");
            return;
        }

        int count = 0;
        for (String key : recipesSection.getKeys(false)) {
            ConfigurationSection recipeSection = recipesSection.getConfigurationSection(key);
            if (recipeSection == null) continue;

            try {
                // craft-method: workbench, ritual, or both (default: workbench)
                String craftMethod = recipeSection.getString("craft-method", "workbench").toLowerCase();
                if ("ritual".equals(craftMethod)) {
                    // workbenchレシピとしては登録しない
                    continue;
                }

                String type = recipeSection.getString("type", "shaped");
                switch (type.toLowerCase()) {
                    case "shaped" -> registerShaped(key, recipeSection);
                    case "shapeless" -> registerShapeless(key, recipeSection);
                    default -> plugin.getLogger().warning("Unknown recipe type: " + type + " for " + key);
                }
                count++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load recipe: " + key, e);
            }
        }
        plugin.getLogger().info("Loaded " + count + " recipes from recipes.yml");
    }

    private void registerShaped(String key, ConfigurationSection section) {
        ItemStack result = resolveResult(section);
        if (result == null) return;

        int amount = section.getInt("amount", 1);
        result.setAmount(amount);

        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        ShapedRecipe recipe = new ShapedRecipe(nsKey, result);

        List<String> shape = section.getStringList("shape");
        if (shape.isEmpty()) {
            plugin.getLogger().warning("Recipe " + key + " has no shape defined");
            return;
        }
        recipe.shape(shape.toArray(new String[0]));

        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (ingredients != null) {
            for (String symbol : ingredients.getKeys(false)) {
                String materialName = ingredients.getString(symbol);
                RecipeChoice choice = resolveIngredient(materialName, key);
                if (choice != null) {
                    recipe.setIngredient(symbol.charAt(0), choice);
                }
            }
        }

        Bukkit.addRecipe(recipe);
        registeredRecipes.put(nsKey, recipe);
    }

    private void registerShapeless(String key, ConfigurationSection section) {
        ItemStack result = resolveResult(section);
        if (result == null) return;

        int amount = section.getInt("amount", 1);
        result.setAmount(amount);

        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        ShapelessRecipe recipe = new ShapelessRecipe(nsKey, result);

        List<String> ingredientNames = section.getStringList("ingredients");
        for (String ingredientName : ingredientNames) {
            RecipeChoice choice = resolveIngredient(ingredientName, key);
            if (choice != null) {
                recipe.addIngredient(choice);
            }
        }

        Bukkit.addRecipe(recipe);
        registeredRecipes.put(nsKey, recipe);
    }

    /**
     * 素材文字列をRecipeChoiceに変換。"custom:item_id"形式ならExactChoice。
     */
    private RecipeChoice resolveIngredient(String ingredientName, String recipeKey) {
        if (ingredientName == null) return null;

        if (ingredientName.startsWith("custom:")) {
            String customId = ingredientName.substring("custom:".length());
            ItemStack customItem = ArsPaper.getInstance().getItemRegistry()
                .get(customId)
                .map(item -> item.createItemStack())
                .orElse(null);
            if (customItem != null) {
                return new RecipeChoice.ExactChoice(customItem);
            }
            plugin.getLogger().warning("Unknown custom ingredient: " + customId + " in recipe " + recipeKey);
            return null;
        }

        Material mat = Material.matchMaterial(ingredientName);
        if (mat != null) {
            return new RecipeChoice.MaterialChoice(mat);
        }
        plugin.getLogger().warning("Unknown material: " + ingredientName + " in recipe " + recipeKey);
        return null;
    }

    /**
     * resultフィールドを解決。"custom:item_id"形式ならカスタムアイテム。
     */
    private ItemStack resolveResult(ConfigurationSection section) {
        String resultStr = section.getString("result");
        if (resultStr == null) {
            plugin.getLogger().warning("Recipe has no result defined");
            return null;
        }

        if (resultStr.startsWith("custom:")) {
            String customId = resultStr.substring("custom:".length());
            return ArsPaper.getInstance().getItemRegistry()
                .get(customId)
                .map(item -> item.createItemStack())
                .orElseGet(() -> {
                    plugin.getLogger().warning("Unknown custom item: " + customId);
                    return null;
                });
        }

        Material mat = Material.matchMaterial(resultStr);
        if (mat == null) {
            plugin.getLogger().warning("Unknown material for result: " + resultStr);
            return null;
        }
        return new ItemStack(mat);
    }

    public void unloadRecipes() {
        for (NamespacedKey key : registeredRecipes.keySet()) {
            Bukkit.removeRecipe(key);
        }
        registeredRecipes.clear();
    }

    public int getRecipeCount() {
        return registeredRecipes.size();
    }
}
