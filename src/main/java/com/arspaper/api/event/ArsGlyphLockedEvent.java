package com.arspaper.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** グリフが取り消された後に通知される post-event。 */
public class ArsGlyphLockedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String glyphId;
    private final LockSource source;

    public ArsGlyphLockedEvent(Player p, String glyphId, LockSource source) {
        super(p);
        this.glyphId = glyphId;
        this.source = source;
    }

    public String getGlyphId() { return glyphId; }
    public LockSource getSource() { return source; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
