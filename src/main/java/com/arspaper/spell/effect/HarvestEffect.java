package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * 成熟した作物を収穫するEffect。Ars NouveauのEffectHarvestに準拠。
 * - Ageable作物: 最大成長時に収穫。Amplifyレベルに応じて植え直し確率が変動。
 * - 耕地化: DIRT/GRASS_BLOCK等をFARMLANDに変換。
 * - 非作物植物: 花・雑草等を自然に破壊。
 */
public class HarvestEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public HarvestEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "harvest");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // HarvestはエンティティにはNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Material type = block.getType();

        // Case 1: Ageable crops (wheat, carrots, potatoes, etc.)
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;

            // 保護プラグイン互換: BlockBreakEventを発火して許可を確認
            org.bukkit.entity.Player caster = context.getCaster();
            if (caster == null) return;
            BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
            Bukkit.getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled()) return;

            // Amplifyレベルに応じて植え直し判定（スペル1回につき1回判定）
            int amplify = Math.max(0, context.getAmplifyLevel());
            boolean shouldReplant = shouldReplant(amplify);

            if (shouldReplant) {
                // ブロック情報を保存
                Material savedMaterial = block.getType();
                BlockData savedBlockData = block.getBlockData();

                // ドロップを取得してから手動でブロックを消す
                Collection<ItemStack> drops = block.getDrops();
                block.setType(Material.AIR);

                // 植え直し分の種を1つ除去
                removeSeedFromDrops(drops, savedMaterial);

                Location dropLoc = blockLocation.clone().add(0.5, 0.5, 0.5);
                for (ItemStack drop : drops) {
                    if (!drop.getType().isAir() && drop.getAmount() > 0) {
                        block.getWorld().dropItemNaturally(dropLoc, drop);
                    }
                }

                // 植え直し: 成長段階0で復元
                block.setType(savedMaterial);
                if (savedBlockData instanceof Ageable resetAgeable) {
                    resetAgeable.setAge(0);
                    block.setBlockData(resetAgeable);
                }
            } else {
                // 植え直さずに収穫のみ
                block.breakNaturally();
            }

            SpellFxUtil.spawnHarvestFx(blockLocation);
            return;
        }

        // Case 2: 耕地化（DIRT, GRASS_BLOCK等 → FARMLAND）
        if (isTillable(type)) {
            Block above = block.getRelative(BlockFace.UP);
            if (above.getType().isAir() || above.getBlockData() instanceof Ageable) {
                block.setType(Material.FARMLAND);
                SpellFxUtil.spawnHarvestFx(blockLocation);
            }
            return;
        }

        // Case 3: 非作物植物（花、雑草等）を自然に破壊
        if (isHarvestablePlant(type)) {
            block.breakNaturally();
            SpellFxUtil.spawnHarvestFx(blockLocation);
            return;
        }
    }

    /**
     * Amplifyレベルに応じた植え直し判定。
     * 0以下: 植え直さない, 1: 33%, 2: 66%, 3+: 100%
     */
    private boolean shouldReplant(int amplifyLevel) {
        if (amplifyLevel <= 0) return false;
        if (amplifyLevel >= 3) return true;
        double chance = amplifyLevel / 3.0;
        return Math.random() < chance;
    }

    private boolean isTillable(Material type) {
        return type == Material.DIRT || type == Material.GRASS_BLOCK
                || type == Material.DIRT_PATH || type == Material.COARSE_DIRT;
    }

    private boolean isHarvestablePlant(Material type) {
        String name = type.name();
        return name.contains("FLOWER") || name.contains("TULIP") || name.contains("DAISY")
                || name.contains("ORCHID") || name.contains("ALLIUM") || name.contains("CORNFLOWER")
                || type == Material.POPPY || type == Material.DANDELION
                || type == Material.TALL_GRASS || type == Material.SHORT_GRASS
                || type == Material.FERN || type == Material.LARGE_FERN
                || type == Material.DEAD_BUSH || type == Material.SWEET_BERRY_BUSH
                || type == Material.KELP || type == Material.KELP_PLANT
                || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS
                || type == Material.VINE || type == Material.SUGAR_CANE
                || type == Material.BAMBOO || type == Material.NETHER_WART
                || type == Material.LILY_PAD;
    }

    /**
     * ドロップから種を1つ除去する（植え直し分）。
     */
    private void removeSeedFromDrops(Collection<ItemStack> drops, Material cropType) {
        Material seedType = getSeedType(cropType);
        if (seedType == null) return;
        for (ItemStack drop : drops) {
            if (drop.getType() == seedType && drop.getAmount() > 0) {
                drop.setAmount(drop.getAmount() - 1);
                return;
            }
        }
    }

    private Material getSeedType(Material cropType) {
        return switch (cropType) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case TORCHFLOWER_CROP -> Material.TORCHFLOWER_SEEDS;
            case PITCHER_CROP -> Material.PITCHER_POD;
            default -> null;
        };
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "収穫"; }

    @Override
    public String getDescription() { return "成熟した作物を収穫する"; }

    @Override
    public int getManaCost() { return config.getManaCost("harvest"); }

    @Override
    public int getTier() { return config.getTier("harvest"); }
}
