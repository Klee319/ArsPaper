package com.arspaper.ritual.effect;

import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 敵モブ召喚の儀式 - ランダムな敵対モブを召喚する。
 * count パラメータで召喚数を指定（デフォルト3匹）。
 */
public class MobSummonRitualEffect implements RitualEffect {

    private static final int DEFAULT_COUNT = 3;

    private static final List<EntityType> SUMMONABLE_MOBS = List.of(
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.SPIDER,
        EntityType.CREEPER,
        EntityType.WITCH,
        EntityType.PILLAGER,
        EntityType.VINDICATOR,
        EntityType.BLAZE
    );

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        int count = DEFAULT_COUNT;
        String countParam = recipe.effectParams().get("count");
        if (countParam != null) {
            try { count = Integer.parseInt(countParam); } catch (NumberFormatException ignored) {}
        }

        Location spawnCenter = coreLocation.clone().add(0.5, 1.0, 0.5);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            EntityType type = SUMMONABLE_MOBS.get(random.nextInt(SUMMONABLE_MOBS.size()));
            double angle = random.nextDouble(Math.PI * 2);
            double dist = 2 + random.nextDouble(4); // 2〜6ブロック離れた位置
            double offsetX = Math.cos(angle) * dist;
            double offsetZ = Math.sin(angle) * dist;
            Location spawnLoc = spawnCenter.clone().add(offsetX, 0, offsetZ);

            coreLocation.getWorld().spawnEntity(spawnLoc, type);

            coreLocation.getWorld().spawnParticle(
                Particle.SMOKE, spawnLoc.clone().add(0, 0.5, 0),
                15, 0.3, 0.5, 0.3, 0.02
            );
        }

        // 完了エフェクト
        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, effectLoc, 40, 2, 1, 2, 0.05);
        coreLocation.getWorld().playSound(effectLoc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);

        player.sendMessage(Component.text(
            count + " 体の敵モブを召喚しました！", NamedTextColor.RED
        ));
    }
}
