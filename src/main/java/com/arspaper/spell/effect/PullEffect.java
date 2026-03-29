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
 * 引き寄せエフェクト — 対象の位置を中心に吸引エリアを生成する。
 * 突風(WindBurst)の逆で、エンティティを中心に引き寄せる。
 * 対象がエンティティの場合、対象自身は吸引の影響を受けない。
 *
 * 半径増加: 吸引範囲拡大
 * 増幅: 吸引速度（威力）上昇
 * 減衰: 吸引速度低下
 * 延長/短縮: 効果時間
 */
public class PullEffect implements SpellEffect {

    private static final double BASE_RADIUS = 3.0;
    private static final double BASE_FORCE = 0.5;
    private static final double AMPLIFY_FORCE_BONUS = 0.3;
    private static final int BASE_DURATION = 100;           // 5秒
    private static final int DURATION_PER_LEVEL = 60;       // +3秒/段

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public PullEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "pull");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double radius = config.getParam("pull", "base-radius", BASE_RADIUS)
            + context.getAoeRadiusLevel();
        double force = config.getParam("pull", "base-force", BASE_FORCE)
            + config.getParam("pull", "amplify-force-bonus", AMPLIFY_FORCE_BONUS)
                * context.getAmplifyLevel();
        force = Math.max(0.1, force);
        int baseDur = (int) config.getParam("pull", "base-duration", BASE_DURATION);
        int durPerLevel = (int) config.getParam("pull", "duration-per-level", DURATION_PER_LEVEL);
        int duration = baseDur + context.getDurationLevel() * durPerLevel;

        Player caster = context.getCaster();

        // エンティティ中心: 対象の周囲に吸引エリア展開。対象自身は影響を受けない
        startPullTask(target, null, radius, force, duration, caster, context);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        double radius = config.getParam("pull", "base-radius", BASE_RADIUS)
            + context.getAoeRadiusLevel();
        double force = config.getParam("pull", "base-force", BASE_FORCE)
            + config.getParam("pull", "amplify-force-bonus", AMPLIFY_FORCE_BONUS)
                * context.getAmplifyLevel();
        force = Math.max(0.1, force);
        int baseDur = (int) config.getParam("pull", "base-duration", BASE_DURATION);
        int durPerLevel = (int) config.getParam("pull", "duration-per-level", DURATION_PER_LEVEL);
        int duration = baseDur + context.getDurationLevel() * durPerLevel;

        Player caster = context.getCaster();
        Location center = blockLocation.clone().add(0.5, 0.5, 0.5);

        // ブロック中心: 固定位置に吸引エリア展開
        startPullTask(null, center, radius, force, duration, caster, context);
    }

    /**
     * 吸引タスクを開始する。
     * @param anchorEntity null以外ならエンティティ追従、nullならfixedCenter固定位置
     */
    private void startPullTask(LivingEntity anchorEntity, Location fixedCenter,
                                double radius, double force, int durationTicks,
                                Player caster, SpellContext context) {
        Location initLoc = anchorEntity != null ? anchorEntity.getLocation() : fixedCenter;
        spawnPullStartFx(initLoc);

        final double pullForce = force;
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

                // 範囲内のエンティティを引き寄せる（5tickごと、PvP保護準拠）
                if (ticks % 5 == 0) {
                    for (LivingEntity nearby : center.getNearbyLivingEntities(radius)) {
                        // アンカーエンティティ自身と術者は除外
                        if (anchorEntity != null && nearby.equals(anchorEntity)) continue;
                        if (onlineCaster != null && nearby.equals(onlineCaster)) continue;
                        if (onlineCaster != null && !context.isValidAoeTarget(nearby, onlineCaster)) continue;

                        pullToward(nearby, center, pullForce);
                    }
                }

                // パーティクル（10tickごと）
                if (ticks % 10 == 0) {
                    spawnPullParticles(center, radius);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        SpellTaskLimiter.register("pull", task);
    }

    private void pullToward(LivingEntity entity, Location center, double force) {
        Vector direction = center.toVector().subtract(entity.getLocation().toVector());
        if (direction.lengthSquared() < 0.25) return; // 中心付近は無視
        direction.normalize().multiply(force);
        direction.setY(Math.max(direction.getY(), 0.05)); // 最低限の浮き
        entity.setVelocity(direction);
    }

    private void spawnPullStartFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.PORTAL, loc, 30, 1.5, 0.5, 1.5, 0.5);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 20, 1.0, 0.5, 1.0, 1.0);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.PLAYERS, 0.8f, 0.5f);
    }

    private void spawnPullParticles(Location center, double radius) {
        double time = System.currentTimeMillis() / 400.0;
        int points = 16;

        // 外周から中心に向かう渦巻き風（吸い込み方向を示す）
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points - time; // 逆回転（吸引感）
            double r = radius * (0.4 + (i % 3) * 0.3); // 複数リングで奥行き
            Location outerPoint = center.clone().add(
                Math.cos(angle) * r, 0.3 + Math.sin(angle * 2) * 0.4, Math.sin(angle) * r);

            // 中心向きの速度ベクトル（吸い込み方向）
            Vector toCenter = center.toVector().subtract(outerPoint.toVector()).normalize().multiply(0.2);
            center.getWorld().spawnParticle(Particle.CLOUD, outerPoint,
                0, toCenter.getX(), 0.01, toCenter.getZ(), 0.1);
        }

        // 中心の渦（吸引の目）
        for (int i = 0; i < 4; i++) {
            double angle = 2 * Math.PI * i / 4 - time * 2;
            Location vortexPoint = center.clone().add(
                Math.cos(angle) * 0.4, 0.5, Math.sin(angle) * 0.4);
            center.getWorld().spawnParticle(Particle.ENCHANT, vortexPoint,
                2, 0.1, 0.2, 0.1, 0.5);
        }

        // 地面の砂塵（中心に集まる）
        center.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, 0.1, 0),
            2, radius * 0.3, 0.05, radius * 0.3, 0.005);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "引寄"; }
    @Override public String getDescription() { return "対象の位置に吸引エリアを生成し、周囲を引き寄せる"; }
    @Override public int getManaCost() { return config.getManaCost("pull"); }
    @Override public int getTier() { return config.getTier("pull"); }
}
