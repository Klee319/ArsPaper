package com.arspaper.recipe;

import org.bukkit.Keyed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;

/**
 * クラフトレシピの perk 解放ゲート。
 *
 * <p>{@link PrepareItemCraftEvent}（プレイヤーのクラフト台/インベントリ経由）と
 * {@link CrafterCraftEvent}（Crafterブロックによる自動クラフト）の両方を監視し、
 * クラフト中レシピの ID を unlock-gate.yml の recipe-perks と照合する。
 *
 * <p>Crafterブロックはプレイヤー主体ではないため所持perkを確認できない。
 * ゲート対象レシピ（recipe-perksに定義あり）であれば、プレイヤーを特定できない
 * 以上は安全側に倒し fail-closed でクラフトをキャンセルする。
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
        String recipeId = resolveRecipeId(event.getRecipe());
        if (recipeId == null) return;

        HumanEntity viewer = event.getView().getPlayer();
        if (!(viewer instanceof Player player)) {
            return;
        }

        if (!gate.hasRecipePermission(player, recipeId)) {
            // perk 未所持 → クラフト結果を得られないようにする
            event.getInventory().setResult(null);
        }
    }

    /**
     * Crafterブロックからのクラフトを監視する。
     *
     * <p>Crafterは {@link PrepareItemCraftEvent} を経由しないため、同じ perk ゲートを
     * 適用するために専用ハンドラを用意する。プレイヤーを特定できる手段が無いため、
     * ゲート対象レシピであれば fail-closed でキャンセルする。
     */
    @EventHandler
    public void onCrafterCraft(CrafterCraftEvent event) {
        String recipeId = resolveRecipeId(event.getRecipe());
        if (recipeId == null) return;

        if (gate.isRecipeGated(recipeId)) {
            event.setCancelled(true);
        }
    }

    /** レシピの NamespacedKey からレシピIDを取り出す。キー無しレシピは対象外。 */
    private String resolveRecipeId(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null; // レシピ未確定 or キー無しはゲート対象外
        }
        return keyed.getKey().getKey();
    }
}
