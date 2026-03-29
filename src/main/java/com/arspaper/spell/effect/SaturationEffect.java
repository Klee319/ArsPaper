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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 満腹エフェクト。対象に満腹度回復または空腹デバフを付与する。
 * 増幅: 満腹レベル上昇 | 減衰: 空腹デバフレベル上昇
 * 延長/短縮: 効果時間
 */
public class SaturationEffect implements SpellEffect {

    private static final int BASE_DURATION = 200;            // 10秒
    private static final int DURATION_PER_LEVEL = 100;       // +5秒/段

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SaturationEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "saturation");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("saturation", "base-duration", BASE_DURATION);
        int durationPerLevel = (int) config.getParam("saturation", "duration-per-level", DURATION_PER_LEVEL);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;

        int level = context.getAmplifyLevel();
        if (level >= 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, duration, level));
            spawnSaturationFx(target.getLocation());
        } else {
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.HUNGER, duration, Math.abs(level) - 1));
            spawnHungerFx(target.getLocation());
        }
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    private void spawnSaturationFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0),
            8, 0.3, 0.4, 0.3, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_BURP,
            SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    private void spawnHungerFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0),
            8, 0.3, 0.4, 0.3, 0.02);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT,
            SoundCategory.PLAYERS, 0.5f, 0.7f);
    }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "満腹"; }
    @Override public String getDescription() { return "対象に満腹効果を付与する（減衰で空腹）"; }
    @Override public int getManaCost() { return config.getManaCost("saturation"); }
    @Override public int getTier() { return config.getTier("saturation"); }
}
