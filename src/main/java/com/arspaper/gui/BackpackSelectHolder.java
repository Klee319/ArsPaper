package com.arspaper.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 複数部位にバックパックがあるとき、どれを開くかを選ぶ画面。
 */
public final class BackpackSelectHolder implements InventoryHolder {

    private final List<ItemStack> pieces;
    private Inventory inventory;

    public BackpackSelectHolder(List<ItemStack> pieces) {
        this.pieces = List.copyOf(pieces);
    }

    public ItemStack pieceAt(int slot) {
        if (slot < 0 || slot >= pieces.size()) {
            return null;
        }
        return pieces.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
