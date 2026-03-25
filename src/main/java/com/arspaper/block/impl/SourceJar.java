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
 * バニラのBARREL（樽）をベースに使用。
 * 最大10,000 Sourceを貯蔵可能。
 *
 * 右クリックで貯蔵量を確認。
 */
public class SourceJar extends CustomBlock {

    public static final int MAX_SOURCE = 10000;

    public SourceJar(JavaPlugin plugin) {
        super(plugin, "source_jar");
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

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("魔法のソースエネルギーを貯蔵", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("ソース: 0 / " + MAX_SOURCE, NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
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

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        // 設置に使ったアイテムからSource量を復元
        // BlockPlaceEvent時点ではアイテムが既に消費されている場合があるため、
        // 両手をチェックし、ITEM_SOURCE_KEYを持つアイテムを探す
        int restoredSource = 0;
        for (ItemStack hand : new ItemStack[]{
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand()}) {
            if (hand != null && hand.hasItemMeta()) {
                Integer stored = hand.getItemMeta().getPersistentDataContainer()
                    .get(ITEM_SOURCE_KEY, PersistentDataType.INTEGER);
                if (stored != null && stored > 0) {
                    restoredSource = stored;
                    break;
                }
            }
        }
        tileState.getPersistentDataContainer().set(
            BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, restoredSource
        );
        tileState.update();
    }

    @Override
    public ItemStack createDropWithData(TileState tileState) {
        ItemStack drop = super.createDropWithData(tileState);
        int source = getSourceAmount(tileState);
        drop.editMeta(meta -> {
            // Source量をアイテムPDCに保存
            meta.getPersistentDataContainer().set(
                ITEM_SOURCE_KEY, PersistentDataType.INTEGER, source
            );
            // Lore に現在のSource量を反映
            meta.lore(List.of(
                Component.text("魔法のソースエネルギーを貯蔵", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("ソース: " + source + " / " + MAX_SOURCE, NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        });
        return drop;
    }

    /** ソースベリー1個あたりのSource追加量 */
    public static final int SOURCE_PER_BERRY = 100;

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        // ソースベリーを持っている場合: Source追加
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.hasItemMeta()) {
            String customId = hand.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if ("source_berry".equals(customId) && !isInfinite(tileState)) {
                int currentSource = getSourceAmount(tileState);
                if (currentSource >= MAX_SOURCE) {
                    player.sendMessage(Component.text("ソースジャーは満タンです", NamedTextColor.YELLOW));
                    return;
                }

                // ベリー消費 + Source追加
                hand.setAmount(hand.getAmount() - 1);
                int added = setSourceAmount(tileState, currentSource + SOURCE_PER_BERRY) - currentSource;
                player.sendMessage(Component.text(
                    "ソースを" + added + "追加しました (" + getSourceAmount(tileState) + "/" + MAX_SOURCE + ")",
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
                "ソース: \u221E (無限)", NamedTextColor.LIGHT_PURPLE
            ));
        } else {
            int source = getSourceAmount(tileState);
            player.sendMessage(Component.text(
                "ソース: " + source + " / " + MAX_SOURCE, NamedTextColor.AQUA
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
     * TileStateからSource量を取得。無限の場合は常にMAX_SOURCE。
     */
    public static int getSourceAmount(TileState tileState) {
        if (isInfinite(tileState)) return MAX_SOURCE;
        return tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);
    }

    /**
     * TileStateにSource量を設定。
     *
     * @return 実際に設定された量
     */
    public static int setSourceAmount(TileState tileState, int amount) {
        int clamped = Math.clamp(amount, 0, MAX_SOURCE);
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
        int added = Math.min(amount, MAX_SOURCE - current);
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
