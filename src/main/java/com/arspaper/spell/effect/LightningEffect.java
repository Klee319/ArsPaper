package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象に雷を落とすEffect。Ars NouveauのEffectLightningに準拠。
 * バニラの雷Entityを使用するため、パーティクル/サウンドは自動。
 * Geyser完全互換（ネイティブEntity）。
 */
public class LightningEffect implements SpellEffect {

    private final NamespacedKey id;

    public LightningEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "lightning");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        target.getWorld().strikeLightning(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        blockLocation.getWorld().strikeLightning(blockLocation);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Lightning"; }

    @Override
    public int getManaCost() { return 30; }

    @Override
    public int getTier() { return 3; }
}
