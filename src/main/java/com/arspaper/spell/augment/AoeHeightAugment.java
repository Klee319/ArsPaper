package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 視線に対して上下方向に範囲を拡大する増強。
 */
public class AoeHeightAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public AoeHeightAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "aoe_height");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        int bonus = (int) config.getParam("aoe_height", "per-stack", 1.0);
        context.setAoeHeight(context.getAoeHeight() + bonus);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "範囲[高さ]"; }

    @Override
    public String getDescription() { return "対象の面の上下方向に範囲を広げる"; }

    @Override
    public int getManaCost() { return config.getManaCost("aoe_height"); }

    @Override
    public int getTier() { return config.getTier("aoe_height"); }
}
