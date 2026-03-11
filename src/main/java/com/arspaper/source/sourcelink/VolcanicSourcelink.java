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
 * Volcanic Sourcelink - 燃焼可能アイテムを消費してSourceを生成。
 * バニラのFURNACE（かまど）をベースに使用。
 *
 * 内部にアイテムを格納し、定期的に消費してSourceを生成する。
 * 隣接するSource Jarに自動供給。
 */
public class VolcanicSourcelink extends Sourcelink {

    private static final int SOURCE_PER_BURN = 50;

    public VolcanicSourcelink(JavaPlugin plugin) {
        super(plugin, "volcanic_sourcelink");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.FURNACE;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("ヴォルカニックソースリンク", NamedTextColor.RED)
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
                Component.text("燃料を燃やしてソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("近くに燃料アイテムを設置", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.MAGMA_BLOCK);
        head.editMeta(meta -> meta.setCustomModelData(200003));
        return head;
    }

    @Override
    public int generateSource() {
        return SOURCE_PER_BURN;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        int source = tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);
        player.sendMessage(Component.text(
            "ヴォルカニックソースリンク - 蓄積ソース: " + source, NamedTextColor.RED
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
