package com.arspaper.source.sourcelink;

import com.arspaper.block.BlockKeys;
import com.arspaper.integration.TrinityForgeBridge;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Alchemical Sourcelink - 醸造素材を消費してSourceを生成。
 * バニラのBLAST_FURNACE（溶鉱炉）をベースに使用。
 *
 * 右クリックで醸造素材を投入し、素材の希少度に基づいたソースポイントを蓄積。
 * 定期的にバッファから排出して隣接Source Jarに供給する。
 */
public class AlchemicalSourcelink extends Sourcelink {

    /**
     * デフォルトの醸造・錬金素材 → ソースポイント（設定ファイルが無い場合に使用）
     */
    private static final Map<Material, Integer> DEFAULT_ALCHEMY_VALUES = Map.ofEntries(
        Map.entry(Material.GLASS_BOTTLE, 1),
        Map.entry(Material.SUGAR, 1),
        Map.entry(Material.GUNPOWDER, 1),
        Map.entry(Material.REDSTONE, 1),
        Map.entry(Material.GLOWSTONE_DUST, 1),
        Map.entry(Material.SPIDER_EYE, 1),
        Map.entry(Material.NETHER_WART, 3),
        Map.entry(Material.FERMENTED_SPIDER_EYE, 3),
        Map.entry(Material.GLISTERING_MELON_SLICE, 3),
        Map.entry(Material.GOLDEN_CARROT, 3),
        Map.entry(Material.PUFFERFISH, 3),
        Map.entry(Material.MAGMA_CREAM, 3),
        Map.entry(Material.BLAZE_POWDER, 8),
        Map.entry(Material.RABBIT_FOOT, 8),
        Map.entry(Material.PHANTOM_MEMBRANE, 8),
        Map.entry(Material.TURTLE_HELMET, 8),
        Map.entry(Material.GHAST_TEAR, 15),
        Map.entry(Material.EXPERIENCE_BOTTLE, 15),
        Map.entry(Material.POTION, 5),
        Map.entry(Material.SPLASH_POTION, 8),
        Map.entry(Material.LINGERING_POTION, 10),
        Map.entry(Material.DRAGON_BREATH, 30)
    );

    /** 実行時に使用する錬金素材値マップ（設定ファイルから読み込み可能） */
    private com.arspaper.item.ItemCostTable alchemyValues =
            com.arspaper.item.ItemCostTable.fromMaterials(DEFAULT_ALCHEMY_VALUES);

    public AlchemicalSourcelink(JavaPlugin plugin) {
        super(plugin, "alchemical_sourcelink");
    }

    /** カスタムソースリンク (sourcelinks.yml items.<id> type: alchemical) 用: 任意idで同じ挙動の別ブロックを作る。 */
    public AlchemicalSourcelink(JavaPlugin plugin, String blockId) {
        super(plugin, blockId);
    }

    static Map<Material, Integer> getDefaultAlchemyValues() {
        return DEFAULT_ALCHEMY_VALUES;
    }

    public void setAlchemyValues(com.arspaper.item.ItemCostTable values) {
        this.alchemyValues = values != null && !values.isEmpty()
                ? values
                : com.arspaper.item.ItemCostTable.fromMaterials(DEFAULT_ALCHEMY_VALUES);
    }

    @Override
    public Material getBlockMaterial() {
        return materialOr(Material.BLAST_FURNACE);
    }

    @Override
    public Component getDisplayName() {
        return displayNameOr(Component.text("アルケミカルソースリンク", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
    }

    @Override
    public int getCustomModelData() {
        return cmdOr(200005);
    }

    @Override
    public ItemStack createItemStack() {
        return withConfiguredOrDefaultLore(super.createItemStack(), List.of(
                Component.text("醸造素材を消費してソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("醸造素材を手に持って右クリックで投入", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
        ));
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.DRAGON_BREATH);
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
        return alchemyValues.valueOf(item);
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        int sourceValue = alchemyValues.valueOf(hand);

        if (sourceValue > 0) {
            int addCount = player.isSneaking() ? hand.getAmount() : 1;
            addCount = Math.min(addCount, hand.getAmount());
            int totalAdded = sourceValue * addCount;

            addToBuffer(block, totalAdded);

            // 要件 ingredient-no-consume-chance: skilltree由来のperkで素材消費をまれにスキップする。
            // バッファへの加算(addToBuffer)は通常通り行い、スキップするのは「素材の消費」のみ。
            // TF未ロード/perk未所持時はfrac<=0のため必ず従来通り消費する(fail-open)。
            // 2026-07-23 正準スケール分数統一によりstat値は分数[0,1](例 0.10=10%)。
            double noConsumeFrac = TrinityForgeBridge.tfIngredientNoConsumeChanceFraction(player);
            boolean skipConsume = noConsumeFrac > 0.0
                && ThreadLocalRandom.current().nextDouble() < noConsumeFrac;
            if (!skipConsume) {
                hand.setAmount(hand.getAmount() - addCount);
            }

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
