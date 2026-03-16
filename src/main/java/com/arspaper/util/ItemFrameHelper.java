package com.arspaper.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

/**
 * 不可視エンティティによるアイテム表示ユーティリティ。
 * Pedestal: GlowItemFrame（ブロック上面にフラット表示）
 * RitualCore: ArmorStand head（エンチャント台の本の上に浮遊表示）
 */
public final class ItemFrameHelper {

    private static final String MARKER_TAG = "arspaper_display_frame";
    private static final String HEAD_DISPLAY_TAG = "arspaper_head_display";

    private ItemFrameHelper() {}

    // ========================================
    // ItemFrame方式（Pedestal等フラットブロック用）
    // ========================================

    /**
     * 指定ブロックの上面に不可視ItemFrameを生成してアイテムを表示する。
     */
    public static void spawnDisplayFrame(Location blockLoc, ItemStack displayItem) {
        Location spawnLoc = blockLoc.clone().add(0.5, 1.0, 0.5);

        blockLoc.getWorld().spawn(spawnLoc, GlowItemFrame.class, frame -> {
            frame.setFacingDirection(BlockFace.UP);
            frame.setVisible(false);
            frame.setFixed(true);
            frame.setInvulnerable(true);
            frame.setSilent(true);
            frame.setItem(displayItem != null ? displayItem : new ItemStack(Material.AIR));
            frame.setRotation(Rotation.NONE);
            frame.addScoreboardTag(MARKER_TAG);
        });
    }

    /**
     * 指定位置のArsPaper表示用ItemFrameを除去する。
     */
    public static void removeDisplayFrame(Location blockLoc) {
        Location center = blockLoc.clone().add(0.5, 1.0, 0.5);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 0.5, 0.5, 0.5)) {
            if (entity instanceof ItemFrame frame && frame.getScoreboardTags().contains(MARKER_TAG)) {
                frame.remove();
            }
        }
    }

    /**
     * 指定位置のItemFrameの表示アイテムを更新する。
     */
    public static void updateDisplayFrame(Location blockLoc, ItemStack newItem) {
        Location center = blockLoc.clone().add(0.5, 1.0, 0.5);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 0.5, 0.5, 0.5)) {
            if (entity instanceof ItemFrame frame && frame.getScoreboardTags().contains(MARKER_TAG)) {
                frame.setItem(newItem != null ? newItem : new ItemStack(Material.AIR));
                return;
            }
        }
        // 見つからなければ新規生成
        if (newItem != null && newItem.getType() != Material.AIR) {
            spawnDisplayFrame(blockLoc, newItem);
        }
    }

    // ========================================
    // ArmorStand head方式（エンチャント台等の非フラットブロック用）
    // ========================================

    /**
     * 指定ブロック上に不可視ArmorStandを生成し、ヘッド装備でアイテムを表示する。
     * エンチャント台の浮遊する本の上に配置されるよう、Y座標を調整。
     */
    public static void spawnHeadDisplay(Location blockLoc, ItemStack displayItem) {
        // ArmorStand(small)のヘッド位置: spawnY + ~0.7
        // エンチャント台(高さ0.75) + 本(~0.5) → Y+0.6で ArmorStandの頭がY+1.3付近に浮く
        Location spawnLoc = blockLoc.clone().add(0.5, 0.6, 0.5);

        blockLoc.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCanPickupItems(false);
            if (displayItem != null && displayItem.getType() != Material.AIR) {
                stand.getEquipment().setHelmet(displayItem);
            }
            stand.addScoreboardTag(HEAD_DISPLAY_TAG);
        });
    }

    /**
     * 指定位置のArsPaper表示用ArmorStandを除去する。
     */
    public static void removeHeadDisplay(Location blockLoc) {
        Location center = blockLoc.clone().add(0.5, 1.0, 0.5);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 1.0, 1.5, 1.0)) {
            if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(HEAD_DISPLAY_TAG)) {
                stand.remove();
            }
        }
    }

    /**
     * 指定位置のArmorStandのヘッド装備アイテムを更新する。
     */
    public static void updateHeadDisplay(Location blockLoc, ItemStack newItem) {
        Location center = blockLoc.clone().add(0.5, 1.0, 0.5);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 1.0, 1.5, 1.0)) {
            if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(HEAD_DISPLAY_TAG)) {
                if (newItem != null && newItem.getType() != Material.AIR) {
                    stand.getEquipment().setHelmet(newItem);
                } else {
                    stand.getEquipment().setHelmet(null);
                }
                return;
            }
        }
        // 見つからなければ新規生成
        if (newItem != null && newItem.getType() != Material.AIR) {
            spawnHeadDisplay(blockLoc, newItem);
        }
    }
}
