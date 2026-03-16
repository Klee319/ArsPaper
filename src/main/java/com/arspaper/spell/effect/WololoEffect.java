package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ブロックや羊の色を変えるEffect。Ars NouveauのEffectWololoに準拠。
 * エンティティ: 羊の毛色を次の色にサイクル
 * ブロック: 羊毛/コンクリート/テラコッタ/色付きガラス等を次の色に変換
 */
public class WololoEffect implements SpellEffect {

    private static final DyeColor[] DYE_COLORS = DyeColor.values();
    private final NamespacedKey id;
    private final GlyphConfig config;

    public WololoEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "wololo");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        if (!(target instanceof Sheep sheep)) return;

        DyeColor current = sheep.getColor();
        if (current == null) current = DyeColor.WHITE;

        int step = 1 + Math.max(0, context.getAmplifyLevel());
        int nextIndex = (current.ordinal() + step) % DYE_COLORS.length;
        sheep.setColor(DYE_COLORS[nextIndex]);

        spawnWololoFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Material current = block.getType();
        Material next = getNextColorVariant(current, 1 + Math.max(0, context.getAmplifyLevel()));
        if (next == null) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        // 保護プラグイン互換: BlockPlaceEventを発火して許可を確認
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block,
            block.getState(),
            block.getRelative(BlockFace.DOWN),
            new ItemStack(next),
            caster,
            true,
            EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        block.setType(next);
        spawnWololoFx(blockLocation.clone().add(0.5, 0.5, 0.5));
    }

    /**
     * 色付きブロックの次の色バリアントを返す。対応外のブロックはnullを返す。
     */
    private Material getNextColorVariant(Material material, int step) {
        String name = material.name();

        // 色プレフィックスを検出
        for (DyeColor color : DYE_COLORS) {
            String colorPrefix = dyeColorToPrefix(color);
            if (name.startsWith(colorPrefix + "_")) {
                String suffix = name.substring(colorPrefix.length() + 1);
                // 色付きブロック系統のサフィックスのみ対応
                if (!isColorableBlockSuffix(suffix)) continue;

                int nextIndex = (color.ordinal() + step) % DYE_COLORS.length;
                String nextPrefix = dyeColorToPrefix(DYE_COLORS[nextIndex]);
                String nextName = nextPrefix + "_" + suffix;
                Material nextMat = Material.matchMaterial(nextName);
                return nextMat;
            }
        }
        return null;
    }

    /**
     * 色変更可能なブロックのサフィックスかどうかを判定する。
     */
    private boolean isColorableBlockSuffix(String suffix) {
        return switch (suffix) {
            case "WOOL",
                 "CONCRETE",
                 "CONCRETE_POWDER",
                 "TERRACOTTA",
                 "STAINED_GLASS",
                 "STAINED_GLASS_PANE",
                 "CARPET",
                 "BED",
                 "BANNER",
                 "CANDLE",
                 "GLAZED_TERRACOTTA",
                 "SHULKER_BOX" -> true;
            default -> false;
        };
    }

    /**
     * DyeColorをMaterial名のプレフィックスに変換する。
     */
    private String dyeColorToPrefix(DyeColor color) {
        return switch (color) {
            case WHITE -> "WHITE";
            case ORANGE -> "ORANGE";
            case MAGENTA -> "MAGENTA";
            case LIGHT_BLUE -> "LIGHT_BLUE";
            case YELLOW -> "YELLOW";
            case LIME -> "LIME";
            case PINK -> "PINK";
            case GRAY -> "GRAY";
            case LIGHT_GRAY -> "LIGHT_GRAY";
            case CYAN -> "CYAN";
            case PURPLE -> "PURPLE";
            case BLUE -> "BLUE";
            case BROWN -> "BROWN";
            case GREEN -> "GREEN";
            case RED -> "RED";
            case BLACK -> "BLACK";
        };
    }

    private void spawnWololoFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.ENTITY_EFFECT, loc.clone().add(0, 1, 0),
            15, 0.4, 0.4, 0.4, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_PREPARE_WOLOLO,
            SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "ウォロロ"; }

    @Override
    public String getDescription() { return "ブロックや羊の色を変える"; }

    @Override
    public int getManaCost() { return config.getManaCost("wololo"); }

    @Override
    public int getTier() { return config.getTier("wololo"); }
}
