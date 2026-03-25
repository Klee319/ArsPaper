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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

/**
 * 水を生成し、炎を消し、エンティティにSoaked（Slowness I）を付与するEffect。Ars Nouveau準拠。
 * ブロック: 対象位置に水を生成し、隣接する炎ブロックを消す。
 * エンティティ: 炎を消火してSlowness I（Soaked）を付与する。
 *   - 基本持続: 60tick (3秒)
 *   - ExtendTimeで増加: +80tick/level
 *
 * 水は一定時間後に自動消滅する。消滅時、無限水源メカニクスで生成された
 * 隣接水源ブロックも合わせて除去する。
 */
public class ConjureWaterEffect implements SpellEffect {

    private static final int BASE_SOAKED_TICKS = 60;
    private static final int DURATION_BONUS_TICKS = 80;
    private static final int BASE_WATER_LIFETIME = 200;  // 10秒
    private static final int WATER_LIFETIME_PER_DURATION = 100; // +5秒/level
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public ConjureWaterEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "conjure_water");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // 炎を消す
        target.setFireTicks(0);

        // Soaked (Slowness I) を付与
        int baseSoaked = (int) config.getParam("conjure_water", "base-soaked-ticks", BASE_SOAKED_TICKS);
        int durationBonus = (int) config.getParam("conjure_water", "duration-bonus-ticks", DURATION_BONUS_TICKS);
        int durationTicks = Math.max(baseSoaked,
            baseSoaked + context.getDurationLevel() * durationBonus);
        target.addPotionEffect(
            new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 0, false, true, true));

        spawnWaterFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Player caster = context.getCaster();
        if (caster == null) return;

        // エンティティチェックはSpellContext側で一括実施
        if (block.getType().isAir() || block.getType() == Material.FIRE
                || block.getType() == Material.SOUL_FIRE) {

            BlockState previousState = block.getState();
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                block,
                previousState,
                block.getRelative(BlockFace.DOWN),
                new ItemStack(Material.WATER_BUCKET),
                caster,
                true,
                EquipmentSlot.HAND
            );
            Bukkit.getPluginManager().callEvent(placeEvent);
            if (!placeEvent.isCancelled()) {
                // 設置前に隣接ブロックの既存水源を記録（無限水源対策）
                Set<BlockFace> preExistingWater = new HashSet<>();
                for (BlockFace face : HORIZONTAL_FACES) {
                    if (block.getRelative(face).getType() == Material.WATER) {
                        preExistingWater.add(face);
                    }
                }

                block.setType(Material.WATER);
                spawnWaterFx(blockLocation);

                // 一定時間後に水を消滅させる
                int baseLifetime = (int) config.getParam("conjure_water", "base-water-lifetime", BASE_WATER_LIFETIME);
                int lifetimePerDuration = (int) config.getParam("conjure_water", "water-lifetime-per-duration", WATER_LIFETIME_PER_DURATION);
                int waterLifetime = baseLifetime + context.getDurationLevel() * lifetimePerDuration;
                Location waterLoc = block.getLocation();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Block b = waterLoc.getBlock();
                        if (b.getType() == Material.WATER) {
                            b.setType(Material.AIR);
                            // 無限水源メカニクスで生成された隣接水源を除去
                            // （設置前に既に存在していた水源は保持）
                            for (BlockFace face : HORIZONTAL_FACES) {
                                if (!preExistingWater.contains(face)) {
                                    Block adj = b.getRelative(face);
                                    if (adj.getType() == Material.WATER) {
                                        adj.setType(Material.AIR);
                                    }
                                }
                            }
                            spawnWaterFx(waterLoc);
                        }
                    }
                }.runTaskLater(plugin, waterLifetime);
            }
        }

        // 隣接する炎ブロックを消火
        extinguishAdjacentFire(block, caster);
    }

    /**
     * 指定ブロックに隣接する全方向の炎を消す。
     */
    private void extinguishAdjacentFire(Block center, Player caster) {
        BlockFace[] faces = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
        };
        for (BlockFace face : faces) {
            Block adjacent = center.getRelative(face);
            if (adjacent.getType() == Material.FIRE || adjacent.getType() == Material.SOUL_FIRE) {
                // BlockBreakEventで保護確認
                org.bukkit.event.block.BlockBreakEvent breakEvent =
                    new org.bukkit.event.block.BlockBreakEvent(adjacent, caster);
                Bukkit.getPluginManager().callEvent(breakEvent);
                if (!breakEvent.isCancelled()) {
                    adjacent.setType(Material.AIR);
                }
            }
        }
    }

    private void spawnWaterFx(Location loc) {
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.SPLASH, loc.clone().add(0, 1, 0),
            25, 0.4, 0.3, 0.4, 0.3);
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.BUBBLE_COLUMN_UP, loc.clone().add(0, 0.5, 0),
            10, 0.3, 0.2, 0.3, 0.1);
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.ITEM_BUCKET_EMPTY, org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "水生成"; }

    @Override
    public String getDescription() { return "水を生成し、炎を消す"; }

    @Override
    public int getManaCost() { return config.getManaCost("conjure_water"); }

    @Override
    public int getTier() { return config.getTier("conjure_water"); }

    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_OUTWARD; }
}
