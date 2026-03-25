package com.arspaper.block.impl;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import com.arspaper.gui.ScribingTableGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Scribing Table - グリフのアンロックを行うワークステーション。
 * バニラのLECTERN（書見台）をベースに使用。
 *
 * 右クリックでグリフアンロックGUIを開く。
 */
public class ScribingTable extends CustomBlock {

    public ScribingTable(JavaPlugin plugin) {
        super(plugin, "scribing_table");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.LECTERN;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("筆記台", NamedTextColor.DARK_AQUA)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200001;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("設置して右クリックでグリフを解放", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        // カスタムモデル付きのアイテムを頭装備として使用
        ItemStack head = new ItemStack(Material.PAPER);
        head.editMeta(meta -> meta.setCustomModelData(200001));
        return head;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        // アニメーション進行中は筆記台を開けない（マナ増殖防止）
        if (com.arspaper.gui.GlyphUnlockAnimation.isAnimating(player.getUniqueId())) {
            player.sendMessage(Component.text(
                "グリフ解放中です！完了までお待ちください。", NamedTextColor.YELLOW));
            return;
        }
        // グリフアンロックGUIを開く
        ScribingTableGui gui = new ScribingTableGui(
            ArsPaper.getInstance(),
            player,
            block.getLocation()
        );
        gui.open();
    }
}
