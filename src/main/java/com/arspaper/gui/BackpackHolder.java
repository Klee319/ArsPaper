package com.arspaper.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * バックパックGUIのInventoryHolder。
 * GUIに紐づく防具ItemStack参照を保持し、タイトル文字列に依存しない堅牢な判別を可能にする。
 */
public final class BackpackHolder implements InventoryHolder {

    private final ItemStack armorItem;
    private Inventory inventory;

    public BackpackHolder(ItemStack armorItem) {
        this.armorItem = armorItem;
    }

    /**
     * このGUIを開いた元の防具ItemStack参照を返す。
     */
    public ItemStack getArmorItem() {
        return armorItem;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
