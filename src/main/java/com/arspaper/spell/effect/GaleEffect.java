package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 疾風エフェクト。対象にスピードまたは鈍足を付与する。
 * 増幅: スピードレベル上昇 | 減衰: 鈍足レベル上昇
 * 延長/短縮: 効果時間
 */
public class GaleEffect implements SpellEffect {

    private static final int BASE_DURATION = 400;            // 20秒
    private static final int DURATION_PER_LEVEL = 200;       // +10秒/段

    private final NamespacedKey id;
    private final GlyphConfig config;

    public GaleEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "gale");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("gale", "base-duration", BASE_DURATION);
        int durationPerLevel = (int) config.getParam("gale", "duration-per-level", DURATION_PER_LEVEL);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;

        int level = context.getAmplifyLevel();
        if (level >= 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, level));
            spawnSpeedFx(target.getLocation());
        } else {
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, duration, Math.abs(level) - 1));
            spawnSlowFx(target.getLocation());
        }
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    private void spawnSpeedFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0),
            10, 0.3, 0.2, 0.3, 0.1);
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.5, 0),
            3, 0.2, 0.1, 0.2, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_BREEZE_SHOOT,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    private void spawnSlowFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 0.5, 0),
            10, 0.3, 0.3, 0.3, 0.02);
        loc.getWorld().playSound(loc, Sound.BLOCK_POWDER_SNOW_STEP,
            SoundCategory.PLAYERS, 0.6f, 0.8f);
    }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "疾風"; }
    @Override public String getDescription() { return "対象にスピードを付与する（減衰で鈍足）"; }
    @Override public int getManaCost() { return config.getManaCost("gale"); }
    @Override public int getTier() { return config.getTier("gale"); }
}
