package com.arspaper.spell.form;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaManager;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 直線状にレーザーを放つTier3形態。
 * 投射の軌道が直線（即着弾ヒットスキャン）になったバージョン。
 * レッドストーンパーティクルでビームを可視化する。
 *
 * 増強互換:
 *   - 拡散(split): ビーム数増加
 *   - 減速(decelerate): ビーム射程↓
 *   - 加速(accelerate): ビーム射程↑
 *   - 貫通(pierce): 複数エンティティを貫通
 *   - 範囲(aoe): ビームが太くなる
 *   - 軌跡(trail): ビーム軌道上にも効果を適用＋チャネリング（長押し連射）
 */
public class BeamForm implements SpellForm {

    private static final double BASE_RANGE = 30.0;
    private static final double RANGE_PER_ACCEL = 10.0;
    private static final double SPREAD_ANGLE_STEP = 0.12;
    private static final double PARTICLE_STEP = 1.0;
    private static final int MAX_CHANNEL_TICKS = 100; // 5秒
    private static final int CHANNEL_INTERVAL = 20;   // 20tickごとに再発射（1秒）
    private static final int CHANNEL_START_DELAY = 40; // チャネリング開始までの遅延（2秒、単押し防止）
    private static final int MAX_TRAIL_BLOCKS = 20;   // 軌跡モード最大ブロック効果数

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    /** アクティブなチャネリングタスクを追跡（shutdown cleanup用） */
    private static final Set<BukkitTask> activeChannels = ConcurrentHashMap.newKeySet();

    public BeamForm(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "beam");
        this.config = config;
    }

    /** サーバーシャットダウン時に全チャネリングを停止する。 */
    public static void cleanupAll() {
        for (BukkitTask task : activeChannels) {
            task.cancel();
        }
        activeChannels.clear();
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);

        if (context.isTrailActive()) {
            startChanneling(caster, context);
        } else {
            fireSingleBeam(caster, context);
        }
    }

    private void fireSingleBeam(Player caster, SpellContext context) {
        double range = Math.max(5.0, BASE_RANGE + context.getAcceleration() * RANGE_PER_ACCEL);
        int totalBeams = 1 + Math.min(context.getSplitCount(), 8);
        double beamRadius = context.getAoeLevel() * 0.5;

        Vector baseDirection = caster.getLocation().getDirection();

        for (int i = 0; i < totalBeams; i++) {
            Vector direction = baseDirection.clone();
            if (totalBeams > 1) {
                double angle = (i - (totalBeams - 1) / 2.0) * SPREAD_ANGLE_STEP;
                direction.rotateAroundY(angle);
            }

            SpellContext beamContext = context.copy();
            fireBeam(caster, beamContext, direction, range, beamRadius);
        }
    }

    /**
     * 個別ビームを発射する。rayTraceを使用してヒット判定し、パーティクルで可視化する。
     */
    private void fireBeam(Player caster, SpellContext context, Vector direction, double range, double beamRadius) {
        Location origin = caster.getEyeLocation();
        boolean trailMode = context.isTrailActive();
        int pierceRemaining = context.getPierceCount();

        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(200, 50, 50), 1.5f);

        // rayTraceでブロックヒット位置を先に取得
        RayTraceResult blockHit = caster.getWorld().rayTraceBlocks(
            origin, direction, range, FluidCollisionMode.NEVER, true);
        double effectiveRange = blockHit != null
            ? origin.distance(blockHit.getHitPosition().toLocation(caster.getWorld()))
            : range;

        // rayTraceでエンティティヒットを取得（ビーム幅考慮）
        double entityHitRadius = 0.5 + beamRadius;
        RayTraceResult entityHit = caster.getWorld().rayTraceEntities(
            origin, direction, effectiveRange, entityHitRadius,
            entity -> entity instanceof LivingEntity && !entity.equals(caster));

        // ビームのパーティクル描画（ヒット位置まで）
        for (double dist = 0.5; dist <= effectiveRange; dist += PARTICLE_STEP) {
            Location point = origin.clone().add(direction.clone().multiply(dist));
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
        }

        // 軌跡モード: 軌道上のブロックにも効果を適用（制限付き）
        if (trailMode) {
            Set<Location> processedBlocks = new HashSet<>();
            for (double dist = 1.0; dist <= effectiveRange && processedBlocks.size() < MAX_TRAIL_BLOCKS; dist += 1.0) {
                Location point = origin.clone().add(direction.clone().multiply(dist));
                Location blockLoc = point.getBlock().getLocation();
                if (processedBlocks.add(blockLoc) && point.getBlock().getType().isAir()) {
                    SpellContext trailCtx = context.copy();
                    trailCtx.resolveOnBlockNoAoe(blockLoc);
                }
            }
        }

        // 貫通モード: 複数エンティティをヒット
        if (pierceRemaining > 0) {
            Set<UUID> hitEntities = new HashSet<>();
            Collection<LivingEntity> nearbyEntities = origin.getWorld().getNearbyLivingEntities(
                origin, effectiveRange + entityHitRadius);

            for (LivingEntity entity : nearbyEntities) {
                if (entity.equals(caster)) continue;
                if (hitEntities.contains(entity.getUniqueId())) continue;

                // ビーム直線との距離チェック
                if (!isEntityOnBeamPath(origin, direction, effectiveRange, entity, entityHitRadius)) continue;

                hitEntities.add(entity.getUniqueId());
                SpellFxUtil.spawnImpactBurst(entity.getLocation());
                SpellContext hitContext = context.copy();
                hitContext.resolveOnEntity(entity);

                pierceRemaining--;
                if (pierceRemaining < 0) break;
            }
        } else if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity target) {
            // 非貫通: 最初のエンティティのみ
            double entityDist = origin.distance(target.getLocation());
            if (blockHit == null || entityDist < effectiveRange) {
                SpellFxUtil.spawnImpactBurst(target.getLocation());
                SpellContext hitContext = context.copy();
                hitContext.resolveOnEntity(target);
                spawnBeamImpact(target.getLocation());
                return;
            }
        }

        // ブロックヒット処理
        if (blockHit != null && blockHit.getHitBlock() != null) {
            Location blockLoc = blockHit.getHitBlock().getLocation();
            SpellContext blockCtx = context.copy();
            if (blockHit.getHitBlockFace() != null) {
                blockCtx.setHitFace(blockHit.getHitBlockFace());
            }
            blockCtx.resolveOnBlock(blockLoc);
            spawnBeamImpact(blockHit.getHitPosition().toLocation(caster.getWorld()));
        } else {
            Location endPoint = origin.clone().add(direction.clone().multiply(range));
            spawnBeamEnd(endPoint);
        }
    }

    /**
     * エンティティがビーム直線上にあるかチェックする。
     */
    private boolean isEntityOnBeamPath(Location origin, Vector direction, double maxDist,
                                        LivingEntity entity, double radius) {
        Vector toEntity = entity.getLocation().add(0, 1, 0).toVector().subtract(origin.toVector());
        double projection = toEntity.dot(direction);
        if (projection < 0 || projection > maxDist) return false;

        Vector closest = origin.toVector().add(direction.clone().multiply(projection));
        double distSq = closest.distanceSquared(entity.getLocation().add(0, 1, 0).toVector());
        return distSq <= radius * radius;
    }

    /**
     * チャネリングモード: 一定時間ビームを連射し続ける。
     */
    private void startChanneling(Player caster, SpellContext context) {
        int totalManaCost = context.getRecipe().getTotalManaCost();
        ManaManager manaManager = ArsPaper.getInstance().getManaManager();

        // 初回ビームは即発射（単押し分）
        fireSingleBeam(caster, context);

        // チャネリングは遅延後に開始（単押しなら発動しない）
        BukkitTask task = new BukkitRunnable() {
            private int elapsed = 0;

            @Override
            public void run() {
                elapsed += CHANNEL_INTERVAL;

                if (elapsed > MAX_CHANNEL_TICKS || !caster.isOnline()
                    || caster.isDead() || caster.isSneaking()) {
                    activeChannels.remove(this);
                    cancel();
                    return;
                }

                int tickCost = (int) Math.ceil((double) totalManaCost * CHANNEL_INTERVAL / 20.0);
                if (!manaManager.consumeMana(caster, tickCost)) {
                    activeChannels.remove(this);
                    cancel();
                    return;
                }

                fireSingleBeam(caster, context);
            }
        }.runTaskTimer(plugin, CHANNEL_START_DELAY, CHANNEL_INTERVAL);

        activeChannels.add(task);
    }

    private void spawnBeamImpact(Location loc) {
        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5),
            15, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.fromRGB(255, 50, 50), 2.0f));
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST,
            SoundCategory.PLAYERS, 0.6f, 1.5f);
    }

    private void spawnBeamEnd(Location loc) {
        loc.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.2, 0.2, 0.2, 0,
            new Particle.DustOptions(Color.fromRGB(200, 50, 50), 1.0f));
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "照射"; }

    @Override
    public String getDescription() { return "直線状にレーザーを放ち、命中した対象に効果を適用する"; }

    @Override
    public int getManaCost() { return config.getManaCost("beam"); }

    @Override
    public int getTier() { return config.getTier("beam"); }
}
