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
        int duration = BASE_DURATION_TICKS + context.getDurationTicks();
        int amplifier = Math.max(0, context.getAmplifyLevel());
        target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, amplifier));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, amplifier));
        SpellFxUtil.spawnLightFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;
        // エンティティチェックはSpellContext側で一括実施

        if (!block.getType().isAir()) return;

        // 下にソリッドブロックがあれば通常の松明
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType().isSolid()) {
            if (placeTorch(block, below, caster)) {
                SpellFxUtil.spawnLightFx(blockLocation);
            }
            return;
        }

        // 隣接にソリッドブロックがあれば壁松明
        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType().isSolid()) {
                if (placeWallTorch(block, face, caster)) {
                    SpellFxUtil.spawnLightFx(blockLocation);
                }
                return;
            }
        }
        // 隣接にソリッドブロックがない → 設置失敗（宙に浮かない）
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
        BlockPlaceEvent event = new BlockPlaceEvent(
            block, block.getState(), block.getRelative(attachedFace),
            new ItemStack(Material.WALL_TORCH), caster, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        block.setType(Material.WALL_TORCH);
        Directional directional = (Directional) block.getBlockData();
        directional.setFacing(attachedFace.getOppositeFace());
        block.setBlockData(directional);
        return true;
    }

    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_OUTWARD; }

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
