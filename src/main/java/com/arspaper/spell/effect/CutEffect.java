package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * ハサミ使用をシミュレートするEffect。
 * エンティティ: 羊/キノコ牛などを刈る（Shearable mob）。
 * ブロック: 葉・ツタ・クモの巣・羊毛類をハサミで採取。
 * Amplify付き: 斧での伐採をシミュレート（原木の樹皮剥き）。
 */
public class CutEffect implements SpellEffect {

    /** ハサミで採取可能なブロック素材（葉・ツタ・クモの巣・羊毛） */
    private static final Set<Material> SHEAR_BLOCKS = Set.of(
        // 全種類の葉
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
        Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
        Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES,
        Material.FLOWERING_AZALEA_LEAVES,
        // ツタ類
        Material.VINE, Material.GLOW_LICHEN, Material.HANGING_ROOTS,
        // クモの巣
        Material.COBWEB,
        // 羊毛
        Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL,
        Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
        Material.PINK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL,
        Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
        Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL,
        Material.BLACK_WOOL
    );

    /** 斧で樹皮を剥けるログ→剥ぎ取り後のマッピング */
    private static final java.util.Map<Material, Material> STRIP_LOG_MAP;
    static {
        var map = new java.util.EnumMap<Material, Material>(Material.class);
        map.put(Material.OAK_LOG, Material.STRIPPED_OAK_LOG);
        map.put(Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG);
        map.put(Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG);
        map.put(Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG);
        map.put(Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG);
        map.put(Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG);
        map.put(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG);
        map.put(Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG);
        map.put(Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM);
        map.put(Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM);
        map.put(Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD);
        map.put(Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD);
        map.put(Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD);
        map.put(Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD);
        map.put(Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD);
        map.put(Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD);
        map.put(Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD);
        map.put(Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD);
        map.put(Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE);
        map.put(Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE);
        STRIP_LOG_MAP = java.util.Collections.unmodifiableMap(map);
    }

    private final NamespacedKey id;
    private final GlyphConfig config;

    public CutEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "cut");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // 羊→刈り取り
        if (target instanceof Sheep sheep) {
            if (!sheep.isSheared()) {
                sheep.setSheared(true);
                // 羊毛ドロップをシミュレート
                target.getWorld().dropItemNaturally(
                    target.getLocation(),
                    new org.bukkit.inventory.ItemStack(getWoolMaterial(sheep.getColor()), 1)
                );
            }
            return;
        }
        // キノコ牛→キノコをドロップし通常牛に変換
        if (target instanceof MushroomCow mushroomCow) {
            Material mushroomType = mushroomCow.getVariant() == MushroomCow.Variant.BROWN
                ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
            for (int i = 0; i < 5; i++) {
                target.getWorld().dropItemNaturally(
                    target.getLocation(),
                    new org.bukkit.inventory.ItemStack(mushroomType, 1)
                );
            }
            mushroomCow.getWorld().spawn(mushroomCow.getLocation(), org.bukkit.entity.Cow.class);
            mushroomCow.remove();
        }
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        // Amplify付き: 斧での樹皮剥き
        if (context.getAmplifyLevel() > 0) {
            Material stripped = STRIP_LOG_MAP.get(block.getType());
            if (stripped != null) {
                BlockBreakEvent event = new BlockBreakEvent(block, caster);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    block.setType(stripped);
                }
            }
            return;
        }

        // 通常: ハサミ採取可能ブロックを破壊してドロップ
        if (SHEAR_BLOCKS.contains(block.getType())) {
            BlockBreakEvent event = new BlockBreakEvent(block, caster);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                block.breakNaturally(new org.bukkit.inventory.ItemStack(Material.SHEARS));
            }
        }
    }

    /** 羊の色から羊毛素材を返す */
    private Material getWoolMaterial(org.bukkit.DyeColor color) {
        if (color == null) return Material.WHITE_WOOL;
        return switch (color) {
            case ORANGE -> Material.ORANGE_WOOL;
            case MAGENTA -> Material.MAGENTA_WOOL;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_WOOL;
            case YELLOW -> Material.YELLOW_WOOL;
            case LIME -> Material.LIME_WOOL;
            case PINK -> Material.PINK_WOOL;
            case GRAY -> Material.GRAY_WOOL;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_WOOL;
            case CYAN -> Material.CYAN_WOOL;
            case PURPLE -> Material.PURPLE_WOOL;
            case BLUE -> Material.BLUE_WOOL;
            case BROWN -> Material.BROWN_WOOL;
            case GREEN -> Material.GREEN_WOOL;
            case RED -> Material.RED_WOOL;
            case BLACK -> Material.BLACK_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "剪断"; }

    @Override
    public String getDescription() { return "ハサミ使用をシミュレートする"; }

    @Override
    public int getManaCost() { return config.getManaCost("cut"); }

    @Override
    public int getTier() { return config.getTier("cut"); }
}
