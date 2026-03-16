package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象ブロックにレッドストーン信号を与えるEffect。
 *
 * レッドストーンパウダー: 下のブロックをpowered状態にして間接通電
 * Powerable (レバー等): powered = true
 * Openable (ドア等): open = true
 * Lightable (ランプ等): lit = true
 * AnaloguePowerable (その他): power設定
 * その他のブロック: 下のブロックをpowered状態にする
 *
 * Amplify: 持続時間延長
 * ExtendTime/DurationDown: 持続時間を調整
 */
public class RedstoneSignalEffect implements SpellEffect {

    private static final long BASE_DURATION_TICKS = 40L;
    private static final long DURATION_PER_LEVEL_TICKS = 20L;

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public RedstoneSignalEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "redstone_signal");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        applyToBlock(context, target.getLocation().subtract(0, 1, 0).getBlock().getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        int amplify = Math.max(0, context.getAmplifyLevel());
        long durationTicks = BASE_DURATION_TICKS
            + context.getDurationLevel() * DURATION_PER_LEVEL_TICKS
            + amplify * 10L;

        BlockData data = block.getBlockData();
        BlockData originalData = data.clone();
        boolean modified = false;

        if (block.getType() == Material.REDSTONE_WIRE) {
            // パウダー: 下のブロックをpowered状態にして間接通電
            modified = powerBlockBelow(block, durationTicks);
        } else if (data instanceof Powerable powerable) {
            powerable.setPowered(true);
            block.setBlockData(powerable, true);
            modified = true;
            scheduleRestore(block, originalData, durationTicks);
        } else if (data instanceof Openable openable) {
            openable.setOpen(true);
            block.setBlockData(openable, true);
            modified = true;
            scheduleRestore(block, originalData, durationTicks);
        } else if (data instanceof Lightable lightable) {
            lightable.setLit(true);
            block.setBlockData(lightable, true);
            modified = true;
            scheduleRestore(block, originalData, durationTicks);
        } else if (data instanceof AnaloguePowerable analoguePowerable) {
            int power = Math.min(analoguePowerable.getMaximumPower(), 1 + amplify * 4);
            analoguePowerable.setPower(power);
            block.setBlockData(analoguePowerable, true);
            modified = true;
            scheduleRestore(block, originalData, durationTicks);
        } else {
            // その他のブロック: 下のブロックをpowered状態にする
            modified = powerBlockBelow(block, durationTicks);
        }

        if (modified) {
            spawnSignalFx(blockLocation);
        }
    }

    /**
     * 対象ブロックの下にあるソリッドブロックにレバーを一時設置してpowered状態にする。
     * これによりパウダーや隣接ブロックが自然に通電する。
     */
    private boolean powerBlockBelow(Block target, long durationTicks) {
        // 下のブロックがソリッドか確認
        Block below = target.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid()) return false;

        // 隣接する空気ブロックを探してレバーを一時設置
        BlockFace[] faces = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };
        for (BlockFace face : faces) {
            Block adjacent = below.getRelative(face);
            if (adjacent.getType().isAir()) {
                // 一時的なレバーを設置（powered状態）
                BlockData leverData = Material.LEVER.createBlockData();
                if (leverData instanceof Powerable powerable) {
                    powerable.setPowered(true);
                }
                if (leverData instanceof org.bukkit.block.data.type.Switch switchData) {
                    // レバーの向きを設定（壁付き）
                    switchData.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
                    switchData.setFacing(face.getOppositeFace());
                }
                adjacent.setBlockData(leverData, true);

                // 一定時間後にレバーを除去
                Location leverLoc = adjacent.getLocation();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Block b = leverLoc.getBlock();
                    if (b.getType() == Material.LEVER) {
                        b.setType(Material.AIR, true);
                    }
                }, durationTicks);
                return true;
            }
        }

        // 隣接に空気がない場合、上を試す
        Block above = target.getRelative(BlockFace.UP);
        if (above.getType().isAir() || above == target) {
            // 下ブロックの上面にレバー設置
            Block onTop = below.getRelative(BlockFace.UP);
            if (onTop.equals(target)) {
                // パウダー自体の位置にはレバーを置けないので別の方法を試す
                return false;
            }
        }

        return false;
    }

    private void scheduleRestore(Block block, BlockData originalData, long durationTicks) {
        Location savedLoc = block.getLocation();
        Material savedType = block.getType();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block current = savedLoc.getBlock();
            if (current.getType() == savedType) {
                current.setBlockData(originalData, true);
            }
        }, durationTicks);
    }

    private void spawnSignalFx(Location loc) {
        Location effectLoc = loc.clone().add(0.5, 0.5, 0.5);
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.DUST, effectLoc, 12, 0.3, 0.3, 0.3, 0,
            new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.BLOCK_REDSTONE_TORCH_BURNOUT,
            org.bukkit.SoundCategory.BLOCKS, 0.6f, 1.0f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "信号"; }

    @Override
    public String getDescription() { return "レッドストーン信号を与える"; }

    @Override
    public int getManaCost() { return config.getManaCost("redstone_signal"); }

    @Override
    public int getTier() { return config.getTier("redstone_signal"); }
}
