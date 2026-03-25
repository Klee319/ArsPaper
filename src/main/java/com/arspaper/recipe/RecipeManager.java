package com.arspaper.recipe;

import com.arspaper.ArsPaper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 作業台レシピをサーバーに登録する。
 * UnifiedRecipeLoaderから受け取ったレシピデータと、
 * ArmorConfigManagerの防具レシピを登録する。
 */
public class RecipeManager {

    private final JavaPlugin plugin;
    private final Map<NamespacedKey, Object> registeredRecipes = new HashMap<>();

    public RecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * UnifiedRecipeLoaderから作業台レシピを登録する。
     */
    public void registerWorkbenchRecipes(List<UnifiedRecipeLoader.WorkbenchRecipeData> recipes) {
        int count = 0;
        for (UnifiedRecipeLoader.WorkbenchRecipeData data : recipes) {
            try {
                switch (data.type().toLowerCase()) {
                    case "shaped" -> registerShaped(data);
                    case "shapeless" -> registerShapeless(data);
                    default -> plugin.getLogger().warning("Unknown recipe type: " + data.type() + " for " + data.id());
                }
                count++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load recipe: " + data.id(), e);
            }
        }
        plugin.getLogger().info("Loaded " + count + " workbench recipes");
    }

    private void registerShaped(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        ItemStack result = resolveResult(data.result());
        if (result == null) {
            plugin.getLogger().warning("Skipping shaped recipe " + data.id() + ": result not found (" + data.result() + ")");
            return;
        }
        result.setAmount(data.amount());

        NamespacedKey nsKey = new NamespacedKey(plugin, data.id());

        // 全素材を事前解決（1つでも失敗したらレシピ登録をスキップ）
        Map<Character, RecipeChoice> resolvedIngredients = new HashMap<>();
        boolean allResolved = true;
        for (Map.Entry<String, String> entry : data.ingredients().entrySet()) {
            RecipeChoice choice = resolveIngredient(entry.getValue(), data.id());
            if (choice == null) {
                plugin.getLogger().warning("Skipping shaped recipe " + data.id()
                    + ": ingredient '" + entry.getKey() + "'=" + entry.getValue() + " not found");
                allResolved = false;
            } else {
                resolvedIngredients.put(entry.getKey().charAt(0), choice);
            }
        }
        if (!allResolved) return;

        // reload時の重複防止
        if (registeredRecipes.containsKey(nsKey)) {
            Bukkit.removeRecipe(nsKey);
        }

        ShapedRecipe recipe = new ShapedRecipe(nsKey, result);
        recipe.shape(data.shape().toArray(new String[0]));
        for (Map.Entry<Character, RecipeChoice> entry : resolvedIngredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }

        Bukkit.addRecipe(recipe);
        registeredRecipes.put(nsKey, recipe);
    }

    private void registerShapeless(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        ItemStack result = resolveResult(data.result());
        if (result == null) {
            plugin.getLogger().warning("Skipping shapeless recipe " + data.id() + ": result not found (" + data.result() + ")");
            return;
        }
        result.setAmount(data.amount());

        NamespacedKey nsKey = new NamespacedKey(plugin, data.id());

        // reload時の重複防止
        if (registeredRecipes.containsKey(nsKey)) {
            Bukkit.removeRecipe(nsKey);
        }

        ShapelessRecipe recipe = new ShapelessRecipe(nsKey, result);

        for (String ing : data.ingredients().values()) {
            RecipeChoice choice = resolveIngredient(ing, data.id());
            if (choice != null) recipe.addIngredient(choice);
        }

        Bukkit.addRecipe(recipe);
        registeredRecipes.put(nsKey, recipe);
    }

    /**
     * armors.ymlで定義された防具レシピを登録する。
     */
    public void registerArmorRecipes(com.arspaper.item.ArmorConfigManager armorConfig) {
        int count = 0;
        for (com.arspaper.item.ArmorSetConfig set : armorConfig.getAll()) {
            for (Map.Entry<String, com.arspaper.item.RecipeDefinition> entry : set.getRecipes().entrySet()) {
                String slot = entry.getKey();
                com.arspaper.item.RecipeDefinition def = entry.getValue();
                String recipeKey = "armor_" + set.getItemId(slot);

                try {
                    ItemStack result = ArsPaper.getInstance().getItemRegistry()
                        .get(set.getItemId(slot))
                        .map(item -> item.createItemStack())
                        .orElse(null);
                    if (result == null) {
                        plugin.getLogger().warning("Armor item not found: " + set.getItemId(slot));
                        continue;
                    }

                    NamespacedKey nsKey = new NamespacedKey(plugin, recipeKey);
                    if (registeredRecipes.containsKey(nsKey)) {
                        Bukkit.removeRecipe(nsKey);
                    }
                    if ("shaped".equalsIgnoreCase(def.type())) {
                        ShapedRecipe recipe = new ShapedRecipe(nsKey, result);
                        recipe.shape(def.shape().toArray(new String[0]));
                        for (Map.Entry<String, String> ing : def.ingredients().entrySet()) {
                            RecipeChoice choice = resolveIngredient(ing.getValue(), recipeKey);
                            if (choice != null) {
                                recipe.setIngredient(ing.getKey().charAt(0), choice);
                            }
                        }
                        Bukkit.addRecipe(recipe);
                        registeredRecipes.put(nsKey, recipe);
                    } else if ("shapeless".equalsIgnoreCase(def.type())) {
                        ShapelessRecipe recipe = new ShapelessRecipe(nsKey, result);
                        for (String ing : def.ingredients().values()) {
                            RecipeChoice choice = resolveIngredient(ing, recipeKey);
                            if (choice != null) recipe.addIngredient(choice);
                        }
                        Bukkit.addRecipe(recipe);
                        registeredRecipes.put(nsKey, recipe);
                    }
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to register armor recipe: " + recipeKey + " - " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + count + " armor recipes from armors.yml");
    }

    /**
     * 素材文字列をRecipeChoiceに変換。"custom:item_id"形式ならExactChoice。
     */
    RecipeChoice resolveIngredient(String ingredientName, String recipeKey) {
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

    private ItemStack resolveResult(String resultStr) {
        if (resultStr == null) return null;

        if (resultStr.startsWith("custom:")) {
            String customId = resultStr.substring("custom:".length());
            var opt = ArsPaper.getInstance().getItemRegistry().get(customId);
            if (opt.isPresent()) {
                return opt.get().createItemStack();
            }
            // カスタムアイテムが見つからない場合、バニラMaterialとしてフォールバック
            Material fallback = Material.matchMaterial(customId);
            if (fallback != null) {
                return new ItemStack(fallback);
            }
            plugin.getLogger().warning("Unknown custom item or material: " + customId);
            return null;
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

    /**
     * 登録済みレシピのマップを返す。
     */
    public Map<NamespacedKey, Object> getRegisteredRecipes() {
        return registeredRecipes;
    }
}
