package com.arspaper.ritual.effect;

import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 敵モブ召喚の儀式。
 * <ul>
 *   <li>{@code count} — 召喚数（既定3）</li>
 *   <li>{@code group} — 既定グループ default/raid/nether/variant</li>
 *   <li>{@code entities} — カンマ区切り EntityType 名。指定時は group より優先</li>
 * </ul>
 */
public class MobSummonRitualEffect implements RitualEffect {

    private static final int DEFAULT_COUNT = 3;

    private static final Map<String, List<EntityType>> MOB_GROUPS = Map.of(
        "default", List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.WITCH, EntityType.ENDERMAN
        ),
        "raid", List.of(
            EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.VEX,
            EntityType.RAVAGER, EntityType.EVOKER, EntityType.ILLUSIONER
        ),
        "nether", List.of(
            EntityType.BLAZE, EntityType.GHAST, EntityType.WITHER_SKELETON,
            EntityType.PIGLIN, EntityType.HOGLIN, EntityType.PIGLIN_BRUTE,
            EntityType.MAGMA_CUBE, EntityType.ZOMBIFIED_PIGLIN
        ),
        "variant", List.of(
            EntityType.BOGGED, EntityType.BREEZE, EntityType.STRAY, EntityType.HUSK,
            EntityType.SILVERFISH, EntityType.SLIME, EntityType.PHANTOM,
            EntityType.CAVE_SPIDER, EntityType.DROWNED, EntityType.GUARDIAN
        )
    );

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        int count = DEFAULT_COUNT;
        String countParam = recipe.effectParams().get("count");
        if (countParam != null) {
            try { count = Integer.parseInt(countParam); } catch (NumberFormatException ignored) {}
        }

        List<EntityType> mobList = resolveMobList(recipe.effectParams());
        World world = coreLocation.getWorld();
        if (world == null || mobList.isEmpty()) return;

        Location spawnCenter = coreLocation.clone().add(0.5, 1.0, 0.5);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            EntityType type = mobList.get(random.nextInt(mobList.size()));
            double angle = random.nextDouble(Math.PI * 2);
            double dist = 2 + random.nextDouble(4);
            Location spawnLoc = spawnCenter.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            world.spawnEntity(spawnLoc, type);
            world.spawnParticle(Particle.SMOKE, spawnLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.02);
        }

        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, effectLoc, 40, 2, 1, 2, 0.05);
        world.playSound(effectLoc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
        player.sendMessage(Component.text(count + " 体の敵モブを召喚しました！", NamedTextColor.RED));
    }

    static List<EntityType> resolveMobList(Map<String, String> params) {
        List<EntityType> fromEntities = parseEntitiesParam(params != null ? params.get("entities") : null);
        if (!fromEntities.isEmpty()) {
            return fromEntities;
        }
        String group = params != null ? params.getOrDefault("group", "default") : "default";
        return MOB_GROUPS.getOrDefault(group, MOB_GROUPS.get("default"));
    }

    /** YAML list は RitualRecipe 側でカンマ結合される想定。単一名も可。 */
    static List<EntityType> parseEntitiesParam(String raw) {
        List<EntityType> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,\\s]+")) {
            String key = part.trim().toUpperCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            try {
                EntityType type = EntityType.valueOf(key);
                if (type.isSpawnable() && type.isAlive()) {
                    out.add(type);
                }
            } catch (IllegalArgumentException ignored) {
                // skip unknown
            }
        }
        return out;
    }
}
