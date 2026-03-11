package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象に移動速度ブーストを付与するEffect。
 */
public class SpeedEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 400; // 20秒
    private final NamespacedKey id;

    public SpeedEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "speed");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int duration = BASE_DURATION_TICKS + context.getDurationTicks();
        int amplifier = context.getAmplifyLevel();
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
        SpellFxUtil.spawnSpeedFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // SpeedはブロックにはNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Speed"; }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }
}
