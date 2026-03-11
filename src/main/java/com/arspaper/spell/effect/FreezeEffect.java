package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象を凍結させるEffect。Ars NouveauのEffectFreezeに準拠。
 * エンティティ: 移動速度低下 + freezeTicks付与。
 * ブロック: 水→氷、氷→氷塊、氷塊→青氷、溶岩→黒曜石に変換。
 */
public class FreezeEffect implements SpellEffect {

    private static final int BASE_DURATION = 100; // 5秒
    private final NamespacedKey id;

    public FreezeEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "freeze");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int duration = BASE_DURATION + context.getDurationTicks();
        int amplifier = context.getAmplifyLevel();

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier + 1));
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
            block.setType(newType);
            SpellFxUtil.spawnFreezeFx(blockLocation);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Freeze"; }

    @Override
    public int getManaCost() { return 15; }

    @Override
    public int getTier() { return 2; }
}
