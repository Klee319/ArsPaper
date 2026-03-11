package com.arspaper.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 全カスタムアイテムの抽象基底クラス。
 * PDCでアイテムを識別し、イベントハンドラを提供する。
 */
public abstract class BaseCustomItem {

    protected final JavaPlugin plugin;
    protected final String itemId;
    protected final NamespacedKey namespacedId;

    protected BaseCustomItem(JavaPlugin plugin, String itemId) {
        this.plugin = plugin;
        this.itemId = itemId;
        this.namespacedId = new NamespacedKey(plugin, itemId);
    }

    /** カスタムアイテムID (例: "spell_book_novice") */
    public String getItemId() {
        return itemId;
    }

    public NamespacedKey getNamespacedId() {
        return namespacedId;
    }

    /** ベースとなるバニラマテリアル */
    public abstract Material getBaseMaterial();

    /** 表示名 */
    public abstract Component getDisplayName();

    /** CustomModelData値（リソースパック連携用） */
    public abstract int getCustomModelData();

    /** アイテムスタックを新規生成 */
    public ItemStack createItemStack() {
        ItemStack item = new ItemStack(getBaseMaterial());
        item.editMeta(meta -> {
            meta.displayName(getDisplayName());
            meta.setCustomModelData(getCustomModelData());
            meta.getPersistentDataContainer().set(
                ItemKeys.CUSTOM_ITEM_ID,
                PersistentDataType.STRING,
                itemId
            );
        });
        return item;
    }

    /** 右クリック時の処理 */
    public void onRightClick(PlayerInteractEvent event) {}

    /** 左クリック時の処理 */
    public void onLeftClick(PlayerInteractEvent event) {}

    /** ブロック設置時の処理 */
    public void onPlace(BlockPlaceEvent event) {}
}
