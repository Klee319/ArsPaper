package com.arspaper.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * バックパックGUIのInventoryHolder。
 * GUIに紐づく防具ItemStack参照と、54超のときのページ位置を保持する。
 */
public final class BackpackHolder implements InventoryHolder {

    private final ItemStack armorItem;
    private final int page;
    private final int capacity;
    private final boolean paginated;
    private Inventory inventory;
    /** ページ送りで差し替えたとき、閉じるイベントの二重保存を飛ばす。 */
    private boolean suppressCloseSave;

    public BackpackHolder(ItemStack armorItem) {
        this(armorItem, 0, 0, false);
    }

    public BackpackHolder(ItemStack armorItem, int page, int capacity, boolean paginated) {
        this.armorItem = armorItem;
        this.page = Math.max(0, page);
        this.capacity = Math.max(0, capacity);
        this.paginated = paginated;
    }

    /**
     * このGUIを開いた元の防具ItemStack参照を返す。
     */
    public ItemStack getArmorItem() {
        return armorItem;
    }

    public int getPage() {
        return page;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isPaginated() {
        return paginated;
    }

    public void suppressCloseSave() {
        this.suppressCloseSave = true;
    }

    public boolean isCloseSaveSuppressed() {
        return suppressCloseSave;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
