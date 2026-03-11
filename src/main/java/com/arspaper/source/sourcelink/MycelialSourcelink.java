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
 * Mycelial Sourcelink - 食料アイテムを消費してSourceを生成。
 * バニラのSMOKER（燻製器）をベースに使用。
 *
 * 栄養価の高い食料ほど多くのSourceを生成。
 */
public class MycelialSourcelink extends Sourcelink {

    private static final int SOURCE_PER_FOOD = 30;

    public MycelialSourcelink(JavaPlugin plugin) {
        super(plugin, "mycelial_sourcelink");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.SMOKER;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("マイセリアルソースリンク", NamedTextColor.DARK_GREEN)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200004;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("食料を消費してソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("栄養価が高いほどソース生成量が増加", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.BROWN_MUSHROOM_BLOCK);
        head.editMeta(meta -> meta.setCustomModelData(200004));
        return head;
    }

    @Override
    public int generateSource() {
        return SOURCE_PER_FOOD;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        int source = tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);
        player.sendMessage(Component.text(
            "マイセリアルソースリンク - 蓄積ソース: " + source, NamedTextColor.DARK_GREEN
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
