package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 持続時間-1.0。ExtendTimeの逆。
 * Ars Nouveau: durationMultiplier -= 1.0
 */
public class DurationDownAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public DurationDownAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "duration_down");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.applyDurationDown();
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "短縮"; }

    @Override
    public String getDescription() { return "効果の持続時間を短縮する"; }

    @Override
    public int getManaCost() { return config.getManaCost("duration_down"); }

    @Override
    public int getTier() { return config.getTier("duration_down"); }
}
