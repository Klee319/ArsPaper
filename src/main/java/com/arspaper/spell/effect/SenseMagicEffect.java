package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象に魔力感知効果（グロー＋暗視）を付与するEffect。
 * Ars Nouveau Tier 2準拠:
 *   - GLOWINGポーション効果で魔法的エンティティが発光
 *   - NIGHT_VISIONで短時間の暗視を付与
 *   - 基本持続: 600 tick (30秒) + durationLevel × 160 tick (8秒)
 */
public class SenseMagicEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 600;
    private static final int DURATION_PER_LEVEL_TICKS = 160;
    private static final int NIGHT_VISION_DURATION_TICKS = 400; // 20秒固定

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SenseMagicEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "sense_magic");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int durationLevel = context.getDurationLevel();
        int amplifyLevel = Math.max(0, context.getAmplifyLevel());

        int durationTicks = Math.max(1, BASE_DURATION_TICKS + durationLevel * DURATION_PER_LEVEL_TICKS);

        // GLOWING効果を付与（魔力感知の核心）
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.GLOWING, durationTicks, amplifyLevel, false, true, true));

        // NIGHT_VISION を付与（短時間の暗視）
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.NIGHT_VISION, NIGHT_VISION_DURATION_TICKS + durationLevel * DURATION_PER_LEVEL_TICKS,
            0, false, true, true));

        spawnSenseMagicFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnSenseMagicFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 12, 0.3, 0.5, 0.3, 0.05);
        effectLoc.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 8, 0.4, 0.4, 0.4, 0.5);
        effectLoc.getWorld().playSound(effectLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
            SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "魔力感知"; }

    @Override
    public String getDescription() { return "魔法を感知し暗視を得る"; }

    @Override
    public int getManaCost() { return config.getManaCost("sense_magic"); }

    @Override
    public int getTier() { return config.getTier("sense_magic"); }
}
