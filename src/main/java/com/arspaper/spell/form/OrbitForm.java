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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 術者の周囲を旋回する魔力弾を生成し、接触したエンティティにEffectチェーンを適用するForm。
 * Ars Nouveau準拠: オーブは持続時間中ずっと旋回し、同一エンティティへのヒットは
 * クールダウン付きで繰り返し発動する。
 *
 * Split: オーブ数増加（最大5）
 * Accelerate: 回転速度↑
 * Decelerate: 回転速度↓
 */
public class OrbitForm implements SpellForm {

    private static final double ORBIT_RADIUS = 2.0;
    private static final int BASE_DURATION_TICKS = 200;           // 10秒
    private static final double BASE_ANGULAR_SPEED = Math.PI / 10.0; // ラジアン/tick
    private static final double COLLISION_RADIUS_SQ = 1.5 * 1.5;
    private static final int MAX_ORBS = 5;
    private static final int HIT_COOLDOWN_TICKS = 20;             // 同一エンティティへの再ヒット間隔（1秒）
    private static final int EXTEND_DURATION_BONUS = 60;           // ExtendTimeあたり+3秒

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public OrbitForm(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "orbit");
        this.config = config;
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);

        int maxOrbs = (int) config.getParam("orbit", "max-orbs", (double) MAX_ORBS);
        int baseOrbs = (int) config.getParam("orbit", "base-orbs", 3.0);
        int orbCount = Math.min(baseOrbs + context.getSplitCount(), maxOrbs);
        double accel = context.getAcceleration(); // 加速:回転速度↑ / 減速:回転速度↓
        int durationLevel = context.getDurationLevel(); // ExtendTime(+) / DurationDown(-)
        int baseDurationTicks = (int) config.getParam("orbit", "base-duration-ticks", (double) BASE_DURATION_TICKS);
        int extendDurationBonus = (int) config.getParam("orbit", "extend-duration-bonus", (double) EXTEND_DURATION_BONUS);
        int totalDuration = Math.max(1, baseDurationTicks
            + durationLevel * extendDurationBonus);
        double angularSpeed = BASE_ANGULAR_SPEED * (1.0 + accel * 0.3);

        double orbitRadius = config.getParam("orbit", "orbit-radius", ORBIT_RADIUS);
        double radiusPerAoe = config.getParam("orbit", "radius-per-aoe", 1.0);
        double radius = orbitRadius + context.getAoeRadiusLevel() * radiusPerAoe;
        int hitCooldownTicks = (int) config.getParam("orbit", "hit-cooldown-ticks", (double) HIT_COOLDOWN_TICKS);

        for (int orbIndex = 0; orbIndex < orbCount; orbIndex++) {
            double phaseOffset = (2.0 * Math.PI / orbCount) * orbIndex;
            SpellContext orbContext = context.copy();

            new OrbitTask(caster, orbContext, totalDuration, angularSpeed, phaseOffset, radius, hitCooldownTicks)
                .runTaskTimer(plugin, 0L, 2L);
        }
    }

    /**
     * 個別のorb旋回タスク。2tick間隔で位置更新・衝突判定を行う。
     * オーブは持続時間中ずっと旋回し、同一エンティティへの連続ヒットはクールダウンで制限。
     */
    private static class OrbitTask extends BukkitRunnable {

        private final Player caster;
        private final SpellContext context;
        private final int maxTicks;
        private final double angularSpeed;
        private final double phaseOffset;
        private final double radius;
        private final int hitCooldownTicks;
        /** エンティティUUID → 最後にヒットしたtick */
        private final Map<UUID, Integer> hitCooldowns = new HashMap<>();

        private int elapsed = 0;

        OrbitTask(Player caster, SpellContext context, int maxTicks, double angularSpeed, double phaseOffset, double radius, int hitCooldownTicks) {
            this.caster = caster;
            this.context = context;
            this.maxTicks = maxTicks;
            this.angularSpeed = angularSpeed;
            this.phaseOffset = phaseOffset;
            this.radius = radius;
            this.hitCooldownTicks = hitCooldownTicks;
        }

        @Override
        public void run() {
            elapsed += 2;

            if (elapsed > maxTicks || !caster.isOnline() || caster.isDead()) {
                cancel();
                return;
            }

            // 現在のorb位置を算出
            double angle = phaseOffset + angularSpeed * elapsed;
            Location center = caster.getLocation().add(0, 1.0, 0);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location orbLoc = center.clone().add(x, 0, z);

            // 軌跡パーティクル
            orbLoc.getWorld().spawnParticle(Particle.END_ROD, orbLoc, 2, 0.05, 0.05, 0.05, 0.01);
            orbLoc.getWorld().spawnParticle(Particle.ENCHANT, orbLoc, 3, 0.1, 0.1, 0.1, 0.3);

            // エンティティ衝突判定（クールダウン付き繰り返しヒット）
            for (LivingEntity entity : orbLoc.getWorld().getNearbyLivingEntities(orbLoc, 1.5)) {
                if (entity.equals(caster)) continue;
                if (entity.isDead()) continue;
                if (entity.getLocation().add(0, 1, 0).distanceSquared(orbLoc) > COLLISION_RADIUS_SQ) continue;

                UUID entityId = entity.getUniqueId();
                Integer lastHitTick = hitCooldowns.get(entityId);
                if (lastHitTick != null && (elapsed - lastHitTick) < hitCooldownTicks) continue;

                hitCooldowns.put(entityId, elapsed);
                SpellFxUtil.spawnImpactBurst(entity.getLocation());

                SpellContext hitContext = context.copy();
                hitContext.resolveOnEntity(entity);
            }

            // ブロック衝突判定（破壊/収穫/伐採/精錬/交換/無形 等のブロック系効果用）
            org.bukkit.block.Block block = orbLoc.getBlock();
            if (!block.getType().isAir()) {
                long blockKey = block.getLocation().hashCode();
                String blockKeyStr = block.getX() + "," + block.getY() + "," + block.getZ();
                Integer lastBlockHit = hitCooldowns.get(UUID.nameUUIDFromBytes(blockKeyStr.getBytes()));
                if (lastBlockHit == null || (elapsed - lastBlockHit) >= hitCooldownTicks) {
                    hitCooldowns.put(UUID.nameUUIDFromBytes(blockKeyStr.getBytes()), elapsed);
                    SpellContext blockContext = context.copy();
                    blockContext.resolveOnBlock(block.getLocation());
                }
            }
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "旋回"; }

    @Override
    public String getDescription() { return "術者の周囲を旋回する魔力弾を生成する"; }

    @Override
    public int getManaCost() { return config.getManaCost("orbit"); }

    @Override
    public int getTier() { return config.getTier("orbit"); }
}
