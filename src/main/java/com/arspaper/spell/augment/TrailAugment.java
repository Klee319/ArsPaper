package com.arspaper.spell.augment;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 連射増強: 形態に付与するとスペルのクールダウンを短縮する。
 * 1つにつきCTを半減（最低100ms）。
 * 形態グリフと互換。
 */
public class TrailAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public TrailAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "rapid_fire");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setRapidFireLevel(context.getRapidFireLevel() + 1);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "連射"; }

    @Override
    public String getDescription() { return "スペルのCTを短縮する"; }

    @Override
    public int getManaCost() { return config.getManaCost("rapid_fire"); }

    @Override
    public int getTier() { return config.getTier("rapid_fire"); }
}
