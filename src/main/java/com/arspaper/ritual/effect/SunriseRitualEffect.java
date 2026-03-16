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
 * 日の出の儀式 - 時刻を朝に変更する。
 */
public class SunriseRitualEffect implements RitualEffect {

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        World world = coreLocation.getWorld();
        world.setTime(0);

        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        world.spawnParticle(Particle.END_ROD, effectLoc, 100, 2, 4, 2, 0.15);
        world.playSound(effectLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

        player.sendMessage(Component.text(
            "太陽が昇り、朝が訪れました。", NamedTextColor.GOLD
        ));
    }
}
