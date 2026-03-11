package com.arspaper.block.impl;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        return Material.BARREL;
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

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        // 初期Source量を0に設定
        tileState.getPersistentDataContainer().set(
            BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0
        );
        tileState.update();
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        int source = getSourceAmount(tileState);
        player.sendMessage(Component.text(
            "ソース: " + source + " / " + MAX_SOURCE, NamedTextColor.AQUA
        ));
    }

    /**
     * TileStateからSource量を取得。
     */
    public static int getSourceAmount(TileState tileState) {
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
     * Sourceを消費する。
     *
     * @return 消費に成功したかどうか
     */
    public static boolean consumeSource(TileState tileState, int amount) {
        int current = getSourceAmount(tileState);
        if (current < amount) return false;
        setSourceAmount(tileState, current - amount);
        return true;
    }
}
