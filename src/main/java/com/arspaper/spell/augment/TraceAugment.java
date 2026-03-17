package com.arspaper.spell.augment;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 軌跡増強: 投射/照射の軌道に沿って後続エフェクトを適用する。
 * 投射: 雪玉の飛行経路上のブロックに効果
 * 照射: ビームの直線上のブロックに効果
 */
public class TraceAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public TraceAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "trace");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setTraceActive(true);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "軌跡"; }

    @Override
    public String getDescription() { return "飛行経路上にも効果を適用する"; }

    @Override
    public int getManaCost() { return config.getManaCost("trace"); }

    @Override
    public int getTier() { return config.getTier("trace"); }
}
