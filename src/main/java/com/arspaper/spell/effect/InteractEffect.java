package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * 右クリック操作をシミュレートするEffect。
 * ブロック: ドア・ボタン・レバー・トラップドア・門を操作する。
 * ExtendTime: ボタンの押下持続時間を延長（長押し扱い）。
 */
public class InteractEffect implements SpellEffect {

    private static final Set<Material> BUTTON_MATERIALS = Set.of(
        Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON,
        Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON,
        Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON, Material.CRIMSON_BUTTON,
        Material.WARPED_BUTTON, Material.STONE_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON,
        Material.BAMBOO_BUTTON
    );

    private static final int BASE_BUTTON_TICKS = 30;        // 1.5秒
    private static final int DURATION_BONUS_TICKS = 20;      // ExtendTimeあたり+1秒

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public InteractEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "interact");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティへの操作はNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        BlockData data = block.getBlockData();
        int durationLevel = context.getDurationLevel();

        // ドア・トラップドア・門: 開閉トグル
        if (data instanceof Openable openable) {
            openable.setOpen(!openable.isOpen());
            block.setBlockData(openable);
            playDoorSound(block, openable.isOpen());
            if (data instanceof org.bukkit.block.data.type.Door door) {
                toggleDoubleDoor(block, door);
            }
            return;
        }

        // ボタン: 押下（ExtendTimeで長押し持続延長）
        if (BUTTON_MATERIALS.contains(block.getType()) && data instanceof Powerable powerable) {
            powerable.setPowered(true);
            block.setBlockData(powerable);
            block.getWorld().playSound(block.getLocation(),
                org.bukkit.Sound.BLOCK_STONE_BUTTON_CLICK_ON,
                org.bukkit.SoundCategory.BLOCKS, 0.6f, 1.0f);

            int holdTicks = Math.max(1, BASE_BUTTON_TICKS + durationLevel * DURATION_BONUS_TICKS);
            Location savedLoc = block.getLocation();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block current = savedLoc.getBlock();
                BlockData currentData = current.getBlockData();
                if (currentData instanceof Powerable p) {
                    p.setPowered(false);
                    current.setBlockData(p);
                    current.getWorld().playSound(current.getLocation(),
                        org.bukkit.Sound.BLOCK_STONE_BUTTON_CLICK_OFF,
                        org.bukkit.SoundCategory.BLOCKS, 0.6f, 1.0f);
                }
            }, holdTicks);
            return;
        }

        // レバー: オン/オフトグル
        if (block.getType() == Material.LEVER && data instanceof Powerable powerable) {
            powerable.setPowered(!powerable.isPowered());
            block.setBlockData(powerable);
            block.getWorld().playSound(block.getLocation(),
                org.bukkit.Sound.BLOCK_LEVER_CLICK,
                org.bukkit.SoundCategory.BLOCKS, 0.6f, 1.0f);
        }
    }

    private void toggleDoubleDoor(Block block, org.bukkit.block.data.type.Door door) {
        Block otherHalf = door.getHalf() == Bisected.Half.BOTTOM
            ? block.getRelative(0, 1, 0)
            : block.getRelative(0, -1, 0);

        if (otherHalf.getType() != block.getType()) return;
        BlockData otherData = otherHalf.getBlockData();
        if (!(otherData instanceof Openable otherOpenable)) return;
        otherOpenable.setOpen(door.isOpen());
        otherHalf.setBlockData(otherOpenable);
    }

    private void playDoorSound(Block block, boolean opening) {
        Material type = block.getType();
        org.bukkit.Sound sound;
        if (type.name().contains("IRON")) {
            sound = opening
                ? org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN
                : org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE;
        } else if (type.name().contains("TRAPDOOR")) {
            sound = opening
                ? org.bukkit.Sound.BLOCK_WOODEN_TRAPDOOR_OPEN
                : org.bukkit.Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE;
        } else if (type.name().contains("GATE")) {
            sound = opening
                ? org.bukkit.Sound.BLOCK_FENCE_GATE_OPEN
                : org.bukkit.Sound.BLOCK_FENCE_GATE_CLOSE;
        } else {
            sound = opening
                ? org.bukkit.Sound.BLOCK_WOODEN_DOOR_OPEN
                : org.bukkit.Sound.BLOCK_WOODEN_DOOR_CLOSE;
        }
        block.getWorld().playSound(block.getLocation(), sound, org.bukkit.SoundCategory.BLOCKS, 0.8f, 1.0f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "操作"; }

    @Override
    public String getDescription() { return "遠隔で右クリック操作する"; }

    @Override
    public int getManaCost() { return config.getManaCost("interact"); }

    @Override
    public int getTier() { return config.getTier("interact"); }
}
