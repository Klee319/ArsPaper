package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * ブロックに光源を設置、エンティティに夜間視覚と発光を付与するEffect。
 * 保護プラグイン互換: BlockPlaceEventを発火して許可を確認する。
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

        // ターゲットが空気ブロック: そのまま松明を設置
        if (block.getType().isAir()) {
            if (placeTorchWithProtection(block, caster)) {
                SpellFxUtil.spawnLightFx(blockLocation);
            }
            return;
        }

        // ターゲットが固体ブロック: 上面の空気ブロックに松明を設置
        Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
        if (above.getType().isAir()) {
            if (placeTorchWithProtection(above, caster)) {
                SpellFxUtil.spawnLightFx(above.getLocation());
            }
            return;
        }

        // 上面がダメなら側面にウォールトーチを試行
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType().isAir()) {
                if (placeWallTorchWithProtection(adjacent, face.getOppositeFace(), caster)) {
                    SpellFxUtil.spawnLightFx(adjacent.getLocation());
                }
                return;
            }
        }
    }

    private boolean placeTorchWithProtection(Block block, org.bukkit.entity.Player caster) {
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block, block.getState(), block.getRelative(org.bukkit.block.BlockFace.DOWN),
            new ItemStack(Material.TORCH), caster, true, EquipmentSlot.HAND);
        org.bukkit.Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return false;
        block.setType(Material.TORCH);
        return true;
    }

    private boolean placeWallTorchWithProtection(Block block, org.bukkit.block.BlockFace attachedFace,
                                                  org.bukkit.entity.Player caster) {
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block, block.getState(), block.getRelative(attachedFace),
            new ItemStack(Material.WALL_TORCH), caster, true, EquipmentSlot.HAND);
        org.bukkit.Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return false;
        block.setType(Material.WALL_TORCH);
        org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) block.getBlockData();
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
    public String getDescription() { return "光源を設置、または暗視を付与する"; }

    @Override
    public int getManaCost() { return config.getManaCost("light"); }

    @Override
    public int getTier() { return config.getTier("light"); }
}
