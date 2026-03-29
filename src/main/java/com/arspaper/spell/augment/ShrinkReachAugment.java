package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 収縮増強。射程・距離を短縮する。延伸の逆。
 */
public class ShrinkReachAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public ShrinkReachAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "shrink_reach");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setReachLevel(context.getReachLevel() - 1);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "収縮"; }

    @Override
    public String getDescription() { return "射程・距離を短縮する"; }

    @Override
    public int getManaCost() { return config.getManaCost("shrink_reach"); }

    @Override
    public int getTier() { return config.getTier("shrink_reach"); }
}
