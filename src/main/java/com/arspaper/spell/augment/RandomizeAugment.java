package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ランダム性付与。各Effectで固有の挙動変更を引き起こす。
 * PlaceBlock/Exchange→ランダムブロック、Delay→±25%変動、
 * Break→25%確率スキップ等。
 */
public class RandomizeAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public RandomizeAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "randomize");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setRandomizing(true);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "無作為"; }

    @Override
    public String getDescription() { return "効果にランダム性を付与する"; }

    @Override
    public int getManaCost() { return config.getManaCost("randomize"); }

    @Override
    public int getTier() { return config.getTier("randomize"); }
}
