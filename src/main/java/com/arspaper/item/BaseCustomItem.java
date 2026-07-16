package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.integration.TrinityForgeBridge;
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

    /**
     * 厳選の既定品質(0-5)。サブクラスやドロップ経路で上書きして固定品質を指定できる。
     * {@link #usesQualityRoll()} が false の場合はこの値がそのまま書き込まれる。
     */
    protected int defaultQuality() { return 0; }

    /**
     * 品質を selection.yml の分布から抽選するか。
     * false（既定）なら {@link #defaultQuality()} を使う。
     * サブクラスやドロップ経路で true にすると分布抽選が有効になる。
     */
    protected boolean usesQualityRoll() { return false; }

    /** 生成時の品質を決定する（抽選 or 既定値）。常に 0-5 にクランプして返す。 */
    protected int rollQuality() {
        if (usesQualityRoll()) {
            return SelectionConfig.get().rollQuality(defaultQuality());
        }
        return SelectionConfig.clamp(defaultQuality());
    }

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
            // 厳選: usesQualityRoll()==true の装備・完成品系のみ、生成毎にユニークな rollSeed と
            // 品質(0-5)を TrinityForge ItemData(PDC) へ追記する。
            // ステ値はベイクせず、TrinityForge 側が rollSeed + quality + テーブルから live 導出する。
            // 素材系サブクラス（消耗品・中間素材等）はここをスキップし、PDC差によるスタック不能化を防ぐ。
            // TrinityForge 未ロード時は no-op（既存 PDC は壊さない）。
            if (usesQualityRoll()) {
                long rollSeed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
                TrinityForgeBridge.writeItemRoll(meta, rollSeed, rollQuality());
            }
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
