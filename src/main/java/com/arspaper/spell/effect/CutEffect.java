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
 * AOE: 範囲カット。
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

    private final NamespacedKey id;
    private final GlyphConfig config;

    public CutEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "cut");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // 羊→刈り取り（子羊は不可、バニラ準拠）
        if (target instanceof Sheep sheep) {
            if (sheep.isAdult() && !sheep.isSheared()) {
                sheep.setSheared(true);
                target.getWorld().dropItemNaturally(
                    target.getLocation(),
                    new org.bukkit.inventory.ItemStack(getWoolMaterial(sheep.getColor()), 1)
                );
            }
            return;
        }
        // キノコ牛→キノコをドロップし通常牛に変換（子牛は不可、バニラ準拠）
        if (target instanceof MushroomCow mushroomCow && mushroomCow.isAdult()) {
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

        // ハサミ採取可能ブロックを破壊してドロップ
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
