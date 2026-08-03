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
     * デフォルトの燃料アイテム → ソースポイント（設定ファイルが無い場合に使用）
     */
    private static final Map<Material, Integer> DEFAULT_FUEL_VALUES = Map.ofEntries(
        Map.entry(Material.STICK, 1),
        Map.entry(Material.BAMBOO, 1),
        Map.entry(Material.WHITE_CARPET, 1),
        Map.entry(Material.OAK_SLAB, 1),
        Map.entry(Material.SPRUCE_SLAB, 1),
        Map.entry(Material.BIRCH_SLAB, 1),
        Map.entry(Material.JUNGLE_SLAB, 1),
        Map.entry(Material.ACACIA_SLAB, 1),
        Map.entry(Material.DARK_OAK_SLAB, 1),
        Map.entry(Material.MANGROVE_SLAB, 1),
        Map.entry(Material.CHERRY_SLAB, 1),
        Map.entry(Material.BAMBOO_SLAB, 1),
        Map.entry(Material.OAK_PLANKS, 1),
        Map.entry(Material.SPRUCE_PLANKS, 1),
        Map.entry(Material.BIRCH_PLANKS, 1),
        Map.entry(Material.JUNGLE_PLANKS, 1),
        Map.entry(Material.ACACIA_PLANKS, 1),
        Map.entry(Material.DARK_OAK_PLANKS, 1),
        Map.entry(Material.MANGROVE_PLANKS, 1),
        Map.entry(Material.CHERRY_PLANKS, 1),
        Map.entry(Material.BAMBOO_PLANKS, 1),
        Map.entry(Material.OAK_LOG, 3),
        Map.entry(Material.SPRUCE_LOG, 3),
        Map.entry(Material.BIRCH_LOG, 3),
        Map.entry(Material.JUNGLE_LOG, 3),
        Map.entry(Material.ACACIA_LOG, 3),
        Map.entry(Material.DARK_OAK_LOG, 3),
        Map.entry(Material.MANGROVE_LOG, 3),
        Map.entry(Material.CHERRY_LOG, 3),
        Map.entry(Material.CHARCOAL, 5),
        Map.entry(Material.COAL, 5),
        Map.entry(Material.BLAZE_ROD, 10),
        Map.entry(Material.DRIED_KELP_BLOCK, 10),
        Map.entry(Material.COAL_BLOCK, 45),
        Map.entry(Material.LAVA_BUCKET, 100)
    );

    /** 実行時に使用する燃料値マップ（設定ファイルから読み込み可能） */
    private com.arspaper.item.ItemCostTable fuelValues =
            com.arspaper.item.ItemCostTable.fromMaterials(DEFAULT_FUEL_VALUES);

    public VolcanicSourcelink(JavaPlugin plugin) {
        super(plugin, "volcanic_sourcelink");
    }

    /** カスタムソースリンク (sourcelinks.yml items.<id> type: volcanic) 用: 任意idで同じ挙動の別ブロックを作る。 */
    public VolcanicSourcelink(JavaPlugin plugin, String blockId) {
        super(plugin, blockId);
    }

    /**
     * デフォルトの燃料値マップを返す（設定ファイルが無い場合のフォールバック用）。
     */
    static Map<Material, Integer> getDefaultFuelValues() {
        return DEFAULT_FUEL_VALUES;
    }

    /**
     * 設定ファイルから読み込んだ燃料値マップを設定する。
     */
    public void setFuelValues(com.arspaper.item.ItemCostTable values) {
        this.fuelValues = values != null && !values.isEmpty()
                ? values
                : com.arspaper.item.ItemCostTable.fromMaterials(DEFAULT_FUEL_VALUES);
    }

    @Override
    public Material getBlockMaterial() {
        return materialOr(Material.FURNACE);
    }

    @Override
    public Component getDisplayName() {
        return displayNameOr(Component.text("ヴォルカニックソースリンク", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
    }

    @Override
    public int getCustomModelData() {
        return cmdOr(200003);
    }

    @Override
    public ItemStack createItemStack() {
        return withConfiguredOrDefaultLore(super.createItemStack(), List.of(
                Component.text("燃料を燃やしてソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("燃料を手に持って右クリックで投入", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
        ));
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.MAGMA_BLOCK);
        head.editMeta(meta -> meta.setCustomModelData(getCustomModelData()));
        return head;
    }

    @Override
    public SourceYield generateSource(Block block) {
        // 受動生成は無し — この実装は焼べた分(バッファ)しか吐かない。
        // 注ぎ切れなかった分は全額バッファへ戻る(SourceYield.refundToBuffer)。
        return SourceYield.ofBuffer(drainBuffer(block));
    }

    @Override
    public int getSourceValueForItem(org.bukkit.inventory.ItemStack item) {
        return fuelValues.valueOf(item);
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        int sourceValue = fuelValues.valueOf(hand);

        if (sourceValue > 0) {
            int addCount = player.isSneaking() ? hand.getAmount() : 1;
            addCount = Math.min(addCount, hand.getAmount());
            // 階梯の生成量倍率(items.<id>.yield-multiplier)は「新しく生まれた分」にだけ掛ける。
            int totalAdded = scaleGeneratedYield((long) sourceValue * addCount);

            addToBuffer(block, totalAdded);
            boolean wasLavaBucket = hand.getType() == Material.LAVA_BUCKET && addCount == 1 && !isCustomItem(hand);
            hand.setAmount(hand.getAmount() - addCount);

            // 溶岩バケツは空バケツを返す（バニラのみ）
            if (wasLavaBucket) {
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
