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
        context.setAoeVerticalLevel(context.getAoeVerticalLevel() + 1);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "範囲[垂直]"; }

    @Override
    public String getDescription() { return "ヒット面に対し垂直に効果の範囲を拡大する"; }

    @Override
    public int getManaCost() { return config.getManaCost("aoe_vertical"); }

    @Override
    public int getTier() { return config.getTier("aoe_vertical"); }
}
