package com.arspaper.item;

import com.arspaper.ArsPaper;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
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

    /** エンチャントオーラを表示するかどうか（防具はfalseにオーバーライド） */
    public boolean hasEnchantGlow() { return true; }

    /** アイテムスタックを新規生成 */
    public ItemStack createItemStack() {
        ItemStack item = new ItemStack(getBaseMaterial());
        item.editMeta(meta -> {
            meta.displayName(getDisplayName());
            // Geyser互換: itemName も設定（Bedrockでベース素材名が表示される問題の対策）
            meta.itemName(getDisplayName());
            // Geyser互換: CustomModelDataを無効化してアイテム透明化を防止
            if (!isCustomModelDataDisabled()) {
                meta.setCustomModelData(getCustomModelData());
            }
            meta.getPersistentDataContainer().set(
                ItemKeys.CUSTOM_ITEM_ID,
                PersistentDataType.STRING,
                itemId
            );
            // エンチャントオーラ（防具以外のカスタムアイテムに光沢を付与）
            if (hasEnchantGlow()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            // 鍛冶型等のバニラデフォルトテキストを非表示
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return item;
    }

    /** config.ymlのgeyser.disable-custom-model-data設定を参照 */
    private static boolean isCustomModelDataDisabled() {
        ArsPaper instance = ArsPaper.getInstance();
        if (instance == null) return false;
        return instance.getConfig().getBoolean("geyser.disable-custom-model-data", false);
    }

    /** 右クリック時の処理 */
    public void onRightClick(PlayerInteractEvent event) {}

    /** 左クリック時の処理 */
    public void onLeftClick(PlayerInteractEvent event) {}

    /** ブロック設置時の処理。デフォルトは設置キャンセル（CustomBlockが必要に応じてオーバーライド） */
    public void onPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }
}
