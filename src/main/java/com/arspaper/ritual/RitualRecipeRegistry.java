package com.arspaper.ritual;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 儀式レシピの登録・検索を管理。
 * UnifiedRecipeLoaderからレシピを受け取る。
 */
public class RitualRecipeRegistry {

    private final JavaPlugin plugin;
    private final Map<String, RitualRecipe> recipes = new LinkedHashMap<>();

    public RitualRecipeRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * UnifiedRecipeLoaderから儀式レシピを一括登録する。
     */
    public void registerRecipes(List<RitualRecipe> ritualRecipes) {
        recipes.clear();
        for (RitualRecipe recipe : ritualRecipes) {
            recipes.put(recipe.id(), recipe);
        }
        plugin.getLogger().info("Loaded " + recipes.size() + " ritual recipes");
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
