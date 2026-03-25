package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

/**
 * 凍結・鈍化・水濡れ状態のエンティティに爆発ダメージを与えるEffect。Ars Nouveau準拠。
 * エンティティがSlowness、凍結中（freezeTicks > 0）、または水中にある場合のみ発動する。
 * ダメージ: 6.0 + 2.5 * min(amp, 2)
 * 氷パーティクルを生成する。
 */
public class ColdSnapEffect implements SpellEffect {

    private static final double BASE_DAMAGE = 6.0;
    private static final double AMPLIFY_BONUS = 2.5;
    private static final int MAX_AMP = 2;

    private final NamespacedKey id;
    private final GlyphConfig config;

    public ColdSnapEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "cold_snap");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // 凍結・鈍化・水濡れ条件チェック
        boolean isFrozen    = target.getFreezeTicks() > 0;
        boolean isSlowed    = target.hasPotionEffect(PotionEffectType.SLOWNESS);
        boolean isWet       = target.isInWater() || target.isInRain();

        if (!isFrozen && !isSlowed && !isWet) {
            // 条件を満たさない場合はNoOp
            return;
        }

        double baseDamage = config.getParam("cold_snap", "base-damage", BASE_DAMAGE);
        int maxAmp = (int) config.getParam("cold_snap", "max-amp", (double) MAX_AMP);
        int clampedAmp = Math.min(Math.max(0, context.getAmplifyLevel()), maxAmp);
        double damage = Math.max(0, baseDamage + clampedAmp * AMPLIFY_BONUS);

        Player caster = context.getCaster();
        damage = context.calculateSpellDamage(damage, target);
        target.damage(damage, caster);

        spawnColdSnapFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // 氷系ブロックをクラックさせる演出（ブロック変換はなし）
        spawnColdSnapFx(blockLocation);
    }

    private void spawnColdSnapFx(Location loc) {
        // 氷パーティクル
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.SNOWFLAKE, loc.clone().add(0, 1, 0),
            30, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.BLOCK, loc.clone().add(0, 0.5, 0),
            15, 0.4, 0.4, 0.4, 0.05,
            Material.BLUE_ICE.createBlockData());
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.8f);
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.BLOCK_GLASS_BREAK, org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.5f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "凍裂"; }

    @Override
    public String getDescription() { return "凍結・鈍化中の対象に爆発ダメージ"; }

    @Override
    public int getManaCost() { return config.getManaCost("cold_snap"); }

    @Override
    public int getTier() { return config.getTier("cold_snap"); }
}
