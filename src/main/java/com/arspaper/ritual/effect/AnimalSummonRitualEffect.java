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
 * 動物召喚の儀式 - ランダムなパッシブモブを召喚する。
 * count パラメータで召喚数を指定（デフォルト5匹）。
 */
public class AnimalSummonRitualEffect implements RitualEffect {

    private static final int DEFAULT_COUNT = 5;

    private static final List<EntityType> SUMMONABLE_ANIMALS = List.of(
        EntityType.COW,
        EntityType.SHEEP,
        EntityType.PIG,
        EntityType.CHICKEN,
        EntityType.RABBIT,
        EntityType.HORSE,
        EntityType.DONKEY,
        EntityType.GOAT
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
            EntityType type = SUMMONABLE_ANIMALS.get(random.nextInt(SUMMONABLE_ANIMALS.size()));
            double offsetX = random.nextDouble(-3, 3);
            double offsetZ = random.nextDouble(-3, 3);
            Location spawnLoc = spawnCenter.clone().add(offsetX, 0, offsetZ);

            coreLocation.getWorld().spawnEntity(spawnLoc, type);

            // 個別パーティクル
            coreLocation.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER, spawnLoc.clone().add(0, 0.5, 0),
                10, 0.3, 0.3, 0.3, 0
            );
        }

        // 完了エフェクト
        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.HEART, effectLoc, 20, 2, 1, 2, 0.1);
        coreLocation.getWorld().playSound(effectLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        player.sendMessage(Component.text(
            count + " 匹の動物を召喚しました！", NamedTextColor.GREEN
        ));
    }
}
