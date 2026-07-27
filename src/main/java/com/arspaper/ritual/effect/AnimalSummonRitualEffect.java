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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 動物召喚の儀式。
 * <ul>
 *   <li>{@code count} — 召喚数（既定5）</li>
 *   <li>{@code entities} — カンマ区切り EntityType。未指定時は既定友好モブ</li>
 * </ul>
 */
public class AnimalSummonRitualEffect implements RitualEffect {

    private static final int DEFAULT_COUNT = 5;

    private static final List<EntityType> DEFAULT_ANIMALS = List.of(
        EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN,
        EntityType.RABBIT, EntityType.HORSE, EntityType.DONKEY, EntityType.GOAT
    );

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        int count = DEFAULT_COUNT;
        String countParam = recipe.effectParams().get("count");
        if (countParam != null) {
            try { count = Integer.parseInt(countParam); } catch (NumberFormatException ignored) {}
        }

        List<EntityType> animals = resolveAnimals(recipe.effectParams());
        Location spawnCenter = coreLocation.clone().add(0.5, 1.0, 0.5);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            EntityType type = animals.get(random.nextInt(animals.size()));
            Location spawnLoc = spawnCenter.clone().add(random.nextDouble(-3, 3), 0, random.nextDouble(-3, 3));
            coreLocation.getWorld().spawnEntity(spawnLoc, type);
            coreLocation.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER, spawnLoc.clone().add(0, 0.5, 0),
                10, 0.3, 0.3, 0.3, 0
            );
        }

        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.HEART, effectLoc, 20, 2, 1, 2, 0.1);
        coreLocation.getWorld().playSound(effectLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(Component.text(count + " 匹の動物を召喚しました！", NamedTextColor.GREEN));
    }

    static List<EntityType> resolveAnimals(Map<String, String> params) {
        List<EntityType> custom = MobSummonRitualEffect.parseEntitiesParam(
            params != null ? params.get("entities") : null);
        return custom.isEmpty() ? DEFAULT_ANIMALS : custom;
    }
}
