package com.arspaper.recipe;

import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;

/**
 * フォーク登録の {@code method: workbench}(作業台専用, 3×3)レシピが2×2インベントリグリッドで
 * クラフトできてしまうのを防ぐ。{@code method: inventory} は2×2/3×3どちらでも成立させるため対象外。
 *
 * <p>TrinityForge {@code CatalogWorkbenchListener} の
 * {@code registered.spec().isWorkbench() && matrix.length == 4} 判定パターンに準拠する。
 * インベントリの2×2クラフトグリッドは {@link org.bukkit.inventory.CraftingInventory#getMatrix()} の
 * 長さが4になる(3×3の作業台は9)。
 */
public final class WorkbenchGridGateListener implements Listener {

    private final RecipeManager recipeManager;

    public WorkbenchGridGateListener(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) {
            return;
        }
        if (event.getInventory().getMatrix().length != 4) {
            return; // 2×2インベントリグリッド以外は対象外(3×3作業台では常に成立してよい)
        }
        if (recipeManager.isWorkbenchOnly(keyed.getKey())) {
            event.getInventory().setResult(null);
        }
    }
}
