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

/**
 * 対象を燃焼させるEffect。
 * エンティティ: 炎上付与（基礎3秒 + Amplify毎+2秒）。
 * ブロック: 上面が空気なら火を設置。
 */
public class IgniteEffect implements SpellEffect {

    private static final int DEFAULT_BASE_FIRE_TICKS = 60; // 3秒
    private static final int DEFAULT_AMPLIFY_BONUS_TICKS = 40; // +2秒
    private static final int DEFAULT_DURATION_BONUS_TICKS = 20; // +1秒 per ExtendTime
    private final NamespacedKey id;
    private final GlyphConfig config;

    public IgniteEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "ignite");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseFireTicks = (int) config.getParam("ignite", "base-fire-ticks", DEFAULT_BASE_FIRE_TICKS);
        int amplifyBonusTicks = (int) config.getParam("ignite", "amplify-bonus-ticks", DEFAULT_AMPLIFY_BONUS_TICKS);
        int durationBonusTicks = (int) config.getParam("ignite", "duration-bonus-ticks", DEFAULT_DURATION_BONUS_TICKS);
        int ticks = baseFireTicks + context.getAmplifyLevel() * amplifyBonusTicks
            + context.getDurationLevel() * durationBonusTicks;
        target.setFireTicks(Math.max(1, ticks));
        SpellFxUtil.spawnIgniteFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Block above = block.getRelative(BlockFace.UP);
        if (above.isEmpty()) {
            org.bukkit.entity.Player caster = context.getCaster();
            if (caster == null) return;

            // 保護プラグイン互換: BlockPlaceEventを発火して許可を確認
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                above,
                above.getState(),
                block,
                new ItemStack(Material.FIRE),
                caster,
                true,
                EquipmentSlot.HAND
            );
            org.bukkit.Bukkit.getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled()) return;

            above.setType(Material.FIRE);
            SpellFxUtil.spawnIgniteFx(above.getLocation());
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "着火"; }

    @Override
    public String getDescription() { return "対象を炎上させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("ignite"); }

    @Override
    public int getTier() { return config.getTier("ignite"); }
}
