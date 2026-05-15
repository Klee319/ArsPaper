package com.arspaper.api.event;

import com.arspaper.api.modifier.ModifierType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Modifierが追加・削除・変更された後に発火する post-event。 */
public class ArsManaModifierChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final NamespacedKey key;
    private final ModifierType type;
    private final Double oldValue;
    private final Double newValue;

    public ArsManaModifierChangeEvent(Player p, NamespacedKey key, ModifierType type,
                                       @Nullable Double oldValue, @Nullable Double newValue) {
        super(p);
        this.key = key;
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public NamespacedKey getKey() { return key; }
    public ModifierType getType() { return type; }
    public @Nullable Double getOldValue() { return oldValue; }
    public @Nullable Double getNewValue() { return newValue; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
