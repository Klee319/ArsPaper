package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * パワー-1.0。Amplifyの逆。特定エフェクトで特殊動作あり。
 * Ars Nouveau: amplification -= 1.0
 */
public class DampenAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public DampenAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "dampen");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.applyDampen();
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "減衰"; }

    @Override
    public String getDescription() { return "効果の威力を抑える"; }

    @Override
    public int getManaCost() { return config.getManaCost("dampen"); }

    @Override
    public int getTier() { return config.getTier("dampen"); }
}
