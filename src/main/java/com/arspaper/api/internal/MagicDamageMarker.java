package com.arspaper.api.internal;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * スペル由来ダメージの直前にターゲットPDCにcasterのUUIDを5tick TTLで書き込み、
 * EliteMobs等が EntityDamageEvent からマジックダメージとcasterを判定できるようにする。
 */
public final class MagicDamageMarker {

    private MagicDamageMarker() {}

    public static final NamespacedKey CASTER_KEY = new NamespacedKey("arspaper", "magic_damage_caster");
    public static final NamespacedKey EXPIRE_KEY = new NamespacedKey("arspaper", "magic_damage_expire_tick");

    private static final long TTL_TICKS = 5L;

    /** ターゲットにcasterのUUIDをmarkerとして書き込み、5tick後に削除する。 */
    public static void attach(JavaPlugin plugin, LivingEntity target, Player caster) {
        if (target == null || caster == null) return;
        long expireTick = plugin.getServer().getCurrentTick() + TTL_TICKS;
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        pdc.set(CASTER_KEY, PersistentDataType.STRING, caster.getUniqueId().toString());
        pdc.set(EXPIRE_KEY, PersistentDataType.LONG, expireTick);

        // TTL経過後に削除（target退場後でも安全に動くよう、entity reference を保持しない）
        UUID targetUuid = target.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            org.bukkit.entity.Entity ent = plugin.getServer().getEntity(targetUuid);
            if (ent instanceof LivingEntity le) {
                PersistentDataContainer p = le.getPersistentDataContainer();
                Long currentExpire = p.get(EXPIRE_KEY, PersistentDataType.LONG);
                // 別のmarkerで上書きされている場合は削除しない
                if (currentExpire != null && currentExpire <= expireTick) {
                    p.remove(CASTER_KEY);
                    p.remove(EXPIRE_KEY);
                }
            }
        }, TTL_TICKS);
    }

    /** EntityDamageEvent がマジックダメージかどうかを判定する。 */
    public static boolean isMagic(EntityDamageEvent e, JavaPlugin plugin) {
        if (!(e.getEntity() instanceof LivingEntity le)) return false;
        return getCaster(le, plugin) != null;
    }

    /** target に有効なmagic damage markerが付いている場合、caster Player を返す。 */
    public static Player getCaster(LivingEntity victim, JavaPlugin plugin) {
        PersistentDataContainer pdc = victim.getPersistentDataContainer();
        String uuidStr = pdc.get(CASTER_KEY, PersistentDataType.STRING);
        Long expire = pdc.get(EXPIRE_KEY, PersistentDataType.LONG);
        if (uuidStr == null || expire == null) return null;
        if (plugin.getServer().getCurrentTick() > expire) return null;
        try {
            return plugin.getServer().getPlayer(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
