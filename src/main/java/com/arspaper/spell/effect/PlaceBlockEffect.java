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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * インベントリからブロックを設置するEffect。
 * オフハンド → ホットバー右端(8)から左端(0) の優先順でブロックアイテムを探索。
 * BlockPlaceEvent を発火して保護プラグイン互換を確保する。
 * 設置後、インベントリから1個消費する。
 */
public class PlaceBlockEffect implements SpellEffect {

    private static final Set<Material> NON_PLACEABLE_OVERRIDES = Set.of(
        Material.AIR, Material.CAVE_AIR, Material.VOID_AIR
    );

    private final NamespacedKey id;
    private final GlyphConfig config;

    public PlaceBlockEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "place_block");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティ対象: 足元の空気ブロックに設置を試みる
        applyToBlock(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        // 対象位置が空気でなければ、上のブロックを試す（投射形態対応）
        if (!block.getType().isAir()) {
            Block above = block.getRelative(BlockFace.UP);
            if (above.getType().isAir()) {
                block = above;
                blockLocation = block.getLocation();
            } else {
                return;
            }
        }

        Player caster = context.getCaster();
        if (caster == null) return;

        // オフハンド → ホットバー右端(8)から左端(0)の優先順で探索
        PlayerInventory inv = caster.getInventory();
        int foundSlot = -1;
        ItemStack foundItem = null;

        // オフハンドを先にチェック
        ItemStack offhand = inv.getItemInOffHand();
        if (!offhand.getType().isAir() && offhand.getType().isBlock()
            && !NON_PLACEABLE_OVERRIDES.contains(offhand.getType())) {
            foundSlot = 40;
            foundItem = offhand.clone();
        }

        // ホットバー右端(8)から左端(0)
        if (foundItem == null) {
            for (int slot = 8; slot >= 0; slot--) {
                ItemStack item = inv.getItem(slot);
                if (item != null && !item.getType().isAir() && item.getType().isBlock()
                    && !NON_PLACEABLE_OVERRIDES.contains(item.getType())) {
                    foundSlot = slot;
                    foundItem = item.clone();
                    break;
                }
            }
        }

        if (foundItem == null) return;

        java.util.List<String> namespaces = new java.util.ArrayList<>();
        if (foundItem.hasItemMeta()) {
            for (NamespacedKey key : foundItem.getItemMeta().getPersistentDataContainer().getKeys()) {
                namespaces.add(key.getNamespace());
            }
        }
        if (PlaceBlockPolicy.refuseVanillaPlaceholder(foundItem.getType(), namespaces)) {
            // ドロワー等: setType すると基材の空コンテナになり中身が消える。復元できないので置かない。
            caster.sendMessage(net.kyori.adventure.text.Component.text(
                    "このブロックは設置魔法では置けません（中身が消えるため）。手で設置してください。",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        // 先に設置してから BlockPlaceEvent を飛ばす。イベント→setType だと、ドロワー側が
        // イベント中に書いた TileEntity を直後の setType が空の樽で上書きする。
        // 向きの setBlockData はインベントリ転写より前。後だと中身と CustomBlockListener の TileState が消える。
        org.bukkit.block.BlockState replaced = block.getState();
        block.setType(foundItem.getType());
        org.bukkit.inventory.meta.BlockStateMeta blockMeta = foundItem.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta m
                ? m : null;
        if (blockMeta != null) {
            block.setBlockData(blockMeta.getBlockState().getBlockData(), false);
        }
        applyPlacementFacing(block, context, caster);
        if (blockMeta != null) {
            org.bukkit.block.BlockState itemState = blockMeta.getBlockState();
            if (itemState instanceof org.bukkit.inventory.InventoryHolder fromHolder
                    && block.getState() instanceof org.bukkit.inventory.InventoryHolder toHolder) {
                toHolder.getInventory().setContents(fromHolder.getInventory().getContents());
                ((org.bukkit.block.BlockState) toHolder).update(true, false);
            }
        }

        Block blockBelow = block.getRelative(BlockFace.DOWN);
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block,
            replaced,
            blockBelow,
            foundItem,
            caster,
            true,
            EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            replaced.update(true, false);
            return;
        }

        // インベントリから1個消費
        ItemStack current = inv.getItem(foundSlot);
        if (current != null) {
            if (current.getAmount() <= 1) {
                inv.setItem(foundSlot, null);
            } else {
                current.setAmount(current.getAmount() - 1);
            }
        }

        // 設置エフェクト
        blockLocation.getWorld().spawnParticle(
            org.bukkit.Particle.BLOCK,
            blockLocation.clone().add(0.5, 0.5, 0.5),
            12, 0.3, 0.3, 0.3, 0,
            foundItem.getType().createBlockData()
        );
        blockLocation.getWorld().playSound(blockLocation,
            org.bukkit.Sound.BLOCK_STONE_PLACE,
            org.bukkit.SoundCategory.BLOCKS, 0.8f, 1.0f);
    }

    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_OUTWARD; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "設置"; }

    @Override
    public String getDescription() { return "ブロックを設置する"; }

    @Override
    public int getManaCost() { return config.getManaCost("place_block"); }

    @Override
    public int getTier() { return config.getTier("place_block"); }

    /**
     * 向きは {@link BlockPlaceEvent} より前に決める。イベント後に {@code setBlockData} すると、
     * {@code CustomBlockListener} が書いた TileState が空のバニラブロックに戻る。
     */
    private static void applyPlacementFacing(Block block, SpellContext context, Player caster) {
        org.bukkit.block.data.BlockData blockData = block.getBlockData();
        if (!(blockData instanceof org.bukkit.block.data.Directional directional)) {
            return;
        }
        org.bukkit.block.BlockFace placeFace = context.getHitFace();
        if (placeFace == null) {
            org.bukkit.util.Vector look = caster.getLocation().getDirection().setY(0);
            if (look.lengthSquared() > 0.01) {
                look.normalize();
                double absX = Math.abs(look.getX());
                double absZ = Math.abs(look.getZ());
                if (absX > absZ) {
                    placeFace = look.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
                } else {
                    placeFace = look.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
                }
            }
        }
        if (placeFace != null && directional.getFaces().contains(placeFace)) {
            directional.setFacing(placeFace);
            block.setBlockData(directional);
        }
    }
}
