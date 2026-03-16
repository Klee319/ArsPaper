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
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 対象ブロックや近くのドロップアイテムを精錬するEffect。Ars Nouveau準拠。
 * ブロック: ハードコードされた精錬マップに基づきブロックを変換する。
 * Sensitive: ドロップアイテムのみを対象にする。
 */
public class SmeltEffect implements SpellEffect {

    /** ブロック精錬マップ: 精錬前 → 精錬後 */
    private static final Map<Material, Material> SMELT_MAP = new HashMap<>();

    static {
        // 鉱石 → 素材（溶鉱炉出力に準拠）
        SMELT_MAP.put(Material.IRON_ORE,       Material.IRON_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT_MAP.put(Material.GOLD_ORE,       Material.GOLD_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT_MAP.put(Material.COPPER_ORE,     Material.COPPER_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT_MAP.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        // ブロック変換
        SMELT_MAP.put(Material.COBBLESTONE,    Material.STONE);
        SMELT_MAP.put(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);
        SMELT_MAP.put(Material.STONE,          Material.SMOOTH_STONE);
        SMELT_MAP.put(Material.SANDSTONE,      Material.SMOOTH_SANDSTONE);
        SMELT_MAP.put(Material.RED_SANDSTONE,  Material.SMOOTH_RED_SANDSTONE);
        SMELT_MAP.put(Material.QUARTZ_BLOCK,   Material.SMOOTH_QUARTZ);
        SMELT_MAP.put(Material.SAND,           Material.GLASS);
        SMELT_MAP.put(Material.RED_SAND,       Material.GLASS);
        SMELT_MAP.put(Material.CLAY,           Material.TERRACOTTA);
        SMELT_MAP.put(Material.NETHERRACK,     Material.NETHER_BRICK);
        SMELT_MAP.put(Material.BASALT,         Material.SMOOTH_BASALT);
        SMELT_MAP.put(Material.CACTUS,         Material.GREEN_DYE);
        // 食料（調理）
        SMELT_MAP.put(Material.BEEF,           Material.COOKED_BEEF);
        SMELT_MAP.put(Material.CHICKEN,        Material.COOKED_CHICKEN);
        SMELT_MAP.put(Material.PORKCHOP,       Material.COOKED_PORKCHOP);
        SMELT_MAP.put(Material.MUTTON,         Material.COOKED_MUTTON);
        SMELT_MAP.put(Material.RABBIT,         Material.COOKED_RABBIT);
        SMELT_MAP.put(Material.COD,            Material.COOKED_COD);
        SMELT_MAP.put(Material.SALMON,         Material.COOKED_SALMON);
        SMELT_MAP.put(Material.POTATO,         Material.BAKED_POTATO);
        // 木材 → 木炭
        SMELT_MAP.put(Material.OAK_LOG,        Material.CHARCOAL);
        SMELT_MAP.put(Material.SPRUCE_LOG,     Material.CHARCOAL);
        SMELT_MAP.put(Material.BIRCH_LOG,      Material.CHARCOAL);
        SMELT_MAP.put(Material.JUNGLE_LOG,     Material.CHARCOAL);
        SMELT_MAP.put(Material.ACACIA_LOG,     Material.CHARCOAL);
        SMELT_MAP.put(Material.DARK_OAK_LOG,   Material.CHARCOAL);
        SMELT_MAP.put(Material.MANGROVE_LOG,   Material.CHARCOAL);
        SMELT_MAP.put(Material.CHERRY_LOG,     Material.CHARCOAL);
    }

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SmeltEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "smelt");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティには直接作用しないが、周囲のドロップアイテムを精錬する
        smeltNearbyItems(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック精錬 + 近くのドロップアイテムも精錬
        smeltNearbyItems(blockLocation);

        Block block = blockLocation.getBlock();
        Material result = SMELT_MAP.get(block.getType());
        if (result == null) return;

        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;

        // 結果がアイテムの場合（鉱石→インゴット等）はドロップとして出力
        if (!result.isBlock()) {
            // BlockBreakEventで保護確認してからブロックを空気に置換
            org.bukkit.event.block.BlockBreakEvent breakEvent =
                new org.bukkit.event.block.BlockBreakEvent(block, caster);
            Bukkit.getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled()) return;

            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(
                blockLocation.clone().add(0.5, 0.5, 0.5), new ItemStack(result));
            spawnSmeltFx(blockLocation);
            return;
        }

        // 結果もブロックの場合はBlackPlaceEventで保護確認してからブロック変換
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
        spawnSmeltFx(blockLocation);
    }

    /**
     * 指定位置の半径2ブロック以内にあるドロップアイテムを精錬する。
     */
    private void smeltNearbyItems(Location center) {
        Collection<Item> items = center.getWorld().getNearbyEntitiesByType(
            Item.class, center, 2.0);
        for (Item item : items) {
            ItemStack stack = item.getItemStack();
            Material smelted = SMELT_MAP.get(stack.getType());
            if (smelted != null) {
                item.setItemStack(new ItemStack(smelted, stack.getAmount()));
                spawnSmeltFx(item.getLocation());
            }
        }
    }

    private void spawnSmeltFx(Location loc) {
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 12, 0.2, 0.3, 0.2, 0.04);
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.SMOKE, loc.clone().add(0.5, 0.8, 0.5), 8, 0.2, 0.2, 0.2, 0.02);
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.BLOCK_FURNACE_FIRE_CRACKLE, org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "精錬"; }

    @Override
    public String getDescription() { return "対象を精錬する"; }

    @Override
    public int getManaCost() { return config.getManaCost("smelt"); }

    @Override
    public int getTier() { return config.getTier("smelt"); }
}
