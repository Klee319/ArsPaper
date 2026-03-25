package com.arspaper.source.sourcelink;

import com.arspaper.block.BlockKeys;
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
 * Vitalic Sourcelink - 生命の力でSourceを生成。
 * バニラのBARREL（樽）をベースに使用。
 *
 * 基本の定期生成に加え、近くでmobが死亡するとボーナスSourceを蓄積。
 * 蓄積分はティック時に隣接Source Jarへ供給される。
 */
public class VitalicSourcelink extends Sourcelink {

    private static final int SOURCE_PER_TICK = 20;
    /** mob死亡時のボーナスSource */
    public static final int SOURCE_PER_KILL = 50;
    /** mob死亡検知範囲（ブロック） */
    public static final int DETECTION_RADIUS = 10;

    public VitalicSourcelink(JavaPlugin plugin) {
        super(plugin, "vitalic_sourcelink");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.BARREL;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("バイタリックソースリンク", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200006;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("生命の力でソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("近くでmobが倒されるとボーナス生成", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.BONE);
        head.editMeta(meta -> meta.setCustomModelData(200006));
        return head;
    }

    @Override
    public int generateSource() {
        return SOURCE_PER_TICK;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        int source = tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);
        player.sendMessage(Component.text(
            "バイタリックソースリンク - 蓄積ソース: " + source, NamedTextColor.GREEN
        ));
    }

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        tileState.getPersistentDataContainer().set(
            BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0
        );
        tileState.update();
    }
}
