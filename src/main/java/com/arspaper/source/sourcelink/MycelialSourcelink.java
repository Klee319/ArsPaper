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
 * Mycelial Sourcelink - 食料アイテムを消費してSourceを生成。
 * バニラのSMOKER（燻製器）をベースに使用。
 *
 * 右クリックで食料を投入し、満腹度回復量に基づいたソースポイントを蓄積。
 * 定期的にバッファから排出して隣接Source Jarに供給する。
 */
public class MycelialSourcelink extends Sourcelink {

    /**
     * デフォルトの食料アイテム → ソースポイント（設定ファイルが無い場合に使用）
     */
    private static final Map<Material, Integer> DEFAULT_FOOD_VALUES = Map.ofEntries(
        Map.entry(Material.MELON_SLICE, 1),
        Map.entry(Material.SWEET_BERRIES, 1),
        Map.entry(Material.GLOW_BERRIES, 1),
        Map.entry(Material.APPLE, 1),
        Map.entry(Material.CARROT, 1),
        Map.entry(Material.POTATO, 1),
        Map.entry(Material.BEETROOT, 1),
        Map.entry(Material.DRIED_KELP, 3),
        Map.entry(Material.POISONOUS_POTATO, 10),
        Map.entry(Material.TROPICAL_FISH, 5),
        Map.entry(Material.SPIDER_EYE, 3),
        Map.entry(Material.COD, 3),
        Map.entry(Material.SALMON, 3),
        Map.entry(Material.CHICKEN, 3),
        Map.entry(Material.MUTTON, 3),
        Map.entry(Material.RABBIT, 5),
        Map.entry(Material.BEEF, 3),
        Map.entry(Material.PORKCHOP, 3),
        Map.entry(Material.COOKIE, 5),
        Map.entry(Material.BAKED_POTATO, 5),
        Map.entry(Material.BREAD, 5),
        Map.entry(Material.COOKED_COD, 5),
        Map.entry(Material.COOKED_SALMON, 5),
        Map.entry(Material.MUSHROOM_STEW, 7),
        Map.entry(Material.BEETROOT_SOUP, 5),
        Map.entry(Material.SUSPICIOUS_STEW, 7),
        Map.entry(Material.COOKED_CHICKEN, 5),
        Map.entry(Material.COOKED_MUTTON, 10),
        Map.entry(Material.COOKED_PORKCHOP, 10),
        Map.entry(Material.COOKED_BEEF, 10),
        Map.entry(Material.COOKED_RABBIT, 15),
        Map.entry(Material.RABBIT_STEW, 30),
        Map.entry(Material.PUMPKIN_PIE, 30),
        Map.entry(Material.GOLDEN_CARROT, 15),
        Map.entry(Material.GOLDEN_APPLE, 75)
    );

    /** 実行時に使用する食料値マップ（設定ファイルから読み込み可能） */
    private Map<Material, Integer> foodValues = DEFAULT_FOOD_VALUES;

    public MycelialSourcelink(JavaPlugin plugin) {
        super(plugin, "mycelial_sourcelink");
    }

    /**
     * デフォルトの食料値マップを返す（設定ファイルが無い場合のフォールバック用）。
     */
    static Map<Material, Integer> getDefaultFoodValues() {
        return DEFAULT_FOOD_VALUES;
    }

    /**
     * 設定ファイルから読み込んだ食料値マップを設定する。
     */
    public void setFoodValues(Map<Material, Integer> values) {
        this.foodValues = values != null ? values : DEFAULT_FOOD_VALUES;
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
                Component.text("食料を手に持って右クリックで投入", NamedTextColor.DARK_GRAY)
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
    public int generateSource(Block block) {
        return drainBuffer(block);
    }

    @Override
    public int getSourceValueForItem(org.bukkit.inventory.ItemStack item) {
        if (item == null) return 0;
        Integer value = foodValues.get(item.getType());
        return value != null ? value : 0;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isCustomItem(hand)) {
            int buffer = getBuffer(tileState);
            player.sendMessage(Component.text(
                "マイセリアルソースリンク - 蓄積ソース: " + buffer, NamedTextColor.DARK_GREEN
            ));
            return;
        }
        Integer sourceValue = foodValues.get(hand.getType());

        if (sourceValue != null) {
            int addCount = player.isSneaking() ? hand.getAmount() : 1;
            addCount = Math.min(addCount, hand.getAmount());
            int totalAdded = sourceValue * addCount;

            addToBuffer(block, totalAdded);
            hand.setAmount(hand.getAmount() - addCount);

            int buffer = getBuffer((TileState) block.getState());
            player.sendMessage(Component.text(
                "食料を" + addCount + "個投入 [+" + totalAdded + " Source] (蓄積: " + buffer + ")",
                NamedTextColor.DARK_GREEN
            ));
            // 投入エフェクト
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                block.getLocation().add(0.5, 1.0, 0.5), 8, 0.2, 0.2, 0.2, 0.0);
            block.getWorld().playSound(block.getLocation(),
                Sound.ENTITY_GENERIC_EAT, SoundCategory.BLOCKS, 0.8f, 1.0f);
            return;
        }

        // 情報表示
        int buffer = getBuffer(tileState);
        player.sendMessage(Component.text(
            "マイセリアルソースリンク - 蓄積ソース: " + buffer, NamedTextColor.DARK_GREEN
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
