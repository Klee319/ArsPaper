package com.arspaper.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 筆記台でグリフを解放しようとしたときに発火する。
 * cancellable: ArsPaper内部で external-unlock-only:true のグリフは必ずcancel。
 * setMaterialsToConsume / setLevelCost で外部から削減可能。
 */
public class ArsGlyphUnlockRequestEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String glyphId;
    private List<ItemStack> materialsToConsume;
    private int levelCost;
    private boolean cancelled = false;

    public ArsGlyphUnlockRequestEvent(Player p, String glyphId,
                                       List<ItemStack> materials, int levelCost) {
        super(p);
        this.glyphId = glyphId;
        this.materialsToConsume = new ArrayList<>(materials);
        this.levelCost = levelCost;
    }

    public String getGlyphId() { return glyphId; }

    public List<ItemStack> getMaterialsToConsume() { return new ArrayList<>(materialsToConsume); }
    public void setMaterialsToConsume(List<ItemStack> materials) {
        this.materialsToConsume = new ArrayList<>(materials);
    }

    public int getLevelCost() { return levelCost; }
    public void setLevelCost(int cost) { this.levelCost = Math.max(0, cost); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean b) { this.cancelled = b; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
