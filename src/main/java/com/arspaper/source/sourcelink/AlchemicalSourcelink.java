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
 * Alchemical Sourcelink - 醸造の力でSourceを生成。
 * バニラのBLAST_FURNACE（溶鉱炉）をベースに使用。
 *
 * 定期的にSourceを生成し、隣接するSource Jarに自動供給。
 */
public class AlchemicalSourcelink extends Sourcelink {

    private static final int SOURCE_PER_TICK = 40;

    public AlchemicalSourcelink(JavaPlugin plugin) {
        super(plugin, "alchemical_sourcelink");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.BLAST_FURNACE;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("アルケミカルソースリンク", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200005;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("醸造の力でソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("隣接するソースジャーに自動供給", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.DRAGON_BREATH);
        head.editMeta(meta -> meta.setCustomModelData(200005));
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
            "アルケミカルソースリンク - 蓄積ソース: " + source, NamedTextColor.DARK_PURPLE
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
