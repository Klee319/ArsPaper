package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ソニックブーム — 術者の視点方向へ直線状にソニックブームを発生させる。
 *
 * エンティティと透過ブロックはデフォルトで貫通。
 * ソリッドブロックで停止（貫通増強で 2×n ブロック貫通可能）。
 * 分裂: 左右に拡散ソニックブーム追加。
 * 増幅: ダメージ威力増加。
 * 互換形態: 自己のみ。
 */
public class SonicBoomEffect implements SpellEffect {

    private static final double BASE_RANGE = 20.0;
    private static final double BASE_DAMAGE = 10.0;
    private static final double AMPLIFY_DAMAGE_BONUS = 4.0;
    private static final double SPREAD_ANGLE_STEP = 0.15;
    private static final double SCAN_STEP = 0.5;
    private static final double HIT_RADIUS = 1.0;

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SonicBoomEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "sonic_boom");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // self form: target == caster
        Player caster = context.getCaster();
        if (caster == null) return;

        double range = config.getParam("sonic_boom", "range", BASE_RANGE);
        double baseDamage = config.getParam("sonic_boom", "base-damage", BASE_DAMAGE);
        double amplifyBonus = config.getParam("sonic_boom", "amplify-damage-bonus", AMPLIFY_DAMAGE_BONUS);
        double damage = baseDamage + context.getAmplifyLevel() * amplifyBonus;

        int totalBeams = 1 + Math.min(context.getSplitCount(), 6);
        // 貫通1個 = ソリッドブロック2個分貫通
        int blockPierceBlocks = 2 * context.getPierceCount();
        double spreadAngleStep = config.getParam("sonic_boom", "spread-angle-step", SPREAD_ANGLE_STEP);

        Vector baseDirection = caster.getLocation().getDirection();

        for (int i = 0; i < totalBeams; i++) {
            Vector direction = baseDirection.clone();
            if (totalBeams > 1) {
                double angle = (i - (totalBeams - 1) / 2.0) * spreadAngleStep;
                direction.rotateAroundY(angle);
            }
            fireSonicBoom(caster, direction, range, damage, blockPierceBlocks, context);
        }
    }

    private void fireSonicBoom(Player caster, Vector direction, double range,
                                double damage, int blockPierceBlocks, SpellContext context) {
        Location origin = caster.getEyeLocation().clone();
        Set<UUID> hitEntities = new HashSet<>();
        int pierceRemaining = blockPierceBlocks;

        double effectiveRange = range;

        // ブロック衝突スキャン
        // 透過ブロック（ガラス、葉等）とPassable(空気、花等)は自動貫通
        // ソリッド不透明ブロックのみ貫通チャージを消費
        Set<Location> checkedBlocks = new HashSet<>();
        for (double dist = 0.5; dist <= range; dist += 0.5) {
            Location point = origin.clone().add(direction.clone().multiply(dist));
            Block block = point.getBlock();
            Location blockLoc = block.getLocation();
            if (!checkedBlocks.add(blockLoc)) continue; // 同一ブロック重複チェック回避

            // Passable(空気、花、松明等)・液体 → 自動貫通
            if (block.isPassable() || block.isLiquid()) continue;
            // 非不透明ブロック(ガラス、氷、葉等) → 自動貫通
            if (!block.getType().isOccluding()) continue;

            // ソリッド不透明ブロック → 貫通チャージ消費
            if (pierceRemaining > 0) {
                pierceRemaining--;
            } else {
                effectiveRange = dist;
                break;
            }
        }

        // エンティティヒット（全貫通、PvP保護準拠）
        // 一括スキャン: ビーム全長をカバーする範囲で1回だけgetNearbyLivingEntitiesを呼ぶ
        double hitRadius = config.getParam("sonic_boom", "hit-radius", HIT_RADIUS);
        Location midPoint = origin.clone().add(direction.clone().multiply(effectiveRange / 2));
        double scanRadius = effectiveRange / 2 + hitRadius + 1;
        for (LivingEntity nearby : midPoint.getNearbyLivingEntities(scanRadius)) {
            if (nearby.equals(caster)) continue;
            if (!hitEntities.add(nearby.getUniqueId())) continue;
            if (!context.isValidAoeTarget(nearby, caster)) continue;

            // ビーム直線上にいるかチェック
            Vector toEntity = nearby.getLocation().add(0, 1, 0).toVector().subtract(origin.toVector());
            double projection = toEntity.dot(direction);
            if (projection < 0 || projection > effectiveRange) continue;
            Vector closest = origin.toVector().add(direction.clone().multiply(projection));
            double distSq = closest.distanceSquared(nearby.getLocation().add(0, 1, 0).toVector());
            if (distSq > hitRadius * hitRadius) continue;

            double finalDamage = context.calculateSpellDamage(damage, nearby);
            nearby.damage(finalDamage, caster);
        }

        // ビジュアル: ソニックブームパーティクル
        spawnSonicBoomFx(origin, direction, effectiveRange);
    }

    private void spawnSonicBoomFx(Location origin, Vector direction, double range) {
        // Warden風ソニックブーム演出
        origin.getWorld().playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM,
            SoundCategory.PLAYERS, 1.5f, 1.0f);

        for (double dist = 1.5; dist <= range; dist += 1.5) {
            Location point = origin.clone().add(direction.clone().multiply(dist));
            origin.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // 自己形態のみなのでブロック対象は発生しない
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "ソニックブーム"; }
    @Override public String getDescription() { return "視点方向へソニックブームを発射する（自己形態のみ対応）"; }
    @Override public int getManaCost() { return config.getManaCost("sonic_boom"); }
    @Override public int getTier() { return config.getTier("sonic_boom"); }
}
