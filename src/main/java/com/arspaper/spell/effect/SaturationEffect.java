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
 * 満腹エフェクト。対象の満腹度を直接操作する。
 * 基本4 + 増幅×1 の満腹度を回復（空腹度1 = 0.5ゲージ）。
 * 減衰を積むと amplifyLevel が負になり、4 + |負値|×1 の満腹度を減少させる。
 */
public class SaturationEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SaturationEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "saturation");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        if (!(target instanceof Player player)) return;

        int baseFoodAmount = (int) config.getParam("saturation", "base-food-amount", 4.0);
        int foodPerAmplify = (int) config.getParam("saturation", "food-per-amplify", 1.0);
        int maxFoodAmount = (int) config.getParam("saturation", "max-food-amount", 6.0);
        int amplifyLevel = context.getAmplifyLevel();

        int amount = Math.min(maxFoodAmount,
            baseFoodAmount + Math.abs(amplifyLevel) * foodPerAmplify);

        if (amplifyLevel >= 0) {
            // 満腹度回復
            int newFood = Math.min(20, player.getFoodLevel() + amount);
            player.setFoodLevel(newFood);
            // 飽和度も少し回復
            float newSat = Math.min(newFood, player.getSaturation() + amount * 0.5f);
            player.setSaturation(newSat);
            spawnSaturationFx(target.getLocation());
        } else {
            // 満腹度減少
            int newFood = Math.max(0, player.getFoodLevel() - amount);
            player.setFoodLevel(newFood);
            player.setSaturation(Math.min(newFood, player.getSaturation()));
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
