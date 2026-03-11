package com.arspaper.block;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * 不可視ArmorStand (Marker:1) を使ったカスタムブロックの見た目管理モジュール。
 * Geyser（統合版）互換のため、Display Entityではなくこの方式を採用。
 *
 * ArmorStandはブロック中心に配置され、頭装備でカスタムモデルを表示する。
 */
public final class BlockDisplayModule {

    private static final double Y_OFFSET = -0.5;
    private static final double SEARCH_RADIUS = 0.5;

    private BlockDisplayModule() {}

    /**
     * カスタムブロック位置にArmorStandを生成して見た目を付与する。
     *
     * @param plugin    プラグイン
     * @param blockLoc  ブロックのLocation
     * @param blockId   カスタムブロックID
     * @param headItem  ArmorStandの頭装備（カスタムモデル表示用）
     * @return 生成されたArmorStand
     */
    public static ArmorStand spawn(JavaPlugin plugin, Location blockLoc, String blockId, ItemStack headItem) {
        // ブロック中心に配置
        Location spawnLoc = blockLoc.clone().add(0.5, Y_OFFSET, 0.5);

        return blockLoc.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);           // 当たり判定なし
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCanPickupItems(false);
            stand.setCollidable(false);
            stand.setCustomNameVisible(false);
            stand.setBasePlate(false);

            // 頭装備でカスタムモデルを表示
            if (headItem != null) {
                stand.setItem(EquipmentSlot.HEAD, headItem);
            }

            // PDCにブロックIDを記録（検索・除去用）
            stand.getPersistentDataContainer().set(
                BlockKeys.DISPLAY_MARKER, PersistentDataType.STRING, blockId
            );
        });
    }

    /**
     * 指定位置のカスタムブロック用ArmorStandを除去する。
     *
     * @param blockLoc ブロックのLocation
     */
    public static void remove(Location blockLoc) {
        Location center = blockLoc.clone().add(0.5, Y_OFFSET, 0.5);

        for (Entity entity : center.getWorld().getNearbyEntities(center, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (!stand.getPersistentDataContainer().has(BlockKeys.DISPLAY_MARKER)) continue;
            stand.remove();
        }
    }

    /**
     * 指定位置のカスタムブロック用ArmorStandを取得する。
     */
    public static Optional<ArmorStand> find(Location blockLoc) {
        Location center = blockLoc.clone().add(0.5, Y_OFFSET, 0.5);

        for (Entity entity : center.getWorld().getNearbyEntities(center, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (!stand.getPersistentDataContainer().has(BlockKeys.DISPLAY_MARKER)) continue;
            return Optional.of(stand);
        }
        return Optional.empty();
    }

    /**
     * ArmorStandの頭装備を更新する（状態変化時の見た目切替用）。
     */
    public static void updateDisplay(Location blockLoc, ItemStack newHeadItem) {
        find(blockLoc).ifPresent(stand ->
            stand.setItem(EquipmentSlot.HEAD, newHeadItem)
        );
    }
}
