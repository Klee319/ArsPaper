package com.arspaper.spell.form;

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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
 *   - 半径増加(aoe_radius): ビームが太くなる
 */
public class BeamForm implements SpellForm {

    private static final double BASE_RANGE = 30.0;
    private static final double RANGE_PER_ACCEL = 10.0;
    private static final double RANGE_PER_REACH = 15.0;
    private static final double SPREAD_ANGLE_STEP = 0.12;
    private static final double PARTICLE_STEP = 1.0;
    /** ビーム発射開始距離（プレイヤーの前方、視界を遮らないように） */
    private static final double BEAM_START_OFFSET = 1.5;

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public BeamForm(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "beam");
        this.config = config;
    }

    /** チャネリング廃止のためno-op */
    public static void cleanupAll() {}

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);
        fireSingleBeam(caster, context);
    }

    private void fireSingleBeam(Player caster, SpellContext context) {
        double range = Math.max(5.0, BASE_RANGE + context.getAcceleration() * RANGE_PER_ACCEL
            + context.getReachLevel() * RANGE_PER_REACH);
        int totalBeams = 1 + Math.min(context.getSplitCount(), 8);
        double beamRadius = context.getAoeRadiusLevel(); // 1個につき半径+1

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
        // rayTraceの起点は目線位置（判定精度のため）
        Location eyeOrigin = caster.getEyeLocation().clone();
        // パーティクル描画の起点はプレイヤー前方にオフセット（視界を遮らないように）
        Location particleOrigin = eyeOrigin.clone().add(direction.clone().multiply(BEAM_START_OFFSET));
        // 照射はデフォルトで全貫通（pierce増強不要）
        int pierceRemaining = Integer.MAX_VALUE;

        // DustTransitionで素早くフェード（サイズ極小=寿命短縮、赤→黒で即消滅感）
        float dustSize = Math.min(1.5f, 0.5f + (float) beamRadius * 0.3f);
        Particle.DustTransition dustTransition = new Particle.DustTransition(
            Color.fromRGB(255, 40, 40), Color.fromRGB(10, 0, 0), dustSize);

        // rayTraceでブロックヒット位置を先に取得（目線からトレース）
        RayTraceResult blockHit = caster.getWorld().rayTraceBlocks(
            eyeOrigin, direction, range, FluidCollisionMode.NEVER, true);
        double effectiveRange = blockHit != null
            ? eyeOrigin.distance(blockHit.getHitPosition().toLocation(caster.getWorld()))
            : range;

        // エンティティヒット判定半径（ビーム幅考慮）
        double entityHitRadius = 0.5 + beamRadius;

        // パーティクル描画開始距離（目線からのオフセット分を考慮）
        double particleStart = BEAM_START_OFFSET;

        // ビームのパーティクル描画（オフセット後の位置から描画、視界を遮らない）
        if (beamRadius > 0) {
            // 範囲照射: 外周リングで描画（軽量化）
            double stepInterval = Math.max(1.5, PARTICLE_STEP + beamRadius * 0.3);
            for (double dist = particleStart; dist <= effectiveRange; dist += stepInterval) {
                Location center = eyeOrigin.clone().add(direction.clone().multiply(dist));
                spawnBeamRing(center, direction, beamRadius, dustTransition);
            }
        } else {
            // 通常ビーム: 中心線（粒子小のため間隔を詰めて密度UP）
            for (double dist = particleStart; dist <= effectiveRange; dist += 0.4) {
                Location point = eyeOrigin.clone().add(direction.clone().multiply(dist));
                point.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, point,
                    1, 0, 0, 0, 0, dustTransition);
            }
        }

        // 軌跡モード: ビーム軌道上のブロックにも効果を適用
        if (context.isTraceActive()) {
            Set<Location> processedBlocks = new HashSet<>();
            for (double dist = 1.0; dist <= effectiveRange; dist += 1.0) {
                Location point = eyeOrigin.clone().add(direction.clone().multiply(dist));
                Location blockLoc = point.getBlock().getLocation();
                if (processedBlocks.add(blockLoc)) {
                    SpellContext trailCtx = context.copy();
                    trailCtx.resolveOnBlockTrace(blockLoc);
                }
            }
        }

        // エンティティヒット処理
        // ビーム半径内の全エンティティを後続効果の対象にする。
        // 貫通なし: ビーム中心線上は最初の1体のみ。半径内の周辺エンティティは全て対象。
        // 貫通あり: ビーム中心線上もpierceCount+1体まで対象。
        {
            // pierceRemaining=MAX_VALUE時のオーバーフロー防止
            int maxLineHits = (pierceRemaining >= Integer.MAX_VALUE - 1)
                ? Integer.MAX_VALUE : 1 + pierceRemaining;
            Set<UUID> hitEntities = new HashSet<>();

            // ビーム中心線上のエンティティ（細い判定: 半径0.5）
            List<LivingEntity> lineCandidates = new ArrayList<>();
            // ビーム半径内の周辺エンティティ（太い判定: beamRadius）
            List<LivingEntity> radiusCandidates = new ArrayList<>();

            for (LivingEntity entity : eyeOrigin.getWorld().getNearbyLivingEntities(
                    eyeOrigin, effectiveRange + entityHitRadius)) {
                if (entity.equals(caster)) continue;
                if (isEntityOnBeamPath(eyeOrigin, direction, effectiveRange, entity, 0.5)) {
                    lineCandidates.add(entity);
                } else if (beamRadius > 0
                        && isEntityOnBeamPath(eyeOrigin, direction, effectiveRange, entity, entityHitRadius)) {
                    radiusCandidates.add(entity);
                }
            }

            // 中心線: 距離順にソートし、maxLineHits体まで
            lineCandidates.sort((a, b) -> Double.compare(
                a.getLocation().distanceSquared(eyeOrigin),
                b.getLocation().distanceSquared(eyeOrigin)));

            int lineHits = 0;
            for (LivingEntity entity : lineCandidates) {
                if (lineHits >= maxLineHits) break;
                if (!hitEntities.add(entity.getUniqueId())) continue;
                SpellFxUtil.spawnImpactBurst(entity.getLocation());
                SpellContext hitContext = context.copy();
                hitContext.resolveOnEntity(entity);
                lineHits++;
            }

            // 半径内の周辺: 全て対象（貫通制限なし、中心線ヒット済みは除外）
            for (LivingEntity entity : radiusCandidates) {
                if (!hitEntities.add(entity.getUniqueId())) continue;
                SpellFxUtil.spawnImpactBurst(entity.getLocation());
                SpellContext hitContext = context.copy();
                hitContext.resolveOnEntity(entity);
            }

            // エンティティにヒットした場合、ブロックヒット処理をスキップ（非貫通時のみ）
            if (lineHits > 0 && pierceRemaining <= 0) {
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
            // ブロック未ヒット（空中照射）: 端点にブロック効果を適用（水生成等）
            Location endPoint = eyeOrigin.clone().add(direction.clone().multiply(range));
            SpellContext endCtx = context.copy();
            endCtx.resolveOnBlock(endPoint.getBlock().getLocation());
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
    private void spawnBeamImpact(Location loc) {
        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5),
            15, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.fromRGB(255, 50, 50), 2.0f));
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST,
            SoundCategory.PLAYERS, 0.6f, 1.5f);
    }

    /**
     * ビーム進行方向に垂直なリングパーティクルを描画する。
     */
    private void spawnBeamRing(Location center, Vector direction, double radius,
                                Particle.DustTransition dustTransition) {
        // 進行方向に垂直な2軸を求める
        Vector up = Math.abs(direction.getY()) > 0.9
            ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = direction.getCrossProduct(up).normalize();
        Vector forward = right.getCrossProduct(direction).normalize();

        // リング上に半径に応じた密度で描画（最低16点、半径1あたり+8点）
        int points = 16 + (int) (radius * 8);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            Location point = center.clone().add(
                right.clone().multiply(dx).add(forward.clone().multiply(dz)));
            point.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, point,
                1, 0, 0, 0, 0, dustTransition);
        }
        // 中心にも1つ
        center.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, center,
            1, 0, 0, 0, 0, dustTransition);
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
