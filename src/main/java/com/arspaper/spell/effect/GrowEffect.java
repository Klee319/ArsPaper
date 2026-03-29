package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 作物や植物の成長を促進するEffect。
 * Amplifyで成長ステージの増加量が上昇。
 */
public class GrowEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public GrowEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "grow");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // 友好モブの子供を成長させる
        if (target instanceof Breedable breedable && !breedable.isAdult()) {
            breedable.setAdult();
            SpellFxUtil.spawnGrowFx(target.getLocation());
        }
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;

        int baseRadius = (int) config.getParam("grow", "base-radius", 0.0);
        int radius = baseRadius + context.getAoeRadiusLevel();
        int baseGrowth = (int) config.getParam("grow", "base-growth", 1.0);
        int growth = baseGrowth + context.getAmplifyLevel();

        // 正方形範囲で成長促進（上下2ブロック走査で足元形態にも対応）
        int centerX = blockLocation.getBlockX();
        int centerY = blockLocation.getBlockY();
        int centerZ = blockLocation.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Block block = blockLocation.getWorld().getBlockAt(
                        centerX + dx, centerY + dy, centerZ + dz);
                    growBlock(block, caster, growth);
                }
            }
        }
    }

    private void growBlock(Block block, org.bukkit.entity.Player caster, int growth) {
        if (block.getType().isAir()) return;

        // Ageableブロック（小麦、ニンジン、ジャガイモ、ビートルート等）: 直接ステージ操作
        // サトウキビ/サボテン/竹もAgeableだが、ageは内部タイマー用なので除外
        Material type = block.getType();
        if (type != Material.SUGAR_CANE && type != Material.CACTUS && type != Material.BAMBOO
                && block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                int newAge = Math.min(ageable.getAge() + growth, ageable.getMaximumAge());
                ageable.setAge(newAge);
                block.setBlockData(ageable);
                SpellFxUtil.spawnGrowFx(block.getLocation());
            }
            return;
        }

        // アメジストの芽: 小→中→大→クラスタ
        Material nextAmethyst = switch (type) {
            case SMALL_AMETHYST_BUD -> Material.MEDIUM_AMETHYST_BUD;
            case MEDIUM_AMETHYST_BUD -> Material.LARGE_AMETHYST_BUD;
            case LARGE_AMETHYST_BUD -> Material.AMETHYST_CLUSTER;
            default -> null;
        };
        if (nextAmethyst != null) {
            org.bukkit.block.data.BlockData oldData = block.getBlockData();
            block.setType(nextAmethyst);
            if (oldData instanceof org.bukkit.block.data.Directional oldDir
                    && block.getBlockData() instanceof org.bukkit.block.data.Directional newDir) {
                newDir.setFacing(oldDir.getFacing());
                block.setBlockData(newDir);
            }
            SpellFxUtil.spawnGrowFx(block.getLocation());
            return;
        }

        // サトウキビ/サボテン/竹: 上に1段追加（バニラ骨粉が効かないため独自処理）
        if (type == Material.SUGAR_CANE || type == Material.CACTUS || type == Material.BAMBOO) {
            Block top = block;
            while (top.getRelative(BlockFace.UP).getType() == type) {
                top = top.getRelative(BlockFace.UP);
            }
            int height = 1;
            Block check = top;
            while (check.getRelative(BlockFace.DOWN).getType() == type) {
                check = check.getRelative(BlockFace.DOWN);
                height++;
            }
            int maxHeight = type == Material.BAMBOO ? 16 : 3;
            for (int i = 0; i < growth && height < maxHeight; i++) {
                Block above = top.getRelative(BlockFace.UP);
                if (above.getType().isAir()) {
                    above.setType(type);
                    SpellFxUtil.spawnGrowFx(above.getLocation());
                    top = above;
                    height++;
                } else {
                    break;
                }
            }
            return;
        }

        // 芽生えたアメジスト: 隣接面にランダムで小さな芽を生成
        if (type == Material.BUDDING_AMETHYST) {
            BlockFace[] faces = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (int i = 0; i < growth; i++) {
                BlockFace face = faces[new java.util.Random().nextInt(faces.length)];
                Block adjacent = block.getRelative(face);
                if (adjacent.getType().isAir()) {
                    adjacent.setType(Material.SMALL_AMETHYST_BUD);
                    if (adjacent.getBlockData() instanceof org.bukkit.block.data.Directional dir) {
                        dir.setFacing(face);
                        adjacent.setBlockData(dir);
                    }
                    SpellFxUtil.spawnGrowFx(adjacent.getLocation());
                }
            }
            return;
        }

        // バニラ骨粉API: 苗木、花、草、苔、ドリップリーフ等
        boolean grew = false;
        for (int i = 0; i < growth; i++) {
            if (block.applyBoneMeal(BlockFace.UP)) {
                grew = true;
            }
        }
        if (grew) {
            SpellFxUtil.spawnGrowFx(block.getLocation());
        }
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "成長"; }

    @Override
    public String getDescription() { return "作物を成長させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("grow"); }

    @Override
    public int getTier() { return config.getTier("grow"); }
}
