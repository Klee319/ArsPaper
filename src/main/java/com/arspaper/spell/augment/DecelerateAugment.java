package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 加速-0.5。Accelerateの逆。弾速/発動頻度DOWN。
 * Ars Nouveau: acceleration -= 0.5
 */
public class DecelerateAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public DecelerateAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "decelerate");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setAcceleration(context.getAcceleration() - 0.5);
        context.setProjectileSpeedMultiplier(Math.max(0.1, context.getProjectileSpeedMultiplier() - 0.5));
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "減速"; }

    @Override
    public String getDescription() { return "飛び道具の速度を低下させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("decelerate"); }

    @Override
    public int getTier() { return config.getTier("decelerate"); }
}
