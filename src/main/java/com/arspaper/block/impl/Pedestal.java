package com.arspaper.block.impl;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Pedestal - 儀式の素材を置く台座。
 * Brewing Standベースのカスタムブロック。
 * 右クリックでアイテムの設置/取得を行う。
 *
 * 素材情報はTileState PDCに保存（Material名）。
 */
public class Pedestal extends CustomBlock {

    /** 台座に載っているアイテムのMaterial名 */
    private static final NamespacedKey PEDESTAL_ITEM_KEY = new NamespacedKey("arspaper", "pedestal_item");

    public Pedestal(JavaPlugin plugin) {
        super(plugin, "pedestal");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.BREWING_STAND;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("台座", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 300002;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("儀式用アイテムを設置する台座", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("アイテムを持って右クリックで設置", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.PAPER);
        head.editMeta(meta -> meta.setCustomModelData(300002));
        return head;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        var pdc = tileState.getPersistentDataContainer();

        String currentItem = pdc.get(PEDESTAL_ITEM_KEY, PersistentDataType.STRING);
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (currentItem != null && !currentItem.isEmpty()) {
            // 台座にアイテムがある → 取得
            Material mat = Material.matchMaterial(currentItem);
            if (mat != null) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(mat, 1));
                if (!overflow.isEmpty()) {
                    // インベントリ満杯 → 足元にドロップ
                    overflow.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
            }
            pdc.remove(PEDESTAL_ITEM_KEY);
            tileState.update();
            player.sendMessage(Component.text("台座からアイテムを回収しました", NamedTextColor.YELLOW));
        } else if (!handItem.isEmpty() && handItem.getType() != Material.AIR) {
            // 手にアイテムがある → 台座に設置
            String materialName = handItem.getType().name();
            pdc.set(PEDESTAL_ITEM_KEY, PersistentDataType.STRING, materialName);
            tileState.update();

            handItem.setAmount(handItem.getAmount() - 1);
            player.sendMessage(Component.text(materialName + " を台座に設置しました",
                NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("台座は空です", NamedTextColor.GRAY));
        }
    }

    /**
     * このPedestalに載っている素材を取得。
     */
    public static Material getPedestalItem(TileState tileState) {
        String matName = tileState.getPersistentDataContainer()
            .get(PEDESTAL_ITEM_KEY, PersistentDataType.STRING);
        if (matName == null || matName.isEmpty()) return null;
        return Material.matchMaterial(matName);
    }

    /**
     * Pedestalの素材を消費（儀式完了時）。
     */
    public static void clearPedestalItem(TileState tileState) {
        tileState.getPersistentDataContainer().remove(PEDESTAL_ITEM_KEY);
        tileState.update();
    }
}
