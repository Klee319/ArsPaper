package com.arspaper.ritual.effect;

import com.arspaper.ArsPaper;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 封じ込めの儀式 - 半径内のナチュラルモブスポーンを一時的に抑制する。
 * radius パラメータで半径、duration パラメータでtick数を指定。
 */
public class ContainmentRitualEffect implements RitualEffect {

    private static final int DEFAULT_RADIUS = 30;
    private static final int DEFAULT_DURATION = 6000; // 5分

    /** アクティブなContainmentリスナー。onDisableで一括解除用。 */
    private static final Set<Listener> activeListeners = ConcurrentHashMap.newKeySet();

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        int radius = DEFAULT_RADIUS;
        int duration = DEFAULT_DURATION;

        String radiusParam = recipe.effectParams().get("radius");
        if (radiusParam != null) {
            try { radius = Integer.parseInt(radiusParam); } catch (NumberFormatException ignored) {}
        }
        String durationParam = recipe.effectParams().get("duration");
        if (durationParam != null) {
            try { duration = Integer.parseInt(durationParam); } catch (NumberFormatException ignored) {}
        }

        Location center = coreLocation.clone().add(0.5, 0.5, 0.5);
        int finalRadius = radius;

        // スポーン抑制リスナーを登録
        Listener spawnBlocker = new Listener() {
            @EventHandler
            public void onCreatureSpawn(CreatureSpawnEvent event) {
                if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
                if (!event.getLocation().getWorld().equals(center.getWorld())) return;
                if (event.getLocation().distance(center) <= finalRadius) {
                    event.setCancelled(true);
                }
            }
        };

        ArsPaper.getInstance().getServer().getPluginManager()
            .registerEvents(spawnBlocker, ArsPaper.getInstance());
        activeListeners.add(spawnBlocker);

        // duration tick後にリスナーを解除
        new BukkitRunnable() {
            @Override
            public void run() {
                HandlerList.unregisterAll(spawnBlocker);
                activeListeners.remove(spawnBlocker);

                // 解除エフェクト
                if (center.getWorld() != null) {
                    center.getWorld().spawnParticle(Particle.SMOKE, center, 30, 2, 1, 2, 0.05);
                }
            }
        }.runTaskLater(ArsPaper.getInstance(), duration);

        // 発動エフェクト
        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 60, 2, 2, 2, 1.0);
        coreLocation.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, effectLoc, 30, 1, 1, 1, 0.05);
        coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.7f);

        int durationSeconds = duration / 20;
        player.sendMessage(Component.text(
            "半径 " + finalRadius + " ブロック内のモブスポーンを " + durationSeconds + " 秒間抑制します！",
            NamedTextColor.AQUA
        ));
    }

    /**
     * プラグイン停止時に全アクティブリスナーを解除する。
     */
    public static void cleanupAll() {
        for (Listener listener : activeListeners) {
            HandlerList.unregisterAll(listener);
        }
        activeListeners.clear();
    }
}
