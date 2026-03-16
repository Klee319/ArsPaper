package com.arspaper.block.impl;

import com.arspaper.ArsPaper;
import com.arspaper.block.CustomBlock;
import com.arspaper.item.ItemKeys;
import com.arspaper.ritual.RitualIngredient;
import com.arspaper.util.ItemFrameHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Pedestal - 儀式の素材を置く台座。
 * Brewing Standベースのカスタムブロック。
 * 右クリックでアイテムの設置/取得を行う。
 *
 * 素材情報はTileState PDCに保存（Material名またはcustom:カスタムアイテムID）。
 */
public class Pedestal extends CustomBlock {

    /** 台座に載っているアイテムのMaterial名 */
    private static final NamespacedKey PEDESTAL_ITEM_KEY = new NamespacedKey("arspaper", "pedestal_item");

    /** 台座に載っているカスタムアイテムのID（カスタムアイテムの場合のみ） */
    private static final NamespacedKey PEDESTAL_CUSTOM_ID_KEY = new NamespacedKey("arspaper", "pedestal_custom_id");

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
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        // 空のItemFrameを配置
        ItemFrameHelper.spawnDisplayFrame(block.getLocation(), null);
    }

    @Override
    public void onBlockBroken(Player player, Block block, TileState tileState) {
        // 台座に載っているアイテムをドロップ
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String materialName = pdc.get(PEDESTAL_ITEM_KEY, PersistentDataType.STRING);
        String customId = pdc.get(PEDESTAL_CUSTOM_ID_KEY, PersistentDataType.STRING);
        if (materialName != null && !materialName.isEmpty()) {
            ItemStack storedItem = resolveStoredItem(materialName, customId);
            if (storedItem != null) {
                block.getWorld().dropItemNaturally(block.getLocation(), storedItem);
            }
        }
        // ItemFrameを除去
        ItemFrameHelper.removeDisplayFrame(block.getLocation());
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();

        String currentItem = pdc.get(PEDESTAL_ITEM_KEY, PersistentDataType.STRING);
        String currentCustomId = pdc.get(PEDESTAL_CUSTOM_ID_KEY, PersistentDataType.STRING);
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (currentItem != null && !currentItem.isEmpty()) {
            // 台座にアイテムがある → 取得
            ItemStack returnItem = resolveStoredItem(currentItem, currentCustomId);
            if (returnItem != null) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(returnItem);
                if (!overflow.isEmpty()) {
                    overflow.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
            }
            pdc.remove(PEDESTAL_ITEM_KEY);
            pdc.remove(PEDESTAL_CUSTOM_ID_KEY);
            tileState.update();
            ItemFrameHelper.updateDisplayFrame(block.getLocation(), null);
            player.sendMessage(Component.text("台座からアイテムを回収しました", NamedTextColor.YELLOW));
        } else if (!handItem.isEmpty() && handItem.getType() != Material.AIR) {
            // 手にアイテムがある → 台座に設置
            String materialName = handItem.getType().name();
            pdc.set(PEDESTAL_ITEM_KEY, PersistentDataType.STRING, materialName);

            // カスタムアイテムの場合はカスタムIDも保存
            String customId = handItem.getItemMeta() != null
                ? handItem.getItemMeta().getPersistentDataContainer()
                    .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING)
                : null;
            if (customId != null) {
                pdc.set(PEDESTAL_CUSTOM_ID_KEY, PersistentDataType.STRING, customId);
            } else {
                pdc.remove(PEDESTAL_CUSTOM_ID_KEY);
            }
            tileState.update();

            Material displayMaterial = handItem.getType(); // 消費前にMaterialを保存
            handItem.setAmount(handItem.getAmount() - 1);
            ItemFrameHelper.updateDisplayFrame(block.getLocation(), new ItemStack(displayMaterial));

            String displayName = customId != null ? "custom:" + customId : materialName;
            player.sendMessage(Component.text(displayName + " を台座に設置しました",
                NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("台座は空です", NamedTextColor.GRAY));
        }
    }

    /**
     * 保存されたアイテム情報からItemStackを復元する。
     */
    private static ItemStack resolveStoredItem(String materialName, String customId) {
        if (customId != null && !customId.isEmpty()) {
            return ArsPaper.getInstance().getItemRegistry()
                .get(customId)
                .map(item -> item.createItemStack())
                .orElseGet(() -> {
                    Material mat = Material.matchMaterial(materialName);
                    return mat != null ? new ItemStack(mat, 1) : null;
                });
        }
        Material mat = Material.matchMaterial(materialName);
        return mat != null ? new ItemStack(mat, 1) : null;
    }

    /**
     * このPedestalに載っている素材をRitualIngredientとして取得。
     */
    public static RitualIngredient getPedestalIngredient(TileState tileState) {
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String customId = pdc.get(PEDESTAL_CUSTOM_ID_KEY, PersistentDataType.STRING);
        if (customId != null && !customId.isEmpty()) {
            return RitualIngredient.ofCustom(customId);
        }

        String matName = pdc.get(PEDESTAL_ITEM_KEY, PersistentDataType.STRING);
        if (matName == null || matName.isEmpty()) return null;
        Material mat = Material.matchMaterial(matName);
        return mat != null ? RitualIngredient.ofMaterial(mat) : null;
    }

    /**
     * このPedestalに載っている素材をMaterialとして取得（後方互換）。
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
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.remove(PEDESTAL_ITEM_KEY);
        pdc.remove(PEDESTAL_CUSTOM_ID_KEY);
        tileState.update();
        // ItemFrame表示もクリア
        ItemFrameHelper.updateDisplayFrame(tileState.getLocation(), null);
    }
}
