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
 * 月落としの儀式 - 時刻を夜に変更する。
 */
public class MoonfallRitualEffect implements RitualEffect {

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        World world = coreLocation.getWorld();
        world.setTime(13000);

        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        world.spawnParticle(Particle.CLOUD, effectLoc, 80, 2, 3, 2, 0.05);
        world.spawnParticle(Particle.WITCH, effectLoc, 40, 1, 2, 1, 0.1);
        world.playSound(effectLoc, Sound.AMBIENT_CAVE, 1.0f, 0.5f);

        player.sendMessage(Component.text(
            "月が昇り、夜が訪れました。", NamedTextColor.DARK_PURPLE
        ));
    }
}
