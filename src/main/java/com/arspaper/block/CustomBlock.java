package com.arspaper.block;

import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * カスタムブロックの抽象基底クラス。
 * BaseCustomItemを継承し、設置時にTileState PDCへデータ転送、
 * 破壊時にドロップItemStackへデータ復元する。
 */
public abstract class CustomBlock extends BaseCustomItem {

    protected CustomBlock(JavaPlugin plugin, String blockId) {
        super(plugin, blockId);
    }

    /**
     * カスタムブロックが使用するバニラブロックのMaterial。
     * LECTERNやBARRELなど、TileStateを持つブロックを返す。
     */
    public abstract Material getBlockMaterial();

    /**
     * ArmorStandの頭に装備するアイテム（見た目用）。
     * nullの場合はArmorStandによる見た目変更なし。
     */
    public abstract ItemStack getDisplayHeadItem();

    /**
     * ブロック設置時の追加初期化処理。
     * TileState PDCへのカスタムデータ書き込み後に呼ばれる。
     */
    public void onBlockPlaced(Player player, Block block, TileState tileState) {}

    /**
     * ブロック破壊時の追加処理。
     * ドロップ前に呼ばれる。
     */
    public void onBlockBroken(Player player, Block block, TileState tileState) {}

    /**
     * プレイヤーがカスタムブロックを右クリックした時の処理。
     */
    public void onBlockInteract(Player player, Block block, TileState tileState) {}

    @Override
    public Material getBaseMaterial() {
        return getBlockMaterial();
    }

    @Override
    public void onPlace(BlockPlaceEvent event) {
        // BaseCustomItemのonPlaceをオーバーライド
        // 実際のPDC転送はCustomBlockListenerで行う
    }

    /**
     * TileStateにカスタムブロックとしてのデータを書き込む。
     */
    public void writeToTileState(TileState tileState, ItemStack sourceItem) {
        PersistentDataContainer dst = tileState.getPersistentDataContainer();
        dst.set(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING, getItemId());

        // ItemStackからカスタムデータを転送
        if (sourceItem.hasItemMeta()) {
            PersistentDataContainer src = sourceItem.getItemMeta().getPersistentDataContainer();
            String customData = src.get(ItemKeys.CUSTOM_DATA, PersistentDataType.STRING);
            if (customData != null) {
                dst.set(BlockKeys.CUSTOM_DATA, PersistentDataType.STRING, customData);
            }
        }

        tileState.update();
    }

    /**
     * TileStateからドロップアイテムにデータを復元する。
     */
    public ItemStack createDropWithData(TileState tileState) {
        ItemStack drop = createItemStack();
        PersistentDataContainer src = tileState.getPersistentDataContainer();
        String customData = src.get(BlockKeys.CUSTOM_DATA, PersistentDataType.STRING);

        if (customData != null) {
            drop.editMeta(meta ->
                meta.getPersistentDataContainer().set(
                    ItemKeys.CUSTOM_DATA, PersistentDataType.STRING, customData
                )
            );
        }
        return drop;
    }
}
