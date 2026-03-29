package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 半径ベースの範囲増強。爆発威力、召喚数、拾い範囲など
 * 内部AOE処理を行うエフェクト専用。
 * 水平/垂直のブロック・エンティティ展開には影響しない。
 */
public class AoeRadiusAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public AoeRadiusAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "aoe_radius");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        int bonus = (int) config.getParam("aoe_radius", "per-stack", 1.0);
        context.setAoeRadiusLevel(context.getAoeRadiusLevel() + bonus);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "半径増加"; }

    @Override
    public String getDescription() { return "爆発・召喚数等を拡大する"; }

    @Override
    public int getManaCost() { return config.getManaCost("aoe_radius"); }

    @Override
    public int getTier() { return config.getTier("aoe_radius"); }
}
