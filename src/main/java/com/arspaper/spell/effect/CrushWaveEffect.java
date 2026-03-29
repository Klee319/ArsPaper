package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 水に関連した物理ダメージを与えるEffect。
 * 基本ダメージ: 3.0 (ハート1.5個分)
 * 水中のエンティティには2倍ダメージ。
 *
 * 互換増強: 増幅/減衰（威力）、半径増加（ダメージエリア）、遅延、初期化のみ。
 */
public class CrushWaveEffect implements SpellEffect {

    private static final double DEFAULT_BASE_DAMAGE = 3.0;
    private static final double DEFAULT_AMPLIFY_BONUS = 2.0;
    private static final double WATER_MULTIPLIER = 2.0;

    private final NamespacedKey id;
    private final GlyphConfig config;

    public CrushWaveEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "crush_wave");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double baseDamage = config.getParam("crush_wave", "base-damage", DEFAULT_BASE_DAMAGE);
        double amplifyBonus = config.getParam("crush_wave", "amplify-bonus", DEFAULT_AMPLIFY_BONUS);
        double damage = Math.max(0, baseDamage + context.getAmplifyLevel() * amplifyBonus);

        // 水に触れている敵にダメージ2倍
        if (target.isInWater() || target.isInRain()) {
            damage *= config.getParam("crush_wave", "water-multiplier", WATER_MULTIPLIER);
        }

        Player caster = context.getCaster();
        damage = context.calculateSpellDamage(damage, target);
        target.damage(damage, caster);
        spawnCrushWaveFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnCrushWaveFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.SPLASH, effectLoc,
            25, 0.4, 0.5, 0.4, 0.3);
        effectLoc.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, effectLoc,
            10, 0.3, 0.4, 0.3, 0.1);
        effectLoc.getWorld().spawnParticle(Particle.BLOCK, effectLoc,
            15, 0.3, 0.3, 0.3, 0.1,
            Material.GRAVEL.createBlockData());
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_GENERIC_SPLASH,
            SoundCategory.PLAYERS, 1.0f, 0.7f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "砕波"; }

    @Override
    public String getDescription() { return "衝撃波ダメージ。水中の敵に2倍"; }

    @Override
    public int getManaCost() { return config.getManaCost("crush_wave"); }

    @Override
    public int getTier() { return config.getTier("crush_wave"); }
}
