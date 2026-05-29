package com.arspaper.gui;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * バックパックGUIの識別用InventoryHolder。
 *
 * タイトル文字列ではなくHolder型でバックパックを識別することで、
 * 同名インベントリの偽装による任意防具へのデータ注入を防止する。
 * また、対象防具を装備スロット(EquipmentSlot)で一意に保持し、
 * 閉じる際に同一スロットの現在の防具へ無条件で保存する。
 */
public class BackpackHolder implements InventoryHolder {

    private final EquipmentSlot armorSlot;
    private Inventory inventory;

    public BackpackHolder(EquipmentSlot armorSlot) {
        this.armorSlot = armorSlot;
    }

    public EquipmentSlot getArmorSlot() {
        return armorSlot;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
