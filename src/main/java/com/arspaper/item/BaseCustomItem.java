package com.arspaper.item;

import com.arspaper.ArsPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

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
     * 既定のlore(ハードコード)。表示名と同様、サブクラスがオーバーライドする。
     * 既定はnull(lore無し)。functional-items.ymlの上書きが無い場合のフォールバック値として使う。
     */
    protected List<Component> getDefaultLore() { return null; }

    /**
     * 品質(rollSeed + quality)を刻印すべき「完成品(装備/触媒)」か。既定 false。
     * 儀式クラフト経路({@code RitualManager})が true のものだけ、生成者のArs鍛冶スキルで品質を刻印する
     * (craft-quality一本化)。素材・消耗品は false のまま — 一意な rollSeed 刻印はスタック不能化を招くため。
     * バニラ卓クラフトは TrinityForge の CraftQualityListener が別途 MaterialTier で判定し刻印する。
     */
    public boolean isQualityStamped() { return false; }

    /** アイテムスタックを新規生成 */
    public ItemStack createItemStack() {
        ItemStack item = new ItemStack(resolveMaterial());
        Component resolvedDisplayName = resolveDisplayName();
        List<Component> resolvedLore = resolveLore();
        boolean glow = resolveEnchantGlow();
        item.editMeta(meta -> {
            meta.displayName(resolvedDisplayName);
            // Geyser互換: itemName も設定（Bedrockでベース素材名が表示される問題の対策）
            meta.itemName(resolvedDisplayName);
            // CustomModelDataは常時付与する(2026-07-27: config経由での無効化を撤去。
            // 無効化するとリソースパックのモデルが一切出なくなるため、逃げ道自体を無くした)。
            meta.setCustomModelData(getCustomModelData());
            meta.getPersistentDataContainer().set(
                ItemKeys.CUSTOM_ITEM_ID,
                PersistentDataType.STRING,
                itemId
            );
            if (resolvedLore != null) {
                meta.lore(resolvedLore);
            }
            // 品質(rollSeed + quality)はここでは刻印しない。バニラ卓クラフトは TrinityForge の
            // CraftQualityListener がスキル駆動で刻印し、儀式クラフトは RitualManager が
            // TrinityForgeBridge.stampCraftedQuality で刻印する(craft-quality一本化)。コマンド付与等の
            // 非クラフト生成は品質0のまま(生成者スキルが無いため baseline)。
            // エンチャントオーラ（防具以外のカスタムアイテムに光沢を付与）
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            // 鍛冶型等のバニラデフォルトテキストを非表示
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return item;
    }

    /**
     * functional-items.yml の表示名上書きを適用したComponentを返す。
     * 上書きが未設定、またはgetDisplayName()がプレーンなTextComponentでない場合は
     * ハードコードされた表示名（色/装飾込み）をそのまま返す。
     * Material/CustomModelData/内部ID(itemId)は一切変更しない。
     */
    public Component resolveDisplayName() {
        Component hardcoded = getDisplayName();
        ArsPaper instance = ArsPaper.getInstance();
        FunctionalItemConfig config = instance != null ? instance.getFunctionalItemConfig() : null;
        String override = config != null ? config.displayNameOverride(itemId) : null;

        if (override != null && !override.isBlank()) {
            // 上書きに色記法(MiniMessage / レガシー &)が書かれていたら、その解釈は
            // DisplayText 1本へ寄せる。素通しすると "<gold>" や "&6" が名前に出る。
            if (com.arspaper.util.DisplayText.hasMarkup(override)) {
                return com.arspaper.util.DisplayText.component(override);
            }
            if (hardcoded instanceof TextComponent text) {
                // 色指定が無い上書きはハードコード側の色/装飾を維持する(従来挙動)。
                return text.content(override);
            }
        }
        return hardcoded;
    }

    /**
     * functional-items.yml のlore上書きを適用したComponentリストを返す。
     * 上書きが未設定の場合は{@link #getDefaultLore()}(ハードコード)をそのまま返す(null=lore無し)。
     * 上書き文字列はcatalog.ymlと同じMiniMessage記法で解釈する。
     */
    public List<Component> resolveLore() {
        ArsPaper instance = ArsPaper.getInstance();
        FunctionalItemConfig config = instance != null ? instance.getFunctionalItemConfig() : null;
        List<String> override = config != null ? config.loreOverride(itemId) : null;

        if (override != null) {
            return override.stream()
                .map(com.arspaper.util.DisplayText::component)
                .collect(Collectors.toList());
        }
        return getDefaultLore();
    }

    /**
     * functional-items.yml のエンチャント光上書きを適用した値を返す。
     * 上書きが未設定の場合は{@link #hasEnchantGlow()}(ハードコード既定値)を返す。
     */
    public boolean resolveEnchantGlow() {
        ArsPaper instance = ArsPaper.getInstance();
        FunctionalItemConfig config = instance != null ? instance.getFunctionalItemConfig() : null;
        Boolean override = config != null ? config.enchantGlowOverride(itemId) : null;
        return override != null ? override : hasEnchantGlow();
    }

    /**
     * functional-items.yml の材質上書きを適用した値を返す。
     * 上書きが未設定(またはブロック系等、上書き非対応のアイテム)の場合は
     * {@link #getBaseMaterial()}(ハードコード既定値)を返す。
     */
    public Material resolveMaterial() {
        ArsPaper instance = ArsPaper.getInstance();
        FunctionalItemConfig config = instance != null ? instance.getFunctionalItemConfig() : null;
        Material override = config != null ? config.materialOverride(itemId) : null;
        return override != null ? override : getBaseMaterial();
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
