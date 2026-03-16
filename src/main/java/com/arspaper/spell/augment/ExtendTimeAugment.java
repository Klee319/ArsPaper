package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 持続時間+1.0。各エフェクトが個別に解釈する。
 * Ars Nouveau: durationMultiplier += 1.0
 * 例: Ignite=+2秒/stack, Harm=Poison化, Light=一時光源化
 */
public class ExtendTimeAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public ExtendTimeAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "extend_time");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setDurationLevel(context.getDurationLevel() + 1);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "延長"; }

    @Override
    public String getDescription() { return "効果の持続時間を延長する"; }

    @Override
    public int getManaCost() { return config.getManaCost("extend_time"); }

    @Override
    public int getTier() { return config.getTier("extend_time"); }
}
