package com.arspaper.spell.augment;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 軌跡増強: 照射形態のビーム軌道上にも後続の効果を与える。
 * この効果を付けているとき、長押しでレーザーを打ち続けることができる（チャネリング）。
 * 消費マナ = スペルマナ × 継続時間[秒]
 */
public class TrailAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public TrailAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "trail");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setTrailActive(true);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "軌跡"; }

    @Override
    public String getDescription() { return "ビーム軌道上に効果を適用する"; }

    @Override
    public int getManaCost() { return config.getManaCost("trail"); }

    @Override
    public int getTier() { return config.getTier("trail"); }
}
