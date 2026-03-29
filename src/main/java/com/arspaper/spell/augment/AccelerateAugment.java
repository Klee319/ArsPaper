package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 加速+1.0。弾速UP、Linger/Wall等の発動頻度UP。
 * Ars Nouveau: acceleration += 1.0
 */
public class AccelerateAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public AccelerateAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "accelerate");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        double bonus = config.getParam("accelerate", "per-stack", 1.0);
        context.setAcceleration(context.getAcceleration() + bonus);
        // Form-augmentとしても使用: 飛び道具速度倍率
        context.setProjectileSpeedMultiplier(context.getProjectileSpeedMultiplier() + bonus);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "加速"; }

    @Override
    public String getDescription() { return "飛び道具の速度を上昇させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("accelerate"); }

    @Override
    public int getTier() { return config.getTier("accelerate"); }
}
