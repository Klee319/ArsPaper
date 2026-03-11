package com.arspaper.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 全BaseGui派生クラスのクリック/ドラッグ/クローズイベントを統一処理するリスナー。
 * ScribingTableGuiは独自リスナーを持つため、BaseGui系のみ対象。
 */
public class GuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BaseGui gui)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != gui.getInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        gui.onClick(event.getSlot(), player, event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof BaseGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BaseGui gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        gui.onClose(player);
    }
}
