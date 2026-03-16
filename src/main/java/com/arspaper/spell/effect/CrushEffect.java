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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * 対象ブロックを粉砕し、エンティティにダメージを与えるEffect。Ars Nouveau準拠。
 * ブロック: 粉砕マップに基づきブロックを変換する（石→砂利、砂利→砂など）。
 * エンティティ: 物理ダメージ (3.0 + 2.0*amp)。水中にいる場合は3倍ダメージ。
 */
public class CrushEffect implements SpellEffect {

    private static final double BASE_DAMAGE = 3.0;
    private static final double AMPLIFY_BONUS = 2.0;
    private static final double WATER_MULTIPLIER = 3.0;

    /** 粉砕マップ: 変換前 → 変換後 */
    private static final Map<Material, Material> CRUSH_MAP = new HashMap<>();

    static {
        // 石系
        CRUSH_MAP.put(Material.STONE,              Material.GRAVEL);
        CRUSH_MAP.put(Material.COBBLESTONE,        Material.GRAVEL);
        CRUSH_MAP.put(Material.MOSSY_COBBLESTONE,  Material.GRAVEL);
        CRUSH_MAP.put(Material.GRAVEL,             Material.SAND);
        CRUSH_MAP.put(Material.DEEPSLATE,          Material.GRAVEL);
        CRUSH_MAP.put(Material.COBBLED_DEEPSLATE,  Material.GRAVEL);
        CRUSH_MAP.put(Material.ANDESITE,           Material.GRAVEL);
        CRUSH_MAP.put(Material.GRANITE,            Material.GRAVEL);
        CRUSH_MAP.put(Material.DIORITE,            Material.GRAVEL);
        CRUSH_MAP.put(Material.BLACKSTONE,         Material.GRAVEL);
        CRUSH_MAP.put(Material.BASALT,             Material.GRAVEL);
        CRUSH_MAP.put(Material.NETHERRACK,         Material.GRAVEL);
        // 砂系
        CRUSH_MAP.put(Material.SAND,               Material.SOUL_SAND);
        // 骨系
        CRUSH_MAP.put(Material.BONE_BLOCK,         Material.GRAVEL);
        // 氷系
        CRUSH_MAP.put(Material.ICE,                Material.SNOW_BLOCK);
        CRUSH_MAP.put(Material.PACKED_ICE,         Material.ICE);
        CRUSH_MAP.put(Material.BLUE_ICE,           Material.PACKED_ICE);
        CRUSH_MAP.put(Material.SNOW_BLOCK,         Material.SNOW);
        // 花 → 染料（ブロック変換：花を砕いて染料を得る）
        // ※ 花はブロックで設置されているが結果はアイテムなのでドロップ扱い
        CRUSH_MAP.put(Material.DANDELION,          Material.YELLOW_DYE);
        CRUSH_MAP.put(Material.POPPY,              Material.RED_DYE);
        CRUSH_MAP.put(Material.BLUE_ORCHID,        Material.LIGHT_BLUE_DYE);
        CRUSH_MAP.put(Material.ALLIUM,             Material.MAGENTA_DYE);
        CRUSH_MAP.put(Material.AZURE_BLUET,        Material.LIGHT_GRAY_DYE);
        CRUSH_MAP.put(Material.RED_TULIP,          Material.RED_DYE);
        CRUSH_MAP.put(Material.ORANGE_TULIP,       Material.ORANGE_DYE);
        CRUSH_MAP.put(Material.WHITE_TULIP,        Material.LIGHT_GRAY_DYE);
        CRUSH_MAP.put(Material.PINK_TULIP,         Material.PINK_DYE);
        CRUSH_MAP.put(Material.OXEYE_DAISY,        Material.LIGHT_GRAY_DYE);
        CRUSH_MAP.put(Material.CORNFLOWER,         Material.BLUE_DYE);
        CRUSH_MAP.put(Material.LILY_OF_THE_VALLEY, Material.WHITE_DYE);
        CRUSH_MAP.put(Material.WITHER_ROSE,        Material.BLACK_DYE);
        CRUSH_MAP.put(Material.SUNFLOWER,          Material.YELLOW_DYE);
        CRUSH_MAP.put(Material.LILAC,              Material.MAGENTA_DYE);
        CRUSH_MAP.put(Material.ROSE_BUSH,          Material.RED_DYE);
        CRUSH_MAP.put(Material.PEONY,              Material.PINK_DYE);
        // その他
        CRUSH_MAP.put(Material.OBSIDIAN,           Material.CRYING_OBSIDIAN);
        CRUSH_MAP.put(Material.TERRACOTTA,         Material.GRAVEL);
    }

    private final NamespacedKey id;
    private final GlyphConfig config;

    public CrushEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "crush");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double damage = Math.max(0, BASE_DAMAGE + context.getAmplifyLevel() * AMPLIFY_BONUS);

        // 水中判定: エンティティが水中にいる場合は3倍ダメージ
        if (target.isInWater()) {
            damage *= WATER_MULTIPLIER;
        }

        Player caster = context.getCaster();
        target.damage(damage, caster);

        // エフェクト
        Location loc = target.getLocation();
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.BLOCK, loc.clone().add(0, 1, 0),
            20, 0.4, 0.5, 0.4, 0.1,
            Material.GRAVEL.createBlockData());
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.BLOCK_GRAVEL_BREAK, org.bukkit.SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Material result = CRUSH_MAP.get(block.getType());
        if (result == null) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        // 花 → 染料の場合: ブロックを空気にしてアイテムをドロップ
        if (!result.isBlock()) {
            org.bukkit.event.block.BlockBreakEvent breakEvent =
                new org.bukkit.event.block.BlockBreakEvent(block, caster);
            Bukkit.getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled()) return;

            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(
                blockLocation.clone().add(0.5, 0.5, 0.5), new ItemStack(result));
            spawnCrushFx(blockLocation);
            return;
        }

        // 通常ブロック変換: BlockPlaceEventで保護確認
        BlockState previousState = block.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block,
            previousState,
            block.getRelative(BlockFace.DOWN),
            new ItemStack(result),
            caster,
            true,
            EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        block.setType(result);
        spawnCrushFx(blockLocation);
    }

    private void spawnCrushFx(Location loc) {
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5),
            20, 0.3, 0.3, 0.3, 0.1,
            Material.GRAVEL.createBlockData());
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.BLOCK_GRAVEL_BREAK, org.bukkit.SoundCategory.PLAYERS, 0.8f, 0.9f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "粉砕"; }

    @Override
    public String getDescription() { return "ブロックを粉砕する。水中の敵に3倍ダメージ"; }

    @Override
    public int getManaCost() { return config.getManaCost("crush"); }

    @Override
    public int getTier() { return config.getTier("crush"); }
}
