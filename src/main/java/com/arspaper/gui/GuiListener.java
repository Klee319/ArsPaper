package com.arspaper.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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
        if (event.getInventory().getHolder(false) instanceof BackpackSelectHolder select) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()
                    && event.getWhoClicked() instanceof Player player) {
                org.bukkit.inventory.ItemStack piece = select.pieceAt(event.getSlot());
                if (piece != null) {
                    BackpackGui.open(player, piece);
                }
            }
            return;
        }
        if (event.getInventory().getHolder(false) instanceof BackpackHolder holder) {
            ClickType click = event.getClick();
            // プレイヤーインベントリ側のダブルクリック吸引は、clickedInventory が下段でも
            // 上段のガラス／ナビを吸い取る。ロック枠クリック以外ではキャンセルしていなかった。
            // COLLECT_TO_CURSOR は Paper 新しめの別名。Ars のコンパイル対象 API には無い。
            if (click == ClickType.DOUBLE_CLICK || "COLLECT_TO_CURSOR".equals(click.name())) {
                event.setCancelled(true);
                return;
            }
            if (event.getClickedInventory() == event.getInventory()
                    && BackpackGui.isLockedSlot(holder, event.getSlot())) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    BackpackGui.handleLockedClick(holder, player, event.getSlot());
                }
            }
            return;
        }
        if (!(event.getInventory().getHolder(false) instanceof BaseGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ThreadGui: プレイヤーインベントリ側のクリックを許可（カーソルにスレッドを載せる操作）
        // ただしshift-click/number-keyはGUIへの不正アイテム移動を防止するためキャンセル
        //
        // 2026-07-31 (F3 指摘5): 【対象装備が入っているスロットだけは通さない】。
        // /ars thread の対象はホットバーのスタックで、そのスロットは開いている ThreadGui の
        // 下段に描画されている。拾ってカーソルへ載せたまま空き枠を押すと、カーソルはスレッドでは
        // ないので在庫のスレッドが 1 個消費される一方、GUI が握っている targetItem は
        // スロットから抜けた側なので書き込みが乗らない = プレイヤーは成功したと思って
        // スレッドを失う。ThreadGui 側の同一性再確認は最後の砦で、こちらは
        // 「そもそも動かせない」を担う(ドロップ/オフハンド入れ替え/数字キーも同様に塞ぐ)。
        if (gui instanceof ThreadGui threadGui && event.getClickedInventory() != gui.getInventory()) {
            if (event.isShiftClick() || event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                return;
            }
            if (touchesThreadTargetSlot(threadGui, event)) {
                event.setCancelled(true);
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "スレッド装着中の装備は動かせません",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            }
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() != gui.getInventory()) return;

        gui.onClick(event.getSlot(), player, event);
    }

    /** {@link #touchesTargetSlot} をイベントから読み取る薄いラッパ。 */
    private static boolean touchesThreadTargetSlot(ThreadGui gui, InventoryClickEvent event) {
        return touchesTargetSlot(gui.getTargetSlot(), event.getSlot(), event.getHotbarButton());
    }

    /**
     * スレッド装着中の対象スロットに触るクリックか。
     *
     * <p>対象スロット自体のクリック(左右クリック・{@code Q} のドロップ・{@code F} の
     * オフハンド入れ替えはすべて「そのスロットをクリックした」形で来る)と、
     * 数字キーの交換先が対象スロットの場合を弾く。
     * {@code targetSlot} が {@link ThreadGui#UNKNOWN_TARGET_SLOT}(負)なら守る対象が無い。
     *
     * <p>Bukkit を触らない純粋な判定にしてある(このフォークのテスト基盤は MockBukkit を持たない)。
     */
    static boolean touchesTargetSlot(int targetSlot, int clickedSlot, int hotbarButton) {
        if (targetSlot < 0) {
            return false;
        }
        return clickedSlot == targetSlot || hotbarButton == targetSlot;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof BackpackHolder holder) {
            for (int raw : event.getRawSlots()) {
                if (BackpackGui.isLockedSlot(holder, raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
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

        // バックパックGUI: holder型（BackpackHolder）で判別し、装備中の防具PDCにデータ保存。
        // タイトル文字列一致は脆弱なため、holderにより堅牢に識別する。
        if (event.getInventory().getHolder(false) instanceof BackpackHolder holder
                && event.getPlayer() instanceof Player player) {
            if (holder.isCloseSaveSuppressed()) {
                return;
            }
            BackpackGui.saveFromHolder(holder, player);
            org.bukkit.inventory.ItemStack armor = holder.getArmorItem();
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            if (inv.getHelmet() == armor) {
                inv.setHelmet(armor);
            } else if (inv.getChestplate() == armor) {
                inv.setChestplate(armor);
            } else if (inv.getLeggings() == armor) {
                inv.setLeggings(armor);
            } else if (inv.getBoots() == armor) {
                inv.setBoots(armor);
            }
        }
    }
}
