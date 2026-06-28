package com.arspaper.recipe;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;

/**
 * クラフトレシピの perk 解放ゲート。
 *
 * <p>{@link PrepareItemCraftEvent} を監視し、クラフト中レシピの ID を
 * unlock-gate.yml の recipe-perks と照合する。必要 perk が設定されていて、
 * クラフトを覗いているプレイヤーがその perk を所持していなければ、
 * 結果スロットを空にしてクラフトを成立させない。
 *
 * <p>レシピID は登録時の NamespacedKey のキー部分（{@code RecipeManager} が
 * {@code new NamespacedKey(plugin, data.id())} で登録するため、キー部分=レシピID）。
 * 設定に未定義のレシピはゲート無し（従来通りクラフト可）。
 */
public final class RecipeUnlockGate implements Listener {

    private final UnlockGate gate;

    public RecipeUnlockGate(UnlockGate gate) {
        this.gate = gate;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) {
            return; // レシピ未確定 or キー無しはゲート対象外
        }
        NamespacedKey key = keyed.getKey();
        String recipeId = key.getKey();

        HumanEntity viewer = event.getView().getPlayer();
        if (!(viewer instanceof Player player)) {
            return;
        }

        if (!gate.hasRecipePermission(player, recipeId)) {
            // perk 未所持 → クラフト結果を得られないようにする
            event.getInventory().setResult(null);
        }
    }
}
