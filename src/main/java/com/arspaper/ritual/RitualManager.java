package com.arspaper.ritual;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.block.impl.Pedestal;
import com.arspaper.block.impl.SourceJar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 儀式の実行を管理する。
 * Ritual Coreの周囲5ブロック以内のPedestalを検索し、
 * 素材が一致するレシピを発動する。
 */
public class RitualManager {

    private static final int SEARCH_RADIUS = 5;
    private final RitualRecipeRegistry recipeRegistry;

    public RitualManager(RitualRecipeRegistry recipeRegistry) {
        this.recipeRegistry = recipeRegistry;
    }

    /**
     * 儀式の実行を試みる。
     */
    public void tryPerformRitual(Player player, Location coreLocation) {
        // 周囲のPedestalを検索
        List<PedestalInfo> pedestals = findNearbyPedestals(coreLocation);

        if (pedestals.isEmpty()) {
            player.sendMessage(Component.text("周囲に台座が見つかりません！", NamedTextColor.YELLOW));
            return;
        }

        // Pedestalの素材リストを構築
        List<Material> pedestalMaterials = new ArrayList<>();
        for (PedestalInfo info : pedestals) {
            if (info.item != null) {
                pedestalMaterials.add(info.item);
            }
        }

        if (pedestalMaterials.isEmpty()) {
            player.sendMessage(Component.text("台座が空です！先にアイテムを置いてください", NamedTextColor.YELLOW));
            return;
        }

        // マッチするレシピを検索
        Optional<RitualRecipe> matchOpt = recipeRegistry.findMatch(pedestalMaterials);
        if (matchOpt.isEmpty()) {
            player.sendMessage(Component.text("これらのアイテムに一致する儀式がありません！", NamedTextColor.RED));
            return;
        }

        RitualRecipe recipe = matchOpt.get();

        // Source消費チェック（近隣のSource Jarから）
        if (recipe.sourceRequired() > 0) {
            if (!consumeSourceFromNearby(coreLocation, recipe.sourceRequired())) {
                player.sendMessage(Component.text(
                    "ソースが不足しています！必要量: " + recipe.sourceRequired(), NamedTextColor.RED));
                return;
            }
        }

        // 儀式実行
        ItemStack result = resolveResult(recipe);
        if (result == null) {
            player.sendMessage(Component.text("儀式の結果が無効です！", NamedTextColor.RED));
            return;
        }

        // Pedestalの素材を消費
        consumePedestalItems(pedestals, recipe.pedestalItems());

        // 結果をドロップ
        coreLocation.getWorld().dropItemNaturally(
            coreLocation.clone().add(0.5, 1.5, 0.5), result
        );

        // エフェクト
        playRitualEffects(coreLocation);

        player.sendMessage(Component.text(
            "儀式完了: " + recipe.name() + "！", NamedTextColor.GREEN));
    }

    private List<PedestalInfo> findNearbyPedestals(Location center) {
        List<PedestalInfo> pedestals = new ArrayList<>();

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    // BREWING_STANDのみチェック（安価なMaterial比較を先に）
                    if (block.getType() != Material.BREWING_STAND) continue;
                    if (!(block.getState() instanceof TileState tileState)) continue;

                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (!"pedestal".equals(blockId)) continue;

                    Material item = Pedestal.getPedestalItem(tileState);
                    pedestals.add(new PedestalInfo(block, tileState, item));
                }
            }
        }
        return pedestals;
    }

    private boolean consumeSourceFromNearby(Location center, int amount) {
        // Phase 1: 利用可能なSource Jarと量を調査（まだ消費しない）
        record JarInfo(TileState tileState, int available) {}
        List<JarInfo> jars = new ArrayList<>();
        int totalAvailable = 0;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (!(block.getState() instanceof TileState tileState)) continue;

                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (!"source_jar".equals(blockId)) continue;

                    int available = SourceJar.getSourceAmount(tileState);
                    if (available > 0) {
                        jars.add(new JarInfo(tileState, available));
                        totalAvailable += available;
                    }
                }
            }
        }

        // Phase 2: 合計が足りなければ何も消費せず失敗
        if (totalAvailable < amount) return false;

        // Phase 3: 十分な量があるので実際に消費
        int remaining = amount;
        for (JarInfo jar : jars) {
            if (remaining <= 0) break;
            int consume = Math.min(jar.available, remaining);
            SourceJar.consumeSource(jar.tileState, consume);
            remaining -= consume;
        }
        return true;
    }

    private void consumePedestalItems(List<PedestalInfo> pedestals, List<Material> requiredItems) {
        List<Material> toConsume = new ArrayList<>(requiredItems);

        for (PedestalInfo pedestal : pedestals) {
            if (pedestal.item != null && toConsume.remove(pedestal.item)) {
                Pedestal.clearPedestalItem(pedestal.tileState);
            }
        }
    }

    private ItemStack resolveResult(RitualRecipe recipe) {
        if (recipe.isCustomResult()) {
            return ArsPaper.getInstance().getItemRegistry()
                .get(recipe.resultId())
                .map(item -> item.createItemStack())
                .orElse(null);
        }
        if (recipe.resultMaterial() != null) {
            return new ItemStack(recipe.resultMaterial());
        }
        return null;
    }

    private void playRitualEffects(Location location) {
        Location effectLoc = location.clone().add(0.5, 1.0, 0.5);
        location.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 100, 1.0, 1.0, 1.0, 0.5);
        location.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 30, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().playSound(effectLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
    }

    private record PedestalInfo(Block block, TileState tileState, Material item) {}
}
