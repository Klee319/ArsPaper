package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * 対象ブロックをリスト内の次のブロックに変更するEffect。
 * ブロック: ブロックが属するティアリストから次のブロックに変換する。
 *   - Amplifyでティアを上げる（上位のリストから選択）
 *   - BlockBreakEvent + BlockPlaceEventを発火して保護プラグイン互換。
 *   - AOE対応。
 * エンティティ: キャスターと対象エンティティの位置を入れ替える。
 */
public class ExchangeEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    /**
     * ブロックのティアリスト。Amplifyで上位ティアから選択できる。
     * 同一ティア内ではサイクル（次のブロックに変換）する。
     */
    private static final List<List<Material>> BLOCK_TIERS = List.of(
        // Tier 0: 基本ブロック
        List.of(Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MUD),
        // Tier 1: 石系
        List.of(Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE),
        // Tier 2: 変成岩
        List.of(Material.ANDESITE, Material.DIORITE, Material.GRANITE),
        // Tier 3: 砂系
        List.of(Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY),
        // Tier 4: レンガ系
        List.of(Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS),
        // Tier 5: 木材
        List.of(Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
                Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
                Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS,
                Material.CRIMSON_PLANKS, Material.WARPED_PLANKS),
        // Tier 6: 原木
        List.of(Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
                Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
                Material.MANGROVE_LOG, Material.CHERRY_LOG),
        // Tier 7: ウール
        List.of(Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL,
                Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
                Material.PINK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL,
                Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
                Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL),
        // Tier 8: テラコッタ
        List.of(Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA,
                Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA,
                Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA,
                Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA, Material.BLUE_TERRACOTTA,
                Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA,
                Material.BLACK_TERRACOTTA),
        // Tier 9: コンクリート
        List.of(Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE,
                Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
                Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
                Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
                Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE,
                Material.BLACK_CONCRETE),
        // Tier 10: ガラス
        List.of(Material.GLASS, Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
                Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
                Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
                Material.PINK_STAINED_GLASS, Material.GRAY_STAINED_GLASS,
                Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
                Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS,
                Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS,
                Material.RED_STAINED_GLASS, Material.BLACK_STAINED_GLASS)
    );

    public ExchangeEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "exchange");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        if (caster == null) return;

        // PvP無効ワールドではプレイヤー対象を拒否
        if (target instanceof Player && !target.getWorld().getPVP()) return;

        // キャスターと対象エンティティの位置を入れ替える
        Location casterLoc = caster.getLocation().clone();
        Location targetLoc = target.getLocation().clone();

        Location casterDest = targetLoc.clone();
        casterDest.setYaw(casterLoc.getYaw());
        casterDest.setPitch(casterLoc.getPitch());

        Location targetDest = casterLoc.clone();
        targetDest.setYaw(targetLoc.getYaw());
        targetDest.setPitch(targetLoc.getPitch());

        target.teleport(targetDest);
        caster.teleport(casterDest);

        casterDest.getWorld().spawnParticle(
            org.bukkit.Particle.PORTAL, casterDest.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.5);
        targetDest.getWorld().spawnParticle(
            org.bukkit.Particle.PORTAL, targetDest.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.5);
        casterDest.getWorld().playSound(casterDest,
            org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, org.bukkit.SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Player caster = context.getCaster();
        if (caster == null) return;

        Block target = blockLocation.getBlock();
        if (target.getType().isAir()) return;

        Material currentType = target.getType();
        int amplify = Math.max(0, context.getAmplifyLevel());

        // ブロックが属するティアリストを検索
        Material nextType = findNextBlock(currentType, amplify);
        if (nextType == null || nextType == currentType) return;

        // 保護チェック: 破壊
        BlockBreakEvent breakEvent = new BlockBreakEvent(target, caster);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        // 保護チェック: 設置
        BlockState previousState = target.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            target, previousState, target.getRelative(BlockFace.DOWN),
            new ItemStack(nextType), caster, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        // ブロック変換（ドロップなし）
        target.setType(nextType);

        // エフェクト
        blockLocation.getWorld().spawnParticle(
            org.bukkit.Particle.PORTAL, blockLocation.clone().add(0.5, 1.0, 0.5), 15, 0.3, 0.4, 0.3, 0.4);
        blockLocation.getWorld().playSound(blockLocation,
            org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * 現在のブロックタイプが属するティアリストから次のブロックを返す。
     * amplifyでティアを上げる（上位のリストから選択）。
     * リストに見つからない場合はnullを返す。
     */
    private Material findNextBlock(Material current, int amplify) {
        for (int tierIndex = 0; tierIndex < BLOCK_TIERS.size(); tierIndex++) {
            List<Material> tier = BLOCK_TIERS.get(tierIndex);
            int currentIndex = tier.indexOf(current);
            if (currentIndex >= 0) {
                // amplifyでティアアップ
                int targetTier = Math.min(tierIndex + amplify, BLOCK_TIERS.size() - 1);
                List<Material> targetList = BLOCK_TIERS.get(targetTier);

                if (targetTier == tierIndex) {
                    // 同一ティア内でサイクル
                    int nextIndex = (currentIndex + 1) % tier.size();
                    return tier.get(nextIndex);
                } else {
                    // 上位ティアの先頭を返す
                    return targetList.get(0);
                }
            }
        }
        return null;
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "交換"; }

    @Override
    public String getDescription() { return "ブロックを変換する"; }

    @Override
    public int getManaCost() { return config.getManaCost("exchange"); }

    @Override
    public int getTier() { return config.getTier("exchange"); }
}
