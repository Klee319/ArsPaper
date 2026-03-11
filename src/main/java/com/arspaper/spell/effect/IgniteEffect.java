package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象を燃焼させるEffect。
 * エンティティ: 炎上付与（基礎3秒 + Amplify毎+2秒）。
 * ブロック: 上面が空気なら火を設置。
 */
public class IgniteEffect implements SpellEffect {

    private static final int BASE_FIRE_TICKS = 60; // 3秒
    private static final int AMPLIFY_BONUS_TICKS = 40; // +2秒
    private final NamespacedKey id;

    public IgniteEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "ignite");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int ticks = BASE_FIRE_TICKS + context.getAmplifyLevel() * AMPLIFY_BONUS_TICKS
            + context.getDurationTicks();
        target.setFireTicks(ticks);
        SpellFxUtil.spawnIgniteFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Block above = block.getRelative(BlockFace.UP);
        if (above.getType() == Material.AIR) {
            above.setType(Material.FIRE);
            SpellFxUtil.spawnIgniteFx(above.getLocation());
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Ignite"; }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }
}
