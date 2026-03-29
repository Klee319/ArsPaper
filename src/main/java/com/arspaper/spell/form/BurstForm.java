package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * 炸裂フォーム - 時限式の飛翔体を発射する。
 *
 * ファイヤーチャージの見た目で直線軌道を飛行し、
 * エンティティへのヒットまたは時間経過で炸裂する。
 * 炸裂地点のブロックと範囲内エンティティの両方にエフェクトを適用。
 * 空中で炸裂した場合も空気ブロックを対象に取れる。
 * 半径増加はエンティティ対象範囲のみ拡大（ブロック対象は常に着弾点1ブロック）。
 *
 * 互換: 延伸, 半径増加, 加速, 減速, 貫通, 分裂
 */
public class BurstForm implements SpellForm {

    private static final String META_KEY = "ars_spell_context";
    private static final String META_BURST = "ars_burst_form";
    private static final double BASE_SPEED = 1.2;
    private static final int BASE_FUSE_TICKS = 40; // 2秒
    private static final double BASE_BURST_RADIUS = 2.0;
    private static final double SPREAD_ANGLE_STEP = 0.15;

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public BurstForm(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "burst");
        this.config = config;
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);

        double speed = BASE_SPEED * Math.min(context.getProjectileSpeedMultiplier(), 4.0)
            + context.getReachLevel() * 0.3;
        int totalProjectiles = 1 + Math.min(context.getSplitCount(), 8);
        int fuseTicks = BASE_FUSE_TICKS + context.getReachLevel() * 10;
        double burstRadius = BASE_BURST_RADIUS + context.getAoeRadiusLevel() * 1.5;

        Vector baseDirection = caster.getLocation().getDirection();

        for (int i = 0; i < totalProjectiles; i++) {
            Vector direction = baseDirection.clone();
            if (totalProjectiles > 1) {
                double angle = (i - (totalProjectiles - 1) / 2.0) * SPREAD_ANGLE_STEP;
                direction.rotateAroundY(angle);
            }

            SpellContext projectileContext = context.copy();
            Location handPos = caster.getEyeLocation().clone().add(0, -0.4, 0);
            final Vector finalDir = direction.clone().normalize().multiply(speed);

            // SmallFireball（ファイヤーチャージの見た目）
            SmallFireball fireball = caster.getWorld().spawn(handPos, SmallFireball.class, fb -> {
                fb.setShooter(caster);
                fb.setDirection(finalDir);
                fb.setVelocity(finalDir);
                fb.setIsIncendiary(false);  // 着火しない
                fb.setYield(0);             // 爆発しない
                fb.setGravity(false);
            });

            fireball.setMetadata(META_KEY, new FixedMetadataValue(plugin, projectileContext));
            fireball.setMetadata(META_BURST, new FixedMetadataValue(plugin, true));

            // 貫通設定（ProjectileHitListenerが処理）
            if (projectileContext.getPierceCount() > 0) {
                fireball.setMetadata("ars_pierce_remaining",
                    new FixedMetadataValue(plugin, projectileContext.getPierceCount()));
            }

            // 時限起爆タスク
            final double finalBurstRadius = burstRadius;
            new BukkitRunnable() {
                private int ticks = 0;

                @Override
                public void run() {
                    ticks++;

                    // 軌跡パーティクル
                    if (ticks % 2 == 0 && fireball.isValid() && !fireball.isDead()) {
                        Location loc = fireball.getLocation();
                        loc.getWorld().spawnParticle(Particle.FLAME, loc, 3, 0.1, 0.1, 0.1, 0.01);
                        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0.05, 0.05, 0.05, 0.01);
                    }

                    // ヒット済み（ProjectileHitListenerが処理した場合）
                    if (fireball.isDead() || !fireball.isValid()) {
                        cancel();
                        return;
                    }

                    // 時限起爆
                    if (ticks >= fuseTicks) {
                        cancel();
                        detonate(fireball.getLocation(), projectileContext, finalBurstRadius, caster);
                        fireball.remove();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    /**
     * 炸裂処理: 着弾点ブロックと範囲内エンティティの両方にエフェクト適用。
     * ブロック対象は常に着弾点1ブロック。エンティティ範囲はradius（半径増加で拡大）。
     */
    public static void detonate(Location center, SpellContext context, double radius, @org.jetbrains.annotations.Nullable Player caster) {
        // 炸裂エフェクト
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.3, 0.3, 0.3, 0.05);
        center.getWorld().spawnParticle(Particle.FLAME, center, 15, 0.5, 0.5, 0.5, 0.1);
        center.getWorld().spawnParticle(Particle.SMOKE, center, 10, 0.4, 0.4, 0.4, 0.05);
        center.getWorld().playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST,
            SoundCategory.PLAYERS, 1.0f, 0.8f);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE,
            SoundCategory.PLAYERS, 0.5f, 1.5f);

        // ブロック対象（空中でも対象に取れる）
        SpellContext blockCtx = context.copy();
        blockCtx.resolveOnBlock(center.getBlock().getLocation());

        // 範囲内エンティティ
        Collection<LivingEntity> nearby = center.getWorld().getNearbyEntitiesByType(
            LivingEntity.class, center, radius);
        for (LivingEntity entity : nearby) {
            if (entity == caster) continue; // 術者は除外
            SpellContext entityCtx = context.copy();
            entityCtx.resolveOnEntity(entity);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "炸裂"; }

    @Override
    public String getDescription() { return "時限式の火球を発射し、炸裂地点に効果を適用"; }

    @Override
    public int getManaCost() { return config.getManaCost("burst"); }

    @Override
    public int getTier() { return config.getTier("burst"); }
}
