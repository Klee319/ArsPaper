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
 * 対象に落下速度軽減（Slow Falling）を付与するEffect。
 * 基本持続: 100tick (5秒)
 * ExtendTime: +100tick/level (5秒)、ただしlevel 5以上は+200tick/level
 * → 0:5秒 / 1:10秒 / 2:15秒 / 3:20秒 / 4:25秒 / 5:35秒
 */
public class SlowfallEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 100;       // 5秒
    private static final int DURATION_BONUS_TICKS = 100;      // 5秒/level
    private static final int HIGH_LEVEL_THRESHOLD = 5;        // 5以上で加速

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SlowfallEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "slowfall");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int level = context.getDurationLevel();
        int baseDurationTicks = (int) config.getParam("slowfall", "base-duration-ticks", (double) BASE_DURATION_TICKS);
        int durationTicks = baseDurationTicks + level * DURATION_BONUS_TICKS;
        // レベル5以上: 追加ボーナス（高スタック報酬）
        if (level >= HIGH_LEVEL_THRESHOLD) {
            durationTicks += (level - HIGH_LEVEL_THRESHOLD + 1) * DURATION_BONUS_TICKS;
        }
        durationTicks = Math.max(1, durationTicks);

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
            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, org.bukkit.SoundCategory.PLAYERS, 0.3f, 1.5f);
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
