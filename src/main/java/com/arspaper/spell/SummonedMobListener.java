package com.arspaper.spell;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 召喚モブの制御リスナー。
 * - 死亡時: アイテムドロップとXPドロップを無効化
 * - ターゲット: キャスターへの攻撃を防止、他の召喚モブへの攻撃を防止
 *
 * PDCキー:
 *   arspaper:summoned (BYTE) - 召喚モブマーカー
 *   arspaper:summoner_uuid (STRING) - 召喚者のUUID
 */
public class SummonedMobListener implements Listener {

    private final NamespacedKey summonedKey;
    private final NamespacedKey summonerUuidKey;

    public SummonedMobListener(JavaPlugin plugin) {
        this.summonedKey = new NamespacedKey(plugin, "summoned");
        this.summonerUuidKey = new NamespacedKey(plugin, "summoner_uuid");
    }

    public NamespacedKey getSummonedKey() { return summonedKey; }
    public NamespacedKey getSummonerUuidKey() { return summonerUuidKey; }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(summonedKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /**
     * 召喚モブがキャスターまたは他の召喚モブをターゲットするのを防止する。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;

        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        if (!pdc.has(summonedKey, PersistentDataType.BYTE)) return;

        LivingEntity target = event.getTarget();
        if (target == null) return;

        // キャスターへのターゲットを防止
        String summonerUuid = pdc.get(summonerUuidKey, PersistentDataType.STRING);
        if (summonerUuid != null && target instanceof Player targetPlayer) {
            if (targetPlayer.getUniqueId().toString().equals(summonerUuid)) {
                event.setCancelled(true);
                return;
            }
        }

        // 他の召喚モブへのターゲットを防止
        if (target.getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
