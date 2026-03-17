package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 範囲+1.0。ブロック/エンティティの影響範囲拡大。
 * Ars Nouveau: aoeMultiplier += 1.0
 */
public class AoeAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public AoeAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "aoe");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setAoeLevel(context.getAoeLevel() + 1);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "範囲[幅]"; }

    @Override
    public String getDescription() { return "対象の面の左右方向に範囲を広げる"; }

    @Override
    public int getManaCost() { return config.getManaCost("aoe"); }

    @Override
    public int getTier() { return config.getTier("aoe"); }
}
