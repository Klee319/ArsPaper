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
 * Large Source Jar - 容量100,000のソース瓶。
 * SourceJarのサブクラスとして実装し、custom_block_idは"source_jar"に統一する
 * （Sourceネットワーク/Sourcelink/儀式すべて従来コードが動作する）。
 * tier識別は source_capacity PDC で行う。
 */
public class LargeSourceJar extends SourceJar {

    public static final int CAPACITY = 100_000;

    public LargeSourceJar(JavaPlugin plugin) {
        super(plugin, "large_source_jar");
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    @Override
    protected NamedTextColor getStorageColor() {
        return NamedTextColor.DARK_AQUA;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("大型ソースジャー", NamedTextColor.DARK_AQUA)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200004;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.CYAN_STAINED_GLASS);
        head.editMeta(meta -> meta.setCustomModelData(200004));
        return head;
    }

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        super.onBlockPlaced(player, block, tileState);
        // source_jarネットワークと互換にするためCUSTOM_BLOCK_IDを"source_jar"に統一
        // 元のtier識別はoriginal_block_idで保持（ドロップ時に正しいクラスへ復帰）
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING, "source_jar");
        pdc.set(
            new org.bukkit.NamespacedKey("arspaper", "original_block_id"),
            PersistentDataType.STRING, "large_source_jar"
        );
        // 容量を未設定状態（旧Jar）から復元したとき、サブクラス自身の容量に揃え直す
        Integer storedCap = pdc.get(BlockKeys.SOURCE_CAPACITY, PersistentDataType.INTEGER);
        if (storedCap == null || storedCap < CAPACITY) {
            pdc.set(BlockKeys.SOURCE_CAPACITY, PersistentDataType.INTEGER, CAPACITY);
        }
        tileState.update();
    }
}
