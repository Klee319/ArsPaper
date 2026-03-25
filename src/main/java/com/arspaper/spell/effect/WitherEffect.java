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
 * 対象にWitherポーション効果を付与するEffect。Ars Nouveau Tier 3, mana 100相当。
 * - 持続時間: base 600 ticks (30秒) + durationLevel * 160 ticks (8秒)
 * - amplifierLevel をそのまま適用（最大4スタック）
 */
public class WitherEffect implements SpellEffect {

    private static final int BASE_DURATION = 600;          // 30秒
    private static final int DURATION_PER_LEVEL = 160;     // ExtendTimeごと +8秒
    private static final int MAX_AMPLIFIER = 4;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public WitherEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "wither");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("wither", "base-duration", (double) BASE_DURATION);
        int durationPerLevel = (int) config.getParam("wither", "duration-per-level", (double) DURATION_PER_LEVEL);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;
        int maxAmp = (int) config.getParam("wither", "max-amplifier", (double) MAX_AMPLIFIER);
        int amplifier = Math.min(Math.max(0, context.getAmplifyLevel()), maxAmp);

        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, amplifier));

        Location loc = target.getLocation().add(0, 1, 0);
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 15, 0.3, 0.5, 0.3, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "衰弱"; }

    @Override
    public String getDescription() { return "対象にウィザー効果を付与する"; }

    @Override
    public int getManaCost() { return config.getManaCost("wither"); }

    @Override
    public int getTier() { return config.getTier("wither"); }
}
