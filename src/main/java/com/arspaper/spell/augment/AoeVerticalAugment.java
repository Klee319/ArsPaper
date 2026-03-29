package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 垂直方向の範囲+1.0。ブロック/エンティティの影響範囲を上下に拡大。
 * 水平方向のAoeAugmentと組み合わせて立体的な範囲制御を実現する。
 */
public class AoeVerticalAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public AoeVerticalAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "aoe_vertical");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        int bonus = (int) config.getParam("aoe_vertical", "per-stack", 2.0);
        context.setAoeDepth(context.getAoeDepth() + bonus);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "範囲[法線]"; }

    @Override
    public String getDescription() { return "対象の面に垂直な方向に範囲を広げる"; }

    @Override
    public int getManaCost() { return config.getManaCost("aoe_vertical"); }

    @Override
    public int getTier() { return config.getTier("aoe_vertical"); }
}
