package com.arspaper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Geyser互換のボタン式GUI抽象基底クラス。
 * ドラッグ・シフトクリック等を全てキャンセルし、
 * スロットクリックのみで操作するシンプルな設計。
 */
public abstract class BaseGui implements InventoryHolder {

    protected final Inventory inventory;
    protected final Player viewer;

    protected BaseGui(Player viewer, int rows, Component title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    /**
     * GUIの内容を初期化・再描画する。
     */
    public abstract void render();

    /**
     * スロットクリック時の処理。
     * @return trueならイベントがハンドルされた
     */
    public abstract boolean onClick(int slot, Player clicker, InventoryClickEvent event);

    /**
     * GUI閉じた時のクリーンアップ処理。
     */
    public void onClose(Player player) {}

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * GUIを開く。
     */
    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    // === ユーティリティ ===

    protected ItemStack createButton(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return item;
    }

    protected ItemStack createButton(Material material, Component name, java.util.List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                .map(l -> l.decoration(TextDecoration.ITALIC, false))
                .toList());
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return item;
    }

    protected void fillBorder(Material material) {
        ItemStack filler = createButton(material, Component.text(""));
        int size = inventory.getSize();
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, filler);
            inventory.setItem(size - 9 + i, filler);
        }
        for (int i = 9; i < size - 9; i += 9) {
            inventory.setItem(i, filler);
            inventory.setItem(i + 8, filler);
        }
    }

    protected void fillRow(int row, Material material) {
        ItemStack filler = createButton(material, Component.text(""));
        int start = row * 9;
        for (int i = start; i < start + 9; i++) {
            inventory.setItem(i, filler);
        }
    }
}
