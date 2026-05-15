package com.arspaper.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * カスタムレシピのクラフト後に発火する post-event。
 * setResultStack で個数倍化・品質付与などの特殊加工が可能。
 */
public class ArsItemCraftedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String recipeId;
    private ItemStack resultStack;
    private final int rollSeed;

    public ArsItemCraftedEvent(Player p, String recipeId, ItemStack resultStack, int rollSeed) {
        super(p);
        this.recipeId = recipeId;
        this.resultStack = resultStack;
        this.rollSeed = rollSeed;
    }

    public String getRecipeId() { return recipeId; }
    public ItemStack getResultStack() { return resultStack; }
    public void setResultStack(ItemStack stack) { this.resultStack = stack; }
    public int getRollSeed() { return rollSeed; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
