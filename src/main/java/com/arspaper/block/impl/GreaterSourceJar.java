package com.arspaper.block.impl;

import com.arspaper.block.BlockKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Greater Source Jar - 容量1,000,000のソース瓶（極大tier）。
 * SourceJarのサブクラスとして実装し、custom_block_idは"source_jar"に統一する。
 */
public class GreaterSourceJar extends SourceJar {

    public static final int CAPACITY = 1_000_000;

    public GreaterSourceJar(JavaPlugin plugin) {
        super(plugin, "greater_source_jar");
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    @Override
    protected NamedTextColor getStorageColor() {
        return NamedTextColor.LIGHT_PURPLE;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("極大ソースジャー", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200005;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.MAGENTA_STAINED_GLASS);
        head.editMeta(meta -> meta.setCustomModelData(200005));
        return head;
    }

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        super.onBlockPlaced(player, block, tileState);
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING, "source_jar");
        pdc.set(
            new org.bukkit.NamespacedKey("arspaper", "original_block_id"),
            PersistentDataType.STRING, "greater_source_jar"
        );
        Integer storedCap = pdc.get(BlockKeys.SOURCE_CAPACITY, PersistentDataType.INTEGER);
        if (storedCap == null || storedCap < CAPACITY) {
            pdc.set(BlockKeys.SOURCE_CAPACITY, PersistentDataType.INTEGER, CAPACITY);
        }
        tileState.update();
    }
}
