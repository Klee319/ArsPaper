package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 伝播増強。エンティティヒット時に周囲の敵にエフェクトをチェーンする。
 * 1つにつき最大3体。複数スタックで対象数が増加する。
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
        context.addPropagateChain(); // +3体/スタック
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "伝播"; }

    @Override
    public String getDescription() { return "エンティティヒット時に周囲の敵にチェーン（1段=3体）"; }

    @Override
    public int getManaCost() { return config.getManaCost("propagate"); }

    @Override
    public int getTier() { return config.getTier("propagate"); }
}
