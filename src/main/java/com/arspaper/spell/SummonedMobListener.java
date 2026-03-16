package com.arspaper.spell;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 召喚モブが死亡した際にアイテムドロップとXPドロップを無効化するリスナー。
 * PDCキー arspaper:summoned が付与されたエンティティが対象。
 */
public class SummonedMobListener implements Listener {

    private final NamespacedKey summonedKey;

    public SummonedMobListener(JavaPlugin plugin) {
        this.summonedKey = new NamespacedKey(plugin, "summoned");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(summonedKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }
}
