package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 拘束の属性修飾子は NBT に残る。解除タスクだけだとログアウトで永久拘束になる（W-191）。
 * {@link ScaleRestoreListener} と同型。
 */
public class SnareRestoreListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduleRestore(player, 1L);
        scheduleRestore(player, 40L);
    }

    private static void scheduleRestore(Player player, long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    SnareEffect.restore(player);
                }
            }
        }.runTaskLater(ArsPaper.getInstance(), delayTicks);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Player || !(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.getPersistentDataContainer().has(
                    com.arspaper.mana.ManaKeys.SPELL_SNARE_END,
                    org.bukkit.persistence.PersistentDataType.LONG)) {
                SnareEffect.restore(living);
            }
        }
    }
}
