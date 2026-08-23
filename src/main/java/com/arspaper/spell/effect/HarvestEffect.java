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
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * 成熟した作物を収穫するEffect。Ars NouveauのEffectHarvestに準拠。
 * - Ageable作物: 最大成長時に収穫+植え直し。
 * - 耕地化: DIRT/GRASS_BLOCK等をFARMLANDに変換し、上の作物も収穫。
 * - 非作物植物: 花・雑草等を自然に破壊。
 *
 * AOEは半径増加(aoe_radius)で正方形範囲に拡大。内部処理。
 * Amplify: 植え直し確率（0=なし, 1=33%, 2=66%, 3+=100%）
 * Fortune: 作物ドロップ数増加。Extract: シルクタッチ（ブロックそのものをドロップ）。
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
        // エンティティの足元で発動
        applyToBlock(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Player caster = context.getCaster();
        if (caster == null) return;

        int baseRadius = (int) config.getParam("harvest", "base-radius", 0.0);
        int radius = baseRadius + context.getAoeRadiusLevel();
        int amplify = Math.max(0, context.getAmplifyLevel());
        int fortuneLevel = context.getFortuneLevel();
        boolean extract = context.getExtractCount() > 0;
        int centerX = blockLocation.getBlockX();
        int centerY = blockLocation.getBlockY();
        int centerZ = blockLocation.getBlockZ();

        // 半径0 = 着弾ブロック＋上のみ、半径1以上 = 正方形範囲
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // 着弾Y付近を広めにスキャン（足元形態対応: 地面+作物+上段）
                for (int dy = -1; dy <= 2; dy++) {
                    Block block = blockLocation.getWorld().getBlockAt(
                        centerX + dx, centerY + dy, centerZ + dz);
                    processBlock(block, caster, amplify, fortuneLevel, extract);
                }
            }
        }
    }

    /**
     * 1ブロックに対する収穫処理。
     */
    private void processBlock(Block block, Player caster, int amplify, int fortuneLevel, boolean extract) {
        Material type = block.getType();

        // Case 0: 洞窟のつた（グロウベリー）。実だけ摘んで、つるは残す。
        //
        // 2026-08-24 要望「ソースベリーを収穫魔法で取れるようにできない？」への対応。
        // ソースベリーは GLOW_BERRIES + アメジストの欠片のクラフト品(functional-items.yml)なので、
        // 収穫できる実体は原料のグロウベリー側。摘む処理自体は「使用」グリフ
        // ({@code InteractEffect#simulateRightClick})が既に持っていたので、同じ手順を踏む。
        //
        // ⚠ 下の Ageable 分岐より必ず前に置く。CAVE_VINES(先端)は Ageable でもあり、その age は
        //   「つるがどこまで伸びたか」であって実の熟度ではない。後ろに置くと最大 age のつるが
        //   breakNaturally() で丸ごと壊され、実を摘むどころか栽培設備が消える
        //   (=この分岐を入れるまで、収穫魔法を洞窟のつたへ当てると実際にそうなっていた)。
        if (type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            // CAVE_VINES(先端) の BlockData も CaveVinesPlant を実装するので1本で両方拾える。
            if (!(block.getBlockData() instanceof CaveVinesPlant vines) || !vines.isBerries()) {
                return;
            }
            // 破壊はしないが、保護プラグインに拒否させる口は他の分岐と揃える。
            // マーカー付きなので TF 側の採取ギミックは二重発動しない。
            BlockBreakEvent pickEvent = SpellBreakMarker.callMarkedBreakEvent(block, caster);
            if (pickEvent.isCancelled()) return;

            vines.setBerries(false);
            block.setBlockData(vines);
            // バニラの手摘みと同じ 1 個。幸運はここでは掛けない —— グロウベリーはソース経済の
            // 入り口(ベリー1個 = 100 ソース)なので、増量は別途決める話にする。
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(Material.GLOW_BERRIES));
            block.getWorld().playSound(block.getLocation(),
                    org.bukkit.Sound.BLOCK_CAVE_VINES_PICK_BERRIES,
                    org.bukkit.SoundCategory.BLOCKS, 1.0f, 1.0f);
            SpellFxUtil.spawnHarvestFx(block.getLocation());
            return;
        }

        // Case 1: Ageable作物（小麦、ニンジン等）
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;

            BlockBreakEvent breakEvent =
                    SpellBreakMarker.callMarkedBreakEvent(block, caster);
            if (breakEvent.isCancelled()) return;

            boolean shouldReplant = shouldReplant(amplify);

            // Extract: シルクタッチ（作物ブロックそのものをドロップ）
            if (extract) {
                ItemStack tool = new ItemStack(Material.WOODEN_HOE);
                tool.addEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1);
                Collection<ItemStack> drops = block.getDrops(tool);
                block.setType(Material.AIR);
                Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack drop : drops) {
                    if (!drop.getType().isAir() && drop.getAmount() > 0) {
                        block.getWorld().dropItemNaturally(dropLoc, drop);
                    }
                }
            } else if (shouldReplant) {
                Material savedMaterial = block.getType();
                BlockData savedBlockData = block.getBlockData();

                // Fortune: 幸運ツールでドロップ計算
                Collection<ItemStack> drops;
                if (fortuneLevel > 0) {
                    ItemStack tool = new ItemStack(Material.WOODEN_HOE);
                    tool.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE, fortuneLevel);
                    drops = block.getDrops(tool);
                } else {
                    drops = block.getDrops();
                }
                block.setType(Material.AIR);

                removeSeedFromDrops(drops, savedMaterial);

                Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack drop : drops) {
                    if (!drop.getType().isAir() && drop.getAmount() > 0) {
                        block.getWorld().dropItemNaturally(dropLoc, drop);
                    }
                }

                // 植え直し
                block.setType(savedMaterial);
                if (savedBlockData instanceof Ageable resetAgeable) {
                    resetAgeable.setAge(0);
                    block.setBlockData(resetAgeable);
                }
            } else {
                if (fortuneLevel > 0) {
                    ItemStack tool = new ItemStack(Material.WOODEN_HOE);
                    tool.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE, fortuneLevel);
                    Collection<ItemStack> drops = block.getDrops(tool);
                    block.setType(Material.AIR);
                    Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                    for (ItemStack drop : drops) {
                        if (!drop.getType().isAir() && drop.getAmount() > 0) {
                            block.getWorld().dropItemNaturally(dropLoc, drop);
                        }
                    }
                } else {
                    block.breakNaturally();
                }
            }

            SpellFxUtil.spawnHarvestFx(block.getLocation());
            return;
        }

        // Case 2: 耕地化（DIRT, GRASS_BLOCK等 → FARMLAND）
        if (isTillable(type)) {
            Block above = block.getRelative(BlockFace.UP);
            if (above.getType().isAir() || above.getBlockData() instanceof Ageable) {
                block.setType(Material.FARMLAND);
                SpellFxUtil.spawnHarvestFx(block.getLocation());
            }
            // 上に作物があれば同時に収穫
            if (above.getBlockData() instanceof Ageable) {
                processBlock(above, caster, amplify, fortuneLevel, extract);
            }
            return;
        }

        // Case 3: 非作物植物（花、雑草等）
        if (isHarvestablePlant(type)) {
            if (fortuneLevel > 0) {
                ItemStack tool = new ItemStack(Material.WOODEN_HOE);
                tool.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE, fortuneLevel);
                Collection<ItemStack> drops = block.getDrops(tool);
                block.setType(Material.AIR);
                Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack drop : drops) {
                    if (!drop.getType().isAir() && drop.getAmount() > 0) {
                        block.getWorld().dropItemNaturally(dropLoc, drop);
                    }
                }
            } else {
                block.breakNaturally();
            }
            SpellFxUtil.spawnHarvestFx(block.getLocation());
        }
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

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
