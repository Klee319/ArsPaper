package com.arspaper.recipe;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 鍛冶台（Smithing Table）経由クラフトの perk 解放ゲート。
 *
 * <p>{@link RecipeUnlockGate} は {@link org.bukkit.event.inventory.PrepareItemCraftEvent} を監視するが、
 * ネザライト強化は鍛冶台の {@link PrepareSmithingEvent} を通り作業台イベントを経由しないため拾えない。
 * このリスナーがその経路を埋め、結果アイテムの型を recipe-perks（{@link UnlockGate}）と照合する。
 *
 * <p><b>トランスフォーム限定</b>: ネザライト強化のように「素材型が変化する」結果だけをゲート対象とする。
 * 装甲トリム（結果の素材型が土台と同じ）は対象外にして、装飾行為を巻き込まない。
 * ゲート対象（recipe-perks に定義あり）で perk 未所持、またはプレイヤーを特定できない場合は
 * fail-closed で結果を消す（{@link RecipeUnlockGate} の Crafter 経路と同じ安全側）。
 */
public final class SmithingTableUnlockGate implements Listener {

    private final UnlockGate gate;

    public SmithingTableUnlockGate(UnlockGate gate) {
        this.gate = gate;
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        // トリム（土台と結果の素材型が同一）は対象外。トランスフォーム（型が変わる=ネザライト化等）のみゲート。
        ItemStack base = event.getInventory().getItem(1);
        if (base != null && base.getType() == result.getType()) {
            return;
        }
        String recipeId = result.getType().getKey().getKey();
        if (!gate.isRecipeGated(recipeId)) {
            return;
        }
        HumanEntity viewer = event.getView().getPlayer();
        if (viewer instanceof Player player && gate.hasRecipePermission(player, recipeId)) {
            return;
        }
        event.setResult(null);
    }
}
