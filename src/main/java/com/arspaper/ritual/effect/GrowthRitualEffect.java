package com.arspaper.ritual.effect;

import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;

/**
 * 成長の儀式 - 半径内のAgeable(作物)を全て最大成長させる。
 */
public class GrowthRitualEffect implements RitualEffect {

    private static final int RADIUS = 10;

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        int radius = RADIUS;
        String radiusParam = recipe.effectParams().get("radius");
        if (radiusParam != null) {
            try { radius = Integer.parseInt(radiusParam); } catch (NumberFormatException ignored) {}
        }

        int grownCount = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = coreLocation.getBlock().getRelative(x, y, z);
                    if (block.getBlockData() instanceof Ageable ageable) {
                        if (ageable.getAge() < ageable.getMaximumAge()) {
                            ageable.setAge(ageable.getMaximumAge());
                            block.setBlockData(ageable);
                            grownCount++;
                            // 個別パーティクル
                            block.getWorld().spawnParticle(
                                Particle.HAPPY_VILLAGER,
                                block.getLocation().add(0.5, 0.8, 0.5),
                                5, 0.3, 0.2, 0.3, 0
                            );
                        }
                    }
                }
            }
        }

        // 完了エフェクト
        Location effectLoc = coreLocation.clone().add(0.5, 1.5, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLoc, 50, 3, 1, 3, 0);
        coreLocation.getWorld().playSound(effectLoc, Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);

        player.sendMessage(net.kyori.adventure.text.Component.text(
            grownCount + " 個の作物を成長させました！",
            net.kyori.adventure.text.format.NamedTextColor.GREEN
        ));
    }
}
