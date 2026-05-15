package com.arspaper.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** 儀式完了後に発火する post-event。 */
public class ArsRitualPostEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String ritualId;
    private final Location location;
    private final boolean successful;

    public ArsRitualPostEvent(Player p, String ritualId, Location location, boolean successful) {
        super(p);
        this.ritualId = ritualId;
        this.location = location;
        this.successful = successful;
    }

    public String getRitualId() { return ritualId; }
    public Location getLocation() { return location; }
    public boolean isSuccessful() { return successful; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
