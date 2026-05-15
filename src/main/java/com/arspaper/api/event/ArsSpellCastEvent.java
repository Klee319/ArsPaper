package com.arspaper.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * スペル詠唱の判定処理の冒頭（マナ消費前）に発火する。
 * cancellable で詠唱中止可能。setManaCost(0) で無料化可能。
 */
public class ArsSpellCastEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final List<String> activeGlyphs;
    private final String primaryEffectId;
    private double manaCost;
    private double damage;
    private boolean cancelled = false;

    public ArsSpellCastEvent(Player caster, List<String> activeGlyphs, String primaryEffectId,
                             double initialManaCost, double initialDamage) {
        super(caster);
        this.activeGlyphs = List.copyOf(activeGlyphs);
        this.primaryEffectId = primaryEffectId;
        this.manaCost = initialManaCost;
        this.damage = initialDamage;
    }

    public List<String> getActiveGlyphs() { return Collections.unmodifiableList(activeGlyphs); }

    public double getManaCost() { return manaCost; }
    public void setManaCost(double cost) { this.manaCost = Math.max(0.0, cost); }

    public double getDamage() { return damage; }
    public void setDamage(double damage) { this.damage = damage; }

    public String getPrimaryEffectId() { return primaryEffectId; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean b) { this.cancelled = b; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
