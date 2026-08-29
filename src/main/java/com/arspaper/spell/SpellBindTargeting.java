package com.arspaper.spell;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * スペルのバインド／アンバインド対象スロット。
 *
 * <p>オフハンドに何かあれば常にオフハンド。空のときだけホットバー9枠目(Bukkit index 8)。
 * オフハンドが魔導書でもホットバーへフォールバックしない。
 */
public final class SpellBindTargeting {

    /** ホットバー右端。プレイヤーから見た「9枠目」。 */
    public static final int HOTBAR_SLOT_9 = 8;

    public enum Slot {
        OFFHAND,
        HOTBAR_9,
        NONE
    }

    private SpellBindTargeting() {
    }

    /**
     * オフハンドが占有されていればオフハンド。そうでなければホットバー9が占有されていればそちら。
     */
    public static Slot resolve(boolean offhandOccupied, boolean hotbar9Occupied) {
        if (offhandOccupied) {
            return Slot.OFFHAND;
        }
        if (hotbar9Occupied) {
            return Slot.HOTBAR_9;
        }
        return Slot.NONE;
    }

    public static boolean isOccupied(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    /**
     * オフハンド優先、空ならホットバー9。どちらも空なら null。
     */
    public static ItemStack occupiedTarget(Player player) {
        if (player == null) {
            return null;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack offhand = inv.getItemInOffHand();
        ItemStack hotbar9 = inv.getItem(HOTBAR_SLOT_9);
        Slot slot = resolve(isOccupied(offhand), isOccupied(hotbar9));
        return switch (slot) {
            case OFFHAND -> offhand;
            case HOTBAR_9 -> hotbar9;
            case NONE -> null;
        };
    }
}
