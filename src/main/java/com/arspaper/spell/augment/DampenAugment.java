package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Amplifyの逆。効果の威力を下げるAugment。
 * Ars NouveauのEffectDampenに準拠。
 * マナコスト0だが効果を弱めるため、自傷軽減などの用途がある。
 */
public class DampenAugment implements SpellAugment {

    private final NamespacedKey id;

    public DampenAugment(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "dampen");
    }

    @Override
    public void modify(SpellContext context) {
        context.setAmplifyLevel(Math.max(0, context.getAmplifyLevel() - 1));
        context.setDamageMultiplier(Math.max(0.25, context.getDamageMultiplier() - 0.5));
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Dampen"; }

    @Override
    public int getManaCost() { return 0; }

    @Override
    public int getTier() { return 1; }
}
