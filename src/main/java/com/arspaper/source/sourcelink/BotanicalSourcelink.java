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
 * Botanical Sourcelink - 植物の成長からSourceを生成。
 * バニラのCOMPOSTER（コンポスター）をベースに使用。
 *
 * 半径10ブロック以内で作物・植物が成長するとソースポイントをバッファに蓄積。
 * 定期的にバッファから排出して隣接Source Jarに供給する。
 * 燃料投入は不要（完全イベント駆動型）。
 */
public class BotanicalSourcelink extends Sourcelink {

    /** 植物成長1回あたりのソースポイント */
    public static final int SOURCE_PER_GROWTH = 5;
    /** 成長検知範囲（ブロック） */
    public static final int DETECTION_RADIUS = 10;

    public BotanicalSourcelink(JavaPlugin plugin) {
        super(plugin, "botanical_sourcelink");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.BEEHIVE;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("ボタニカルソースリンク", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 200007;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("植物の成長からソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("半径10ブロック以内の作物成長で蓄積", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.OAK_LEAVES);
        head.editMeta(meta -> meta.setCustomModelData(200007));
        return head;
    }

    @Override
    public int generateSource(Block block) {
        return drainBuffer(block);
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        int buffer = getBuffer(tileState);
        player.sendMessage(Component.text(
            "ボタニカルソースリンク - 蓄積ソース: " + buffer, NamedTextColor.GREEN
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
