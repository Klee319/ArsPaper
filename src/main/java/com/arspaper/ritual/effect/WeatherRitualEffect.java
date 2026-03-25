package com.arspaper.ritual.effect;

import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 天候操作の儀式 - コアアイテムに応じて天候を変更する。
 * mode パラメータ: clear / rain / thunder
 */
public class WeatherRitualEffect implements RitualEffect {

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        World world = coreLocation.getWorld();
        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);

        String mode = recipe.effectParams().getOrDefault("mode", "clear");

        switch (mode) {
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
                world.setWeatherDuration(6000);
                world.spawnParticle(Particle.CLOUD, effectLoc, 80, 2, 3, 2, 0.05);
                world.playSound(effectLoc, Sound.WEATHER_RAIN, 1.0f, 0.8f);
                player.sendMessage(Component.text("天候を雨にしました！", NamedTextColor.AQUA));
            }
            case "thunder" -> {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(6000);
                world.setThunderDuration(6000);
                world.spawnParticle(Particle.CLOUD, effectLoc, 80, 2, 3, 2, 0.05);
                world.playSound(effectLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
                player.sendMessage(Component.text("天候を雷雨にしました！", NamedTextColor.DARK_PURPLE));
            }
            default -> { // clear
                world.setStorm(false);
                world.setThundering(false);
                world.spawnParticle(Particle.END_ROD, effectLoc, 80, 2, 3, 2, 0.1);
                world.playSound(effectLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
                player.sendMessage(Component.text("天候を晴れにしました！", NamedTextColor.GOLD));
            }
        }
    }
}
