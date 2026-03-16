package com.arspaper.spell.augment;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 視線に垂直な面に効果を投影する増強。
 * 壁・床・橋など、視線方向に対して垂直な面を生成する。
 */
public class WallAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public WallAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "wall");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setWallPattern(true);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "投影"; }

    @Override
    public String getDescription() { return "視線に垂直な面に効果を投影する"; }

    @Override
    public int getManaCost() { return config.getManaCost("wall"); }

    @Override
    public int getTier() { return config.getTier("wall"); }
}
