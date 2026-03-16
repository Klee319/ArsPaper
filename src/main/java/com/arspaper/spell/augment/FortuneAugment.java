package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 幸運効果。Break/Harvestのドロップ増。
 * Extractと排他。上限はglyphs.ymlのmax-augmentsで制御。
 */
public class FortuneAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public FortuneAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "fortune");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        // Extractと排他: Extractが有効ならFortuneは適用しない
        if (context.getExtractCount() > 0) return;
        context.setFortuneLevel(context.getFortuneLevel() + 1);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "幸運"; }

    @Override
    public String getDescription() { return "ドロップ量を増加させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("fortune"); }

    @Override
    public int getTier() { return config.getTier("fortune"); }
}
