package com.arspaper.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 儀式実行前に発火。素材を setRequiredMaterials で削減/置換可能。 */
public class ArsRitualPreEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String ritualId;
    private final Location location;
    private List<ItemStack> requiredMaterials;
    private boolean cancelled = false;

    public ArsRitualPreEvent(Player p, String ritualId, Location location, List<ItemStack> materials) {
        super(p);
        this.ritualId = ritualId;
        this.location = location;
        this.requiredMaterials = new ArrayList<>(materials);
    }

    public String getRitualId() { return ritualId; }
    public Location getLocation() { return location; }
    public List<ItemStack> getRequiredMaterials() { return new ArrayList<>(requiredMaterials); }
    public void setRequiredMaterials(List<ItemStack> materials) {
        this.requiredMaterials = new ArrayList<>(materials);
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean b) { this.cancelled = b; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
