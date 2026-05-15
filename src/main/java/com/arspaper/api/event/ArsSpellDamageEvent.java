package com.arspaper.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * スペル由来のダメージEffect (HarmEffect等) が LivingEntity.damage() を呼ぶ直前に発火。
 * EliteMobs連携で magic resistance を適用するためのフック。
 */
public class ArsSpellDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity target;
    private final Player caster;
    private final List<String> activeGlyphs;
    private final String effectId;
    private double damage;
    private boolean cancelled = false;

    public ArsSpellDamageEvent(LivingEntity target, @Nullable Player caster,
                               List<String> activeGlyphs, String effectId, double damage) {
        this.target = target;
        this.caster = caster;
        this.activeGlyphs = List.copyOf(activeGlyphs);
        this.effectId = effectId;
        this.damage = damage;
    }

    public LivingEntity getTarget() { return target; }
    public @Nullable Player getCaster() { return caster; }
    public List<String> getActiveGlyphs() { return Collections.unmodifiableList(activeGlyphs); }
    public String getEffectId() { return effectId; }

    public double getDamage() { return damage; }
    public void setDamage(double damage) { this.damage = Math.max(0.0, damage); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean b) { this.cancelled = b; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
