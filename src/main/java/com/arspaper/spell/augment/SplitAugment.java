package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Projectileで複数弾を発射。
 * Ars Nouveau: numSplits = 1 + buffCount(Split)
 * 1スタック: 2発、2スタック: 3発、...
 */
public class SplitAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SplitAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "split");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        int bonus = (int) config.getParam("split", "per-stack", 1.0);
        context.setSplitCount(context.getSplitCount() + bonus);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "分裂"; }

    @Override
    public String getDescription() { return "飛び道具を複数に分裂させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("split"); }

    @Override
    public int getTier() { return config.getTier("split"); }
}
