package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * ブロックに松明を設置、エンティティに暗視と発光を付与するEffect。
 * 設置先に隣接するソリッドブロックがない場合は設置失敗。
 */
public class LightEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 600; // 30秒
    private final NamespacedKey id;
    private final GlyphConfig config;

    public LightEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "light");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("light", "base-duration-ticks", BASE_DURATION_TICKS);
        int durationPerLevel = (int) config.getParam("light", "duration-per-level", 200.0);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;
        target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0));
        SpellFxUtil.spawnLightFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;

        // 対象が空気ブロック → そのまま松明設置を試みる
        if (block.getType().isAir()) {
            if (tryPlaceTorchAt(block, caster)) return;
        }

        // 対象がソリッドブロック → ヒット面側の空気ブロックに松明設置を試みる
        if (block.getType().isSolid()) {
            // ヒット面があればそちらを最優先
            org.bukkit.block.BlockFace hitFace = context.getHitFace();
            if (hitFace != null) {
                Block hitSide = block.getRelative(hitFace);
                if (hitSide.getType().isAir() && tryPlaceTorchAt(hitSide, caster)) return;
            }

            // 上面を次に試行
            Block above = block.getRelative(BlockFace.UP);
            if (above.getType().isAir() && tryPlaceTorchAt(above, caster)) return;

            // 残りの側面を試行
            for (BlockFace face : new BlockFace[]{
                    BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (face == hitFace) continue; // 既に試行済み
                Block adj = block.getRelative(face);
                if (adj.getType().isAir() && tryPlaceTorchAt(adj, caster)) return;
            }
        }
    }

    /**
     * 指定の空気ブロックに松明を設置する。
     * 下にソリッドがあれば通常松明、隣接にソリッドがあれば壁松明。
     */
    private boolean tryPlaceTorchAt(Block airBlock, org.bukkit.entity.Player caster) {
        // 下のブロックの上面が松明を支えられるか（階段・ハーフブロック等を正しく判定）
        Block below = airBlock.getRelative(BlockFace.DOWN);
        if (canSupportTorch(below, BlockFace.UP)) {
            if (placeTorch(airBlock, below, caster)) {
                SpellFxUtil.spawnLightFx(airBlock.getLocation());
                return true;
            }
        }

        // 隣接ブロックの側面が壁松明を支えられるか
        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adjacent = airBlock.getRelative(face);
            if (canSupportTorch(adjacent, face)) {
                if (placeWallTorch(airBlock, face, caster)) {
                    SpellFxUtil.spawnLightFx(airBlock.getLocation());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 指定ブロックの指定面が松明を支えられるかチェック。
     * バニラ準拠: フルブロックか、上面が平坦なハーフブロック(top)等のみ許可。
     */
    private boolean canSupportTorch(Block block, BlockFace face) {
        if (block.getType().isAir()) return false;
        var blockData = block.getBlockData();
        // ハーフブロック: top半分のみ上面で松明を支えられる
        if (blockData instanceof org.bukkit.block.data.type.Slab slab) {
            if (face == BlockFace.UP) {
                return slab.getType() == org.bukkit.block.data.type.Slab.Type.TOP
                    || slab.getType() == org.bukkit.block.data.type.Slab.Type.DOUBLE;
            }
            return false; // ハーフブロック側面は壁松明不可
        }
        // 階段: 上面は上付き階段のみ、側面は背面のみ
        if (blockData instanceof org.bukkit.block.data.type.Stairs stairs) {
            if (face == BlockFace.UP) {
                return stairs.getHalf() == org.bukkit.block.data.Bisected.Half.TOP;
            }
            return false; // 階段側面は壁松明不可（バニラ準拠）
        }
        // フェンス・壁・ガラス板等の細いブロックは支えられない
        if (blockData instanceof org.bukkit.block.data.type.Fence
            || blockData instanceof org.bukkit.block.data.type.Wall
            || blockData instanceof org.bukkit.block.data.type.GlassPane) {
            return false;
        }
        // それ以外のソリッドブロック
        return block.getType().isSolid();
    }

    private boolean placeTorch(Block block, Block support, org.bukkit.entity.Player caster) {
        BlockPlaceEvent event = new BlockPlaceEvent(
            block, block.getState(), support,
            new ItemStack(Material.TORCH), caster, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        block.setType(Material.TORCH);
        return true;
    }

    private boolean placeWallTorch(Block block, BlockFace attachedFace, org.bukkit.entity.Player caster) {
        // WALL_TORCHはアイテムとして存在しないため、TORCHでイベント発火
        BlockPlaceEvent event = new BlockPlaceEvent(
            block, block.getState(), block.getRelative(attachedFace),
            new ItemStack(Material.TORCH), caster, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        block.setType(Material.WALL_TORCH);
        Directional directional = (Directional) block.getBlockData();
        directional.setFacing(attachedFace.getOppositeFace());
        block.setBlockData(directional);
        return true;
    }

    // 松明は自分で隣接空気を探すのでFIXED（OUTWARD変換不要）

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "光明"; }

    @Override
    public String getDescription() { return "松明を設置、または暗視を付与"; }

    @Override
    public int getManaCost() { return config.getManaCost("light"); }

    @Override
    public int getTier() { return config.getTier("light"); }
}
