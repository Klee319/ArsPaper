package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 後続のEffect実行を遅延させるEffect。Ars NouveauのEffectDelayに準拠。
 * 本家では後続Effectの実行を指定ティック後まで遅らせるが、
 * 本実装では遅延処理がスペルチェーン解決レベルで必要なため、
 * 現在はパーティクルによる視覚的フィードバックのみのプレースホルダーとして実装する。
 * 遅延時間: 20tick (1秒) ベース + durationLevel × 20tick。
 * Randomize付きで±25%の時間変動。
 */
public class DelayEffect implements SpellEffect {

    private static final int BASE_DELAY_TICKS = 20;
    private static final int DURATION_BONUS_TICKS = 20;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public DelayEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "delay");
        this.config = config;
    }

    /**
     * 遅延tickを計算する。SpellContextのresolve処理から呼ばれる。
     */
    public int calculateDelayTicks(SpellContext context) {
        int baseDelay = (int) config.getParam("delay", "base-delay-ticks", BASE_DELAY_TICKS);
        int durationBonus = (int) config.getParam("delay", "duration-bonus-ticks", DURATION_BONUS_TICKS);
        int delayTicks = baseDelay + context.getDurationLevel() * durationBonus;
        if (context.isRandomizing()) {
            double variation = 1.0 + (Math.random() * 0.5 - 0.25); // ±25%
            delayTicks = (int) (delayTicks * variation);
        }
        return Math.max(1, delayTicks);
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        SpellFxUtil.spawnDelayFx(target.getLocation(), calculateDelayTicks(context));
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        SpellFxUtil.spawnDelayFx(blockLocation, calculateDelayTicks(context));
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
