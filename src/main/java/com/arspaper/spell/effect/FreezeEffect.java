package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象を凍結させるEffect。Ars NouveauのEffectFreezeに準拠。
 * エンティティ: 移動速度低下 + freezeTicks付与。
 * ブロック: 水→氷、氷→氷塊、氷塊→青氷、溶岩→黒曜石に変換。
 */
public class FreezeEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION = 100; // 5秒
    private static final int DEFAULT_DURATION_PER_LEVEL = 200; // 10秒/段
    private final NamespacedKey id;
    private final GlyphConfig config;

    public FreezeEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "freeze");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("freeze", "base-duration", DEFAULT_BASE_DURATION);
        int durationPerLevel = (int) config.getParam("freeze", "duration-per-level", DEFAULT_DURATION_PER_LEVEL);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;
        int baseLevel = (int) config.getParam("freeze", "base-slowness-level", 0.0);
        int amplifier = Math.max(0, baseLevel + context.getAmplifyLevel());

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
        target.setFreezeTicks(Math.min(target.getFreezeTicks() + duration, target.getMaxFreezeTicks()));

        SpellFxUtil.spawnFreezeFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Material newType = switch (block.getType()) {
            case WATER -> Material.ICE;
            case ICE -> Material.PACKED_ICE;
            case PACKED_ICE -> Material.BLUE_ICE;
            case LAVA -> Material.OBSIDIAN;
            default -> null;
        };

        if (newType != null) {
            org.bukkit.entity.Player caster = context.getCaster();
            if (caster == null) return;

            // 保護プラグイン互換: BlockPlaceEventを発火して許可を確認
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                block,
                block.getState(),
                block.getRelative(BlockFace.DOWN),
                new ItemStack(newType),
                caster,
                true,
                EquipmentSlot.HAND
            );
            org.bukkit.Bukkit.getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled()) return;

            block.setType(newType);
            SpellFxUtil.spawnFreezeFx(blockLocation);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "氷結"; }

    @Override
    public String getDescription() { return "対象を凍結させ、鈍化を付与する"; }

    @Override
    public int getManaCost() { return config.getManaCost("freeze"); }

    @Override
    public int getTier() { return config.getTier("freeze"); }
}
