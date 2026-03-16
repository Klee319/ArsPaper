package com.arspaper.spell.augment;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 後続のEffect実行を遅延させる増強。
 * 遅延時間: 20tick (1秒) ベース + durationLevel × 20tick。
 */
public class DelayAugment implements SpellAugment {

    private static final int BASE_DELAY_TICKS = 20;
    private static final int DURATION_BONUS_TICKS = 20;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public DelayAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "delay");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        int delay = BASE_DELAY_TICKS + context.getDurationLevel() * DURATION_BONUS_TICKS;
        if (context.isRandomizing()) {
            double variation = 1.0 + (Math.random() * 0.5 - 0.25);
            delay = (int) (delay * variation);
        }
        context.setDelayTicks(context.getDelayTicks() + Math.max(1, delay));
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "遅延"; }

    @Override
    public String getDescription() { return "後続の効果を遅延させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("delay"); }

    @Override
    public int getTier() { return config.getTier("delay"); }
}
