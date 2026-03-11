package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * ブロックに光源を設置、エンティティに夜間視覚と発光を付与するEffect。
 * 保護プラグイン互換: BlockPlaceEventを発火して許可を確認する。
 */
public class LightEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 600; // 30秒
    private final NamespacedKey id;

    public LightEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "light");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int duration = BASE_DURATION_TICKS + context.getDurationTicks();
        int amplifier = context.getAmplifyLevel();
        target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, amplifier));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, amplifier));
        SpellFxUtil.spawnLightFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (!block.getType().isAir()) return;

        // 保護プラグイン互換: BlockPlaceEventを発火して許可を確認
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block,
            block.getState(),
            block.getRelative(org.bukkit.block.BlockFace.DOWN),
            new ItemStack(Material.LIGHT),
            context.getCaster(),
            true,
            EquipmentSlot.HAND
        );
        org.bukkit.Bukkit.getPluginManager().callEvent(placeEvent);

        if (!placeEvent.isCancelled()) {
            block.setType(Material.LIGHT);
            SpellFxUtil.spawnLightFx(blockLocation);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Light"; }

    @Override
    public int getManaCost() { return 5; }

    @Override
    public int getTier() { return 1; }
}
