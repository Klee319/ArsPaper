package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 延伸増強。射程・距離・範囲を延長する。
 * テレポートの距離延長、投射の射程延長等に使用。
 */
public class ExtendReachAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public ExtendReachAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "extend_reach");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        int bonus = (int) config.getParam("extend_reach", "per-stack", 1.0);
        context.setReachLevel(context.getReachLevel() + bonus);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "延伸"; }

    @Override
    public String getDescription() { return "射程・距離を延長する"; }

    @Override
    public int getManaCost() { return config.getManaCost("extend_reach"); }

    @Override
    public int getTier() { return config.getTier("extend_reach"); }
}
