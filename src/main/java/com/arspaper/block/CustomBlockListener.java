package com.arspaper.block;

import com.arspaper.item.ItemKeys;
import com.arspaper.util.PdcHelper;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * カスタムブロックの設置・破壊・インタラクションイベントを処理するリスナー。
 *
 * 設置時: ItemStack PDC → TileState PDC へ転送 + ArmorStand生成
 * 破壊時: TileState PDC → ドロップItemStack PDC へ復元 + ArmorStand除去
 * インタラクション: カスタムブロックのonBlockInteractを呼び出し
 */
public class CustomBlockListener implements Listener {

    private final JavaPlugin plugin;
    private final CustomBlockRegistry registry;

    public CustomBlockListener(JavaPlugin plugin, CustomBlockRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Optional<String> customId = PdcHelper.getCustomItemId(item);
        if (customId.isEmpty()) return;

        Optional<CustomBlock> customBlock = registry.get(customId.get());
        if (customBlock.isEmpty()) return;

        Block block = event.getBlock();
        if (!(block.getState() instanceof TileState tileState)) return;

        CustomBlock cb = customBlock.get();

        // ItemStack PDC → TileState PDC 転送
        cb.writeToTileState(tileState, item);

        // ArmorStandによる見た目付与
        ItemStack displayHead = cb.getDisplayHeadItem();
        if (displayHead != null) {
            BlockDisplayModule.spawn(plugin, block.getLocation(), cb.getItemId(), displayHead);
        }

        // カスタムブロック固有の初期化
        cb.onBlockPlaced(event.getPlayer(), block, tileState);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof TileState tileState)) return;

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String blockId = pdc.get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) return;

        Optional<CustomBlock> customBlock = registry.get(blockId);
        if (customBlock.isEmpty()) return;

        CustomBlock cb = customBlock.get();

        // カスタムブロック固有の破壊処理
        cb.onBlockBroken(event.getPlayer(), block, tileState);

        // バニラドロップを抑制
        event.setDropItems(false);

        // TileState PDC → ドロップItemStack PDC 復元
        ItemStack drop = cb.createDropWithData(tileState);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);

        // ArmorStand除去
        BlockDisplayModule.remove(block.getLocation());
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof TileState tileState)) return;

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String blockId = pdc.get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) return;

        Optional<CustomBlock> customBlock = registry.get(blockId);
        if (customBlock.isEmpty()) return;

        // バニラのインタラクション（書見台のGUI等）をキャンセル
        event.setCancelled(true);

        customBlock.get().onBlockInteract(event.getPlayer(), block, tileState);
    }
}
