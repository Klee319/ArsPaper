package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * シルクタッチ効果。Break→シルクタッチ、Explosion→ブロックドロップ。
 * Fortuneと排他。
 */
public class ExtractAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public ExtractAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "extract");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        int bonus = (int) config.getParam("extract", "per-stack", 1.0);
        context.setExtractCount(context.getExtractCount() + bonus);
        // Fortuneと排他: Extractが有効ならFortuneを無効化
        if (context.getFortuneLevel() > 0) {
            context.setFortuneLevel(0);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "抽出"; }

    @Override
    public String getDescription() { return "シルクタッチ効果を付与する"; }

    @Override
    public int getManaCost() { return config.getManaCost("extract"); }

    @Override
    public int getTier() { return config.getTier("extract"); }
}
