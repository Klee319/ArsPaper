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
 * Creative Source Jar - 無限にSourceを供給するクリエイティブ専用ブロック。
 * SourceJarと同じcustom_block_id="source_jar"を使用し、
 * source_infinite=1フラグで無限を識別する。
 */
public class CreativeSourceJar extends CustomBlock {

    public CreativeSourceJar(JavaPlugin plugin) {
        super(plugin, "creative_source_jar");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.DECORATED_POT;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("クリエイティブソースジャー", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200003;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("無限のソースエネルギーを供給", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("ソース: \u221E (無限)", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.PURPLE_STAINED_GLASS);
        head.editMeta(meta -> meta.setCustomModelData(200003));
        return head;
    }

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        // 無限フラグをセット
        tileState.getPersistentDataContainer().set(
            BlockKeys.SOURCE_INFINITE, PersistentDataType.BYTE, (byte) 1
        );
        // source_jarとして認識させるためcustom_block_idを上書き（Sourceシステム互換）
        // ※ creative_source_jar IDはアイテムドロップ生成用に別途保持
        tileState.getPersistentDataContainer().set(
            BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING, "source_jar"
        );
        // creative_source_jarのIDも保存（破壊時にCreativeSourceJarとしてドロップさせるため）
        tileState.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey("arspaper", "original_block_id"),
            PersistentDataType.STRING, "creative_source_jar"
        );
        tileState.update();
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        player.sendMessage(Component.text(
            "ソース: \u221E (無限)", NamedTextColor.LIGHT_PURPLE
        ));
    }
}
