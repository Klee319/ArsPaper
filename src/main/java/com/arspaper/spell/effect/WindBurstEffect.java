package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.arspaper.spell.SpellTaskLimiter;

/**
 * 突風エフェクト - 対象の周囲に風のバリアを展開する。
 *
 * ブロック対象: 指定位置の周囲にバリアを展開。近づくエンティティを弾き返す。
 * エンティティ対象: 対象エンティティの周囲にバリアを展開。対象自身は影響を受けない。
 *
 * 半径増加: バリア範囲拡大
 * 増幅: 吹き飛ばし威力（距離）上昇
 * 減衰: 吹き飛ばし威力低下
 */
public class WindBurstEffect implements SpellEffect {

    private static final double BASE_RADIUS = 3.0;
    private static final double BASE_FORCE = 1.0;
    private static final double AMPLIFY_FORCE_BONUS = 0.5;
    private static final int BASE_DURATION = 100;           // 5秒
    private static final int DURATION_PER_LEVEL = 60;       // +3秒/段

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public WindBurstEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "wind_burst");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double radius = config.getParam("wind_burst", "base-radius", BASE_RADIUS)
            + context.getAoeRadiusLevel();
        double force = config.getParam("wind_burst", "base-force", BASE_FORCE)
            + config.getParam("wind_burst", "amplify-force-bonus", AMPLIFY_FORCE_BONUS)
                * context.getAmplifyLevel();
        int baseDur = (int) config.getParam("wind_burst", "base-duration", BASE_DURATION);
        int durPerLevel = (int) config.getParam("wind_burst", "duration-per-level", DURATION_PER_LEVEL);
        int duration = baseDur + context.getDurationLevel() * durPerLevel;

        Player caster = context.getCaster();

        // エンティティバリア: 対象の周囲に展開。対象自身は影響を受けない
        startBarrierTask(target, null, radius, force, duration, caster, context);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        double radius = config.getParam("wind_burst", "base-radius", BASE_RADIUS)
            + context.getAoeRadiusLevel();
        double force = config.getParam("wind_burst", "base-force", BASE_FORCE)
            + config.getParam("wind_burst", "amplify-force-bonus", AMPLIFY_FORCE_BONUS)
                * context.getAmplifyLevel();
        int baseDur = (int) config.getParam("wind_burst", "base-duration", BASE_DURATION);
        int durPerLevel = (int) config.getParam("wind_burst", "duration-per-level", DURATION_PER_LEVEL);
        int duration = baseDur + context.getDurationLevel() * durPerLevel;

        Player caster = context.getCaster();
        Location center = blockLocation.clone().add(0.5, 0.5, 0.5);

        // ブロックバリア: 固定位置に展開
        startBarrierTask(null, center, radius, force, duration, caster, context);
    }

    /**
     * バリアタスクを開始する。
     * @param anchorEntity null以外ならエンティティ追従、nullならfixedCenter固定位置
     */
    private void startBarrierTask(LivingEntity anchorEntity, Location fixedCenter,
                                   double radius, double force, int durationTicks,
                                   Player caster, SpellContext context) {
        spawnWindBurstFx(anchorEntity != null ? anchorEntity.getLocation() : fixedCenter);

        java.util.UUID casterUUID = caster != null ? caster.getUniqueId() : null;

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > durationTicks) {
                    cancel();
                    return;
                }

                // casterオフラインチェック
                Player onlineCaster = casterUUID != null
                    ? org.bukkit.Bukkit.getPlayer(casterUUID) : null;

                // アンカー位置を決定
                Location center;
                if (anchorEntity != null) {
                    if (anchorEntity.isDead() || !anchorEntity.isValid()) {
                        cancel();
                        return;
                    }
                    center = anchorEntity.getLocation().add(0, 0.5, 0);
                } else {
                    center = fixedCenter;
                }

                // 範囲内のエンティティを弾き返す（5tickごと、PvP保護準拠）
                if (ticks % 5 == 0) {
                    for (LivingEntity nearby : center.getNearbyLivingEntities(radius)) {
                        // アンカーエンティティ自身と術者は除外
                        if (anchorEntity != null && nearby.equals(anchorEntity)) continue;
                        if (onlineCaster != null && nearby.equals(onlineCaster)) continue;
                        if (onlineCaster != null && !context.isValidAoeTarget(nearby, onlineCaster)) continue;

                        knockbackAway(nearby, center, force);
                    }
                }

                // パーティクル（10tickごと）
                if (ticks % 10 == 0) {
                    spawnBarrierParticles(center, radius);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        SpellTaskLimiter.register("wind_burst", task);
    }

    private void knockbackAway(LivingEntity entity, Location center, double force) {
        Vector direction = entity.getLocation().toVector().subtract(center.toVector());
        if (direction.lengthSquared() < 0.0001) {
            direction = new Vector(0, 1, 0);
        } else {
            direction.normalize();
        }
        direction.setY(Math.max(direction.getY(), 0.3));
        direction.normalize().multiply(force);
        entity.setVelocity(direction);
    }

    private void spawnWindBurstFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 30, 1.5, 0.5, 1.5, 0.15);
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 5, 0.5, 0.2, 0.5, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_WIND_CHARGE_WIND_BURST,
            SoundCategory.PLAYERS, 1.0f, 0.9f);
    }

    private void spawnBarrierParticles(Location center, double radius) {
        int points = 12;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location point = center.clone().add(
                Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.CLOUD, point, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "突風"; }

    @Override
    public String getDescription() { return "対象の周囲に風のバリアを展開し、近づく者を弾き返す"; }

    @Override
    public int getManaCost() { return config.getManaCost("wind_burst"); }

    @Override
    public int getTier() { return config.getTier("wind_burst"); }
}
