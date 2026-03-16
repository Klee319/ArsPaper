package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 直後のEffectの貫通回数を増加させるAugment。
 * Projectile Formと連携し、ヒット後もエンティティを貫通して次の対象にも効果を適用。
 * 1スタックにつき+1貫通。
 */
public class PierceAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public PierceAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "pierce");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setPierceCount(context.getPierceCount() + 1);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "貫通"; }

    @Override
    public String getDescription() { return "飛び道具が対象を貫通する"; }

    @Override
    public int getManaCost() { return config.getManaCost("pierce"); }

    @Override
    public int getTier() { return config.getTier("pierce"); }
}
