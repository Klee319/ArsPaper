package com.arspaper.block.impl;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import com.arspaper.item.ItemKeys;
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

/**
 * Source Jar - Sourceを貯蔵するブロック。
 * バニラのDECORATED_POTをベースに使用。
 * 標準容量: 10,000 (LargeSourceJarで100,000、GreaterSourceJarで1,000,000まで拡張可能)。
 *
 * 右クリックで貯蔵量を確認。
 */
public class SourceJar extends CustomBlock {

    /** 標準ソース瓶の容量（後方互換のため定数として維持）。 */
    public static final int MAX_SOURCE = 10_000;

    public SourceJar(JavaPlugin plugin) {
        super(plugin, "source_jar");
    }

    /** サブクラスからカスタムIDを渡すためのコンストラクタ。 */
    protected SourceJar(JavaPlugin plugin, String id) {
        super(plugin, id);
    }

    @Override
    public Material getBlockMaterial() {
        return Material.DECORATED_POT;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("ソースジャー", NamedTextColor.BLUE)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200002;
    }

    /** このブロック種別の最大容量。サブクラスでオーバーライドして拡張tierを実装する。 */
    public int getCapacity() {
        return MAX_SOURCE;
    }

    /** lore表示用の色。サブクラスで上書き可能。 */
    protected NamedTextColor getStorageColor() {
        return NamedTextColor.AQUA;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                ITEM_CAPACITY_KEY, PersistentDataType.INTEGER, getCapacity()
            );
            meta.lore(List.of(
                Component.text("魔法のソースエネルギーを貯蔵", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("ソース: 0 / " + getCapacity(), getStorageColor())
                    .decoration(TextDecoration.ITALIC, false)
            ));
        });
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.BLUE_STAINED_GLASS);
        head.editMeta(meta -> meta.setCustomModelData(200002));
        return head;
    }

    /** Source量をItemStackのPDCに保存するキー */
    private static final NamespacedKey ITEM_SOURCE_KEY = new NamespacedKey("arspaper", "stored_source");
    /** 容量をItemStackのPDCに保存するキー（tier識別用） */
    private static final NamespacedKey ITEM_CAPACITY_KEY = new NamespacedKey("arspaper", "stored_capacity");

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        // 設置に使ったアイテムからSource量と容量を復元
        int restoredSource = 0;
        int restoredCapacity = getCapacity();
        for (ItemStack hand : new ItemStack[]{
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand()}) {
            if (hand != null && hand.hasItemMeta()) {
                PersistentDataContainer itemPdc = hand.getItemMeta().getPersistentDataContainer();
                Integer storedCap = itemPdc.get(ITEM_CAPACITY_KEY, PersistentDataType.INTEGER);
                Integer stored = itemPdc.get(ITEM_SOURCE_KEY, PersistentDataType.INTEGER);
                if (storedCap != null || stored != null) {
                    if (storedCap != null) restoredCapacity = storedCap;
                    if (stored != null) restoredSource = stored;
                    break;
                }
            }
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, restoredSource);
        pdc.set(BlockKeys.SOURCE_CAPACITY, PersistentDataType.INTEGER, restoredCapacity);
        tileState.update();
    }

    @Override
    public ItemStack createDropWithData(TileState tileState) {
        ItemStack drop = super.createDropWithData(tileState);
        int source = getSourceAmount(tileState);
        int capacity = getCapacity(tileState);
        NamedTextColor color = getStorageColor();
        drop.editMeta(meta -> {
            PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
            itemPdc.set(ITEM_SOURCE_KEY, PersistentDataType.INTEGER, source);
            itemPdc.set(ITEM_CAPACITY_KEY, PersistentDataType.INTEGER, capacity);
            meta.lore(List.of(
                Component.text("魔法のソースエネルギーを貯蔵", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("ソース: " + source + " / " + capacity, color)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        });
        return drop;
    }

    /** ソースベリー1個あたりのSource追加量 */
    public static final int SOURCE_PER_BERRY = 100;

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        int capacity = getCapacity(tileState);

        // ソースベリーを持っている場合: Source追加
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.hasItemMeta()) {
            String customId = hand.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if ("source_berry".equals(customId) && !isInfinite(tileState)) {
                int currentSource = getSourceAmount(tileState);
                if (currentSource >= capacity) {
                    player.sendMessage(Component.text("ソースジャーは満タンです", NamedTextColor.YELLOW));
                    return;
                }

                // ベリー消費 + Source追加
                hand.setAmount(hand.getAmount() - 1);
                int added = addSource(tileState, SOURCE_PER_BERRY);
                player.sendMessage(Component.text(
                    "ソースを" + added + "追加しました (" + getSourceAmount(tileState) + "/" + capacity + ")",
                    NamedTextColor.AQUA));
                player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_BREWING_STAND_BREW,
                    org.bukkit.SoundCategory.BLOCKS, 0.5f, 1.5f);
                player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                    block.getLocation().add(0.5, 1.0, 0.5), 8, 0.2, 0.3, 0.2, 0.03);
                return;
            }
        }

        // 通常の右クリック: 貯蔵量表示
        if (isInfinite(tileState)) {
            player.sendMessage(Component.text(
                "ソース: ∞ (無限)", NamedTextColor.LIGHT_PURPLE
            ));
        } else {
            int source = getSourceAmount(tileState);
            player.sendMessage(Component.text(
                "ソース: " + source + " / " + capacity, NamedTextColor.AQUA
            ));
        }
    }

    /**
     * TileStateが無限ソースかどうかを判定する。
     */
    public static boolean isInfinite(TileState tileState) {
        return tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_INFINITE, PersistentDataType.BYTE, (byte) 0) != 0;
    }

    /**
     * TileStateの最大容量を取得する。未設定なら標準値（MAX_SOURCE）。
     * Tier情報を読み出すための統一API。
     */
    public static int getCapacity(TileState tileState) {
        return tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_CAPACITY, PersistentDataType.INTEGER, MAX_SOURCE);
    }

    /**
     * TileStateからSource量を取得。無限の場合は常に容量上限。
     */
    public static int getSourceAmount(TileState tileState) {
        if (isInfinite(tileState)) return getCapacity(tileState);
        return tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);
    }

    /**
     * TileStateにSource量を設定。
     *
     * @return 実際に設定された量
     */
    public static int setSourceAmount(TileState tileState, int amount) {
        int capacity = getCapacity(tileState);
        int clamped = Math.clamp(amount, 0, capacity);
        tileState.getPersistentDataContainer().set(
            BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, clamped
        );
        tileState.update();
        return clamped;
    }

    /**
     * Sourceを追加する。
     *
     * @return 実際に追加された量（溢れ分は返さない）
     */
    public static int addSource(TileState tileState, int amount) {
        int current = getSourceAmount(tileState);
        int capacity = getCapacity(tileState);
        int added = Math.min(amount, capacity - current);
        if (added > 0) {
            setSourceAmount(tileState, current + added);
        }
        return added;
    }

    /**
     * Sourceを消費する。無限の場合は減らさずに成功を返す。
     *
     * @return 消費に成功したかどうか
     */
    public static boolean consumeSource(TileState tileState, int amount) {
        if (isInfinite(tileState)) return true;
        int current = getSourceAmount(tileState);
        if (current < amount) return false;
        setSourceAmount(tileState, current - amount);
        return true;
    }
}
