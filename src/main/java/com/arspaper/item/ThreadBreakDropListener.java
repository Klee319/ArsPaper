package com.arspaper.item;

import com.arspaper.gui.SocketedThreadReturn;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;

/**
 * 装備が壊れたとき、挿してあったスレッドを GUI 取り外しと同じ個体として返す。
 *
 * <p>装着データは装備 PDC（{@link ItemKeys#THREAD_SLOTS} ほか）にしか無い。
 * バニラの破壊はスタックごと消すので、ここで組み直さないとスレッドも消える。
 * TF の手書き耐久破壊（連鎖採掘・ダンジョンペナルティ）は {@code PlayerItemBreakEvent}
 * を飛ばしていなかったので、そちらも同イベントを撃つ契約に揃える。
 */
public final class ThreadBreakDropListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        SocketedThreadReturn.returnFromBrokenGear(event.getPlayer(), event.getBrokenItem());
    }
}
