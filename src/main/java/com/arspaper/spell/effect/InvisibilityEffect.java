package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象を透明にするEffect。Ars Nouveau準拠。
 * 基本持続: 600tick (30秒)
 * ExtendTime: +160tick/level (8秒)
 * DurationDown: -160tick/level (8秒減少)
 */
public class InvisibilityEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 600;  // 30秒
    private static final int DURATION_BONUS_TICKS = 160; // 8秒/level

    private final NamespacedKey id;
    private final GlyphConfig config;

    public InvisibilityEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "invisibility");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("invisibility", "base-duration-ticks", BASE_DURATION_TICKS);
        int durationBonus = (int) config.getParam("invisibility", "duration-bonus-ticks", DURATION_BONUS_TICKS);
        int durationTicks = Math.max(1,
            baseDuration + context.getDurationLevel() * durationBonus);

        target.addPotionEffect(
            new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0, false, true, true));

        spawnInvisibilityFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnInvisibilityFx(Location loc) {
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.WITCH, loc.clone().add(0, 1, 0),
            20, 0.4, 0.6, 0.4, 0.05);
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.ENCHANT, loc.clone().add(0, 1, 0),
            15, 0.5, 0.5, 0.5, 1.0);
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.ENTITY_ENDERMAN_AMBIENT, org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "透明化"; }

    @Override
    public String getDescription() { return "対象を透明にする"; }

    @Override
    public int getManaCost() { return config.getManaCost("invisibility"); }

    @Override
    public int getTier() { return config.getTier("invisibility"); }
}
