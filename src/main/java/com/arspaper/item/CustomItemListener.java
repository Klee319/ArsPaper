package com.arspaper.item;

import com.arspaper.util.PdcHelper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * カスタムアイテムのインタラクションイベントを各BaseCustomItemにディスパッチするリスナー。
 */
public class CustomItemListener implements Listener {

    private final CustomItemRegistry registry;

    public CustomItemListener(CustomItemRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // オフハンドの重複呼び出しを無視
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Optional<String> customId = PdcHelper.getCustomItemId(item);
        if (customId.isEmpty()) return;

        Optional<BaseCustomItem> customItem = registry.get(customId.get());
        if (customItem.isEmpty()) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            customItem.get().onRightClick(event);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            customItem.get().onLeftClick(event);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Optional<String> customId = PdcHelper.getCustomItemId(item);
        if (customId.isEmpty()) return;

        Optional<BaseCustomItem> customItem = registry.get(customId.get());
        if (customItem.isEmpty()) return;

        customItem.get().onPlace(event);
    }
}
