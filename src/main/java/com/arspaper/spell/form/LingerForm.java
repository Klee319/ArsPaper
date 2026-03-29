package com.arspaper.spell.form;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * 飛び道具を発射し、着弾点に持続効果領域を生成するForm。
 * 領域内のエンティティに定期的にEffectチェーンを適用する。
 */
public class LingerForm implements SpellForm {

    private static final double ZONE_RADIUS = 3.0;
    private static final int BASE_DURATION_TICKS = 100;        // 5秒
    private static final int DURATION_BONUS_PER_LEVEL = 60;     // durationLevelあたり+3秒
    private static final int ZONE_TICK_INTERVAL = 20;           // 1秒ごとに効果適用
    private static final int MAX_TRAIL_TICKS = 200;
    private static final int RING_PARTICLE_COUNT = 16;

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public LingerForm(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "linger");
        this.config = config;
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);
        // applyFormAugments()はSpellCaster.cast()で既に呼び出し済み

        double speed = 2.0 * Math.min(context.getProjectileSpeedMultiplier(), 4.0);
        Vector direction = caster.getLocation().getDirection();

        // Snowball発射 — ProjectileHitListenerで着弾を処理
        // ars_spell_context を設定してProjectileHitListenerが認識できるようにする
        // ars_linger_form メタデータで Linger zone 生成を指示する
        SpellContext projectileContext = context.copy();
        Snowball projectile = caster.launchProjectile(Snowball.class, direction.multiply(speed));
        projectile.setMetadata("ars_spell_context", new FixedMetadataValue(plugin, projectileContext));
        projectile.setMetadata("ars_linger_form", new FixedMetadataValue(plugin, true));
        projectile.setGlowing(true);

        // 軌跡パーティクル
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > MAX_TRAIL_TICKS || projectile.isDead() || !projectile.isValid()) {
                    cancel();
                    return;
                }
                SpellFxUtil.spawnProjectileTrail(projectile.getLocation());
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * 着弾地点に持続効果領域を生成する。
     * ProjectileHitListenerから呼び出し可能。
     */
    public void startLingerZone(Player caster, SpellContext context, Location center) {
        int durationLevel = context.getDurationLevel();
        int baseDurationTicks = (int) config.getParam("linger", "base-duration-ticks", (double) BASE_DURATION_TICKS);
        int durationBonusPerLevel = (int) config.getParam("linger", "duration-bonus-per-level", (double) DURATION_BONUS_PER_LEVEL);
        int zoneTickInterval = (int) config.getParam("linger", "zone-tick-interval", (double) ZONE_TICK_INTERVAL);
        double zoneRadius = config.getParam("linger", "zone-radius", ZONE_RADIUS);
        int totalDuration = Math.max(1, baseDurationTicks + durationLevel * durationBonusPerLevel);

        new BukkitRunnable() {
            private int elapsed = 0;

            @Override
            public void run() {
                elapsed += zoneTickInterval;

                if (elapsed > totalDuration || !caster.isOnline()) {
                    cancel();
                    return;
                }

                // 領域パーティクル: リング状のWITCH + 中心にENCHANTED_HIT
                spawnZoneParticles(center, zoneRadius);

                // 領域内エンティティに効果適用（PvPチェック）
                // 本家準拠: キャスター自身にも効果を適用する（回復系Lingerで自己回復可能）
                for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, zoneRadius)) {
                    if (!entity.equals(caster) && !context.isValidAoeTarget(entity, caster)) continue;
                    SpellContext tickContext = context.copy();
                    tickContext.resolveOnEntityNoAoe(entity);
                }

                // 中心ブロックに効果適用
                SpellContext blockContext = context.copy();
                blockContext.resolveOnBlock(center.getBlock().getLocation());
            }
        }.runTaskTimer(plugin, 0L, zoneTickInterval);
    }

    /**
     * 領域のリング状パーティクルを描画する。
     */
    private static void spawnZoneParticles(Location center, double zoneRadius) {
        center.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center, 5, 0.2, 0.2, 0.2, 0.3);

        for (int i = 0; i < RING_PARTICLE_COUNT; i++) {
            double angle = (2.0 * Math.PI / RING_PARTICLE_COUNT) * i;
            double x = Math.cos(angle) * zoneRadius;
            double z = Math.sin(angle) * zoneRadius;
            Location ringPoint = center.clone().add(x, 0.2, z);
            ringPoint.getWorld().spawnParticle(Particle.WITCH, ringPoint, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "残留"; }

    @Override
    public String getDescription() { return "着弾点に持続効果領域を生成する"; }

    @Override
    public int getManaCost() { return config.getManaCost("linger"); }

    @Override
    public int getTier() { return config.getTier("linger"); }
}
