package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 水中での呼吸と視界を確保するEffect。Ars NouveauのEffectBubbleに準拠。
 * エンティティ: WATER_BREATHING + CONDUIT_POWER付与
 * ブロック: NoOp
 */
public class BubbleEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 600;       // 30秒
    private static final int DURATION_PER_LEVEL = 160;        // ExtendTimeごと +8秒
    private final NamespacedKey id;
    private final GlyphConfig config;

    public BubbleEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "bubble");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int duration = Math.max(1, BASE_DURATION_TICKS + context.getDurationLevel() * DURATION_PER_LEVEL);
        int amplifier = Math.max(0, context.getAmplifyLevel());

        target.addPotionEffect(new PotionEffect(
            PotionEffectType.WATER_BREATHING, duration, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.CONDUIT_POWER, duration, amplifier, false, true, true));

        spawnBubbleFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnBubbleFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0, 1, 0),
            20, 0.4, 0.5, 0.4, 0.05);
        loc.getWorld().spawnParticle(Particle.DOLPHIN, loc.clone().add(0, 1, 0),
            10, 0.3, 0.3, 0.3, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_DOLPHIN_SPLASH,
            SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "泡"; }

    @Override
    public String getDescription() { return "水中での呼吸と視界を確保する"; }

    @Override
    public int getManaCost() { return config.getManaCost("bubble"); }

    @Override
    public int getTier() { return config.getTier("bubble"); }
}
