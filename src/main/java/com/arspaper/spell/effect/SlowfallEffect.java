package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象に落下速度軽減（Slow Falling）を付与するEffect。Ars Nouveau準拠。
 * 基本持続: 600tick (30秒)
 * ExtendTime: +160tick/level (8秒)
 * DurationDown: -160tick/level (8秒減少)
 */
public class SlowfallEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 600;  // 30秒
    private static final int DURATION_BONUS_TICKS = 160; // 8秒/level

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SlowfallEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "slowfall");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int durationTicks = Math.max(1,
            BASE_DURATION_TICKS + context.getDurationLevel() * DURATION_BONUS_TICKS);

        target.addPotionEffect(
            new PotionEffect(PotionEffectType.SLOW_FALLING, durationTicks, 0, false, true, true));

        spawnSlowfallFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnSlowfallFx(Location loc) {
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.END_ROD, loc.clone().add(0, 2, 0),
            12, 0.3, 0.3, 0.3, 0.02);
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.CLOUD, loc.clone().add(0, 1.5, 0),
            6, 0.4, 0.2, 0.4, 0.01);
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.ITEM_ELYTRA_FLYING, org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "緩落"; }

    @Override
    public String getDescription() { return "落下速度を軽減する"; }

    @Override
    public int getManaCost() { return config.getManaCost("slowfall"); }

    @Override
    public int getTier() { return config.getTier("slowfall"); }
}
