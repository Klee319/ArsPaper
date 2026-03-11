package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 飛び道具を複数に分裂させるAugment。
 * Ars NouveauのGlyphSplitに準拠。
 * 1スタックで+2発（計3発）、2スタックで+4発（計5発）。
 */
public class SplitAugment implements SpellAugment {

    private final NamespacedKey id;

    public SplitAugment(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "split");
    }

    @Override
    public void modify(SpellContext context) {
        context.setSplitCount(context.getSplitCount() + 2);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Split"; }

    @Override
    public int getManaCost() { return 20; }

    @Override
    public int getTier() { return 3; }
}
