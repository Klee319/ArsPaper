package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 伝播増強。投射・接触のブロック着弾時に、
 * 範囲内のエンティティにもエフェクトを適用する。
 * 半径増加と組み合わせて範囲を拡大可能。
 */
public class PropagateAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public PropagateAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "propagate");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setPropagateActive(true);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "伝播"; }

    @Override
    public String getDescription() { return "ブロック着弾時に周辺エンティティにも効果を適用"; }

    @Override
    public int getManaCost() { return config.getManaCost("propagate"); }

    @Override
    public int getTier() { return config.getTier("propagate"); }
}
