package com.arspaper.gui;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ThreadGui: プレイヤーインベントリ側のクリックを許可（カーソルにスレッドを載せる操作）
        // ただしshift-click/number-keyはGUIへの不正アイテム移動を防止するためキャンセル
        if (gui instanceof ThreadGui && event.getClickedInventory() != gui.getInventory()) {
            if (event.isShiftClick() || event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() != gui.getInventory()) return;

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
        if (event.getInventory().getHolder(false) instanceof BaseGui gui) {
            if (event.getPlayer() instanceof Player player) {
                gui.onClose(player);
            }
            return;
        }

        // バックパックGUI: タイトルで判別し、装備中の防具PDCにデータ保存
        if (event.getPlayer() instanceof Player player && event.getView().title() != null) {
            String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
            if ("バックパック".equals(title)) {
                // getArmorContents()はコピーを返すため、直接スロットを参照して書き戻す
                org.bukkit.inventory.PlayerInventory inv = player.getInventory();
                org.bukkit.inventory.ItemStack[] armorSlots = {
                    inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()
                };
                for (int s = 0; s < armorSlots.length; s++) {
                    org.bukkit.inventory.ItemStack armor = armorSlots[s];
                    if (armor != null && BackpackGui.countBackpackThreads(armor) > 0) {
                        BackpackGui.saveBackpackContents(armor, event.getInventory());
                        // editMetaで変更されたItemStackを装備スロットに書き戻す
                        switch (s) {
                            case 0 -> inv.setHelmet(armor);
                            case 1 -> inv.setChestplate(armor);
                            case 2 -> inv.setLeggings(armor);
                            case 3 -> inv.setBoots(armor);
                        }
                        break;
                    }
                }
            }
        }
    }
}
