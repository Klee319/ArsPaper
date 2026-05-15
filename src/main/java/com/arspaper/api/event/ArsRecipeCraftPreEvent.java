package com.arspaper.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** カスタムレシピのクラフト前に発火。素材削減可能。 */
public class ArsRecipeCraftPreEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String recipeId;
    private final ItemStack resultPreview;
    private List<ItemStack> ingredients;
    private boolean cancelled = false;

    public ArsRecipeCraftPreEvent(Player p, String recipeId, ItemStack resultPreview,
                                   List<ItemStack> ingredients) {
        super(p);
        this.recipeId = recipeId;
        this.resultPreview = resultPreview == null ? null : resultPreview.clone();
        this.ingredients = new ArrayList<>(ingredients);
    }

    public String getRecipeId() { return recipeId; }
    public ItemStack getResultPreview() { return resultPreview == null ? null : resultPreview.clone(); }

    public List<ItemStack> getIngredients() { return new ArrayList<>(ingredients); }
    public void setIngredients(List<ItemStack> ingredients) {
        this.ingredients = new ArrayList<>(ingredients);
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean b) { this.cancelled = b; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
