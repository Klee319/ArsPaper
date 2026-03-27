package com.arspaper.source.sourcelink;

import com.arspaper.block.BlockKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Volcanic Sourcelink - 燃焼可能アイテムを消費してSourceを生成。
 * バニラのFURNACE（かまど）をベースに使用。
 *
 * 右クリックで燃料を投入し、バニラの燃焼時間に基づいたソースポイントを蓄積。
 * 定期的にバッファから排出して隣接Source Jarに供給する。
 */
public class VolcanicSourcelink extends Sourcelink {

    /**
     * 燃料アイテム → ソースポイント（バニラ燃焼時間に比例、10〜50範囲）
     * stick(100tick)=10, coal(1600tick)=30, lava_bucket(20000tick)=50
     */
    private static final Map<Material, Integer> FUEL_VALUES = Map.ofEntries(
        // 低価値（10）: 短い燃焼時間
        Map.entry(Material.STICK, 10),
        Map.entry(Material.BAMBOO, 10),
        Map.entry(Material.WHITE_CARPET, 10),
        // やや低（15）: 木材系ハーフブロック
        Map.entry(Material.OAK_SLAB, 15),
        Map.entry(Material.SPRUCE_SLAB, 15),
        Map.entry(Material.BIRCH_SLAB, 15),
        Map.entry(Material.JUNGLE_SLAB, 15),
        Map.entry(Material.ACACIA_SLAB, 15),
        Map.entry(Material.DARK_OAK_SLAB, 15),
        Map.entry(Material.MANGROVE_SLAB, 15),
        Map.entry(Material.CHERRY_SLAB, 15),
        Map.entry(Material.BAMBOO_SLAB, 15),
        // 中価値（20）: 板材・原木
        Map.entry(Material.OAK_PLANKS, 20),
        Map.entry(Material.SPRUCE_PLANKS, 20),
        Map.entry(Material.BIRCH_PLANKS, 20),
        Map.entry(Material.JUNGLE_PLANKS, 20),
        Map.entry(Material.ACACIA_PLANKS, 20),
        Map.entry(Material.DARK_OAK_PLANKS, 20),
        Map.entry(Material.MANGROVE_PLANKS, 20),
        Map.entry(Material.CHERRY_PLANKS, 20),
        Map.entry(Material.BAMBOO_PLANKS, 20),
        Map.entry(Material.OAK_LOG, 20),
        Map.entry(Material.SPRUCE_LOG, 20),
        Map.entry(Material.BIRCH_LOG, 20),
        Map.entry(Material.JUNGLE_LOG, 20),
        Map.entry(Material.ACACIA_LOG, 20),
        Map.entry(Material.DARK_OAK_LOG, 20),
        Map.entry(Material.MANGROVE_LOG, 20),
        Map.entry(Material.CHERRY_LOG, 20),
        Map.entry(Material.CHARCOAL, 25),
        // 高価値（30）: 石炭
        Map.entry(Material.COAL, 30),
        Map.entry(Material.DRIED_KELP_BLOCK, 30),
        // 非常に高い（40）: ブレイズロッド・石炭ブロック
        Map.entry(Material.BLAZE_ROD, 40),
        Map.entry(Material.COAL_BLOCK, 40),
        // 最高（50）: 溶岩バケツ
        Map.entry(Material.LAVA_BUCKET, 50)
    );

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
                Component.text("燃料を手に持って右クリックで投入", NamedTextColor.DARK_GRAY)
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
    public int generateSource(Block block) {
        return drainBuffer(block);
    }

    @Override
    public int getSourceValueForItem(org.bukkit.inventory.ItemStack item) {
        if (item == null) return 0;
        Integer value = FUEL_VALUES.get(item.getType());
        return value != null ? value : 0;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Integer sourceValue = FUEL_VALUES.get(hand.getType());

        if (sourceValue != null) {
            int addCount = player.isSneaking() ? hand.getAmount() : 1;
            addCount = Math.min(addCount, hand.getAmount());
            int totalAdded = sourceValue * addCount;

            addToBuffer(block, totalAdded);
            hand.setAmount(hand.getAmount() - addCount);

            // 溶岩バケツは空バケツを返す
            if (hand.getType() == Material.LAVA_BUCKET && addCount == 1) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            }

            int buffer = getBuffer((TileState) block.getState());
            player.sendMessage(Component.text(
                "燃料を" + addCount + "個投入 [+" + totalAdded + " Source] (蓄積: " + buffer + ")",
                NamedTextColor.RED
            ));
            // 投入エフェクト
            block.getWorld().spawnParticle(Particle.FLAME,
                block.getLocation().add(0.5, 1.0, 0.5), 10, 0.2, 0.2, 0.2, 0.03);
            block.getWorld().playSound(block.getLocation(),
                Sound.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.8f, 1.0f);
            return;
        }

        // 情報表示
        int buffer = getBuffer(tileState);
        player.sendMessage(Component.text(
            "ヴォルカニックソースリンク - 蓄積ソース: " + buffer, NamedTextColor.RED
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
