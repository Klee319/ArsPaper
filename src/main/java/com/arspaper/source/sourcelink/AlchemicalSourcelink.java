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
 * Alchemical Sourcelink - 醸造素材を消費してSourceを生成。
 * バニラのBLAST_FURNACE（溶鉱炉）をベースに使用。
 *
 * 右クリックで醸造素材を投入し、素材の希少度に基づいたソースポイントを蓄積。
 * 定期的にバッファから排出して隣接Source Jarに供給する。
 */
public class AlchemicalSourcelink extends Sourcelink {

    /**
     * 醸造・錬金素材 → ソースポイント（希少度基準）
     * 基準: 石炭=5相当。ネザーウォート=3、ブレイズパウダー=8
     */
    private static final Map<Material, Integer> ALCHEMY_VALUES = Map.ofEntries(
        // 最低（1）: 基本素材
        Map.entry(Material.GLASS_BOTTLE, 1),
        Map.entry(Material.SUGAR, 1),
        Map.entry(Material.GUNPOWDER, 1),
        Map.entry(Material.REDSTONE, 1),
        Map.entry(Material.GLOWSTONE_DUST, 1),
        Map.entry(Material.SPIDER_EYE, 1),
        // 低（3）: 標準醸造素材
        Map.entry(Material.NETHER_WART, 3),
        Map.entry(Material.FERMENTED_SPIDER_EYE, 3),
        Map.entry(Material.GLISTERING_MELON_SLICE, 3),
        Map.entry(Material.GOLDEN_CARROT, 3),
        Map.entry(Material.PUFFERFISH, 3),
        Map.entry(Material.MAGMA_CREAM, 3),
        // 中（8）: 希少素材
        Map.entry(Material.BLAZE_POWDER, 8),
        Map.entry(Material.RABBIT_FOOT, 8),
        Map.entry(Material.PHANTOM_MEMBRANE, 8),
        Map.entry(Material.TURTLE_HELMET, 8),
        // 高（15）: レア素材
        Map.entry(Material.GHAST_TEAR, 15),
        Map.entry(Material.EXPERIENCE_BOTTLE, 15),
        // ポーション系
        Map.entry(Material.POTION, 5),
        Map.entry(Material.SPLASH_POTION, 8),
        Map.entry(Material.LINGERING_POTION, 10),
        // 最高（30）: 最希少
        Map.entry(Material.DRAGON_BREATH, 30)
    );

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
                Component.text("醸造素材を消費してソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("醸造素材を手に持って右クリックで投入", NamedTextColor.DARK_GRAY)
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
    public int generateSource(Block block) {
        return drainBuffer(block);
    }

    @Override
    public int getSourceValueForItem(org.bukkit.inventory.ItemStack item) {
        if (item == null) return 0;
        Integer value = ALCHEMY_VALUES.get(item.getType());
        return value != null ? value : 0;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Integer sourceValue = ALCHEMY_VALUES.get(hand.getType());

        if (sourceValue != null) {
            int addCount = player.isSneaking() ? hand.getAmount() : 1;
            addCount = Math.min(addCount, hand.getAmount());
            int totalAdded = sourceValue * addCount;

            addToBuffer(block, totalAdded);
            hand.setAmount(hand.getAmount() - addCount);

            int buffer = getBuffer((TileState) block.getState());
            player.sendMessage(Component.text(
                "素材を" + addCount + "個投入 [+" + totalAdded + " Source] (蓄積: " + buffer + ")",
                NamedTextColor.DARK_PURPLE
            ));
            // 投入エフェクト
            block.getWorld().spawnParticle(Particle.WITCH,
                block.getLocation().add(0.5, 1.0, 0.5), 10, 0.2, 0.2, 0.2, 0.02);
            block.getWorld().playSound(block.getLocation(),
                Sound.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.8f, 1.2f);
            return;
        }

        // 情報表示
        int buffer = getBuffer(tileState);
        player.sendMessage(Component.text(
            "アルケミカルソースリンク - 蓄積ソース: " + buffer, NamedTextColor.DARK_PURPLE
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
