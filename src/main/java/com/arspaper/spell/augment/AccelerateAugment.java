package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 飛び道具の速度を上昇させるAugment。
 * Ars NouveauのGlyphAccelerateに準拠。
 * Form直後に配置するとProjectileFormで速度倍率として使用される。
 */
public class AccelerateAugment implements SpellAugment {

    private final NamespacedKey id;

    public AccelerateAugment(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "accelerate");
    }

    @Override
    public void modify(SpellContext context) {
        context.setProjectileSpeedMultiplier(context.getProjectileSpeedMultiplier() + 1.0);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Accelerate"; }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 2; }
}
