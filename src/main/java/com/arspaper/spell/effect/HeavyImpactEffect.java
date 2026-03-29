package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.arspaper.spell.SpellTaskLimiter;

import java.util.Collection;

/**
 * ヘビーインパクト — 衝撃波を走らせ、範囲内の全敵に無敵時間を無視した5段階連続ダメージ。
 *
 * 増幅: 1発あたりのダメージ増加
 * 半径増加: 基本3×3から範囲拡大
 * 残留: 10秒間判定が残り、2秒毎にダメージ
 * ブロックが同心円状に波打つ演出。
 * 互換形態: 投射、照射、足元、頭上、接触、旋回
 */
public class HeavyImpactEffect implements SpellEffect {

    private static final double BASE_DAMAGE_PER_HIT = 3.0;
    private static final double AMPLIFY_DAMAGE_BONUS = 1.5;
    private static final int HIT_COUNT = 5;
    private static final int HIT_INTERVAL_TICKS = 3;       // 3tick(0.15秒)間隔
    private static final int BASE_RADIUS = 1;               // 3×3 = 中心+1ブロック
    private static final int LINGER_DURATION_TICKS = 200;   // 10秒
    private static final int LINGER_TICK_INTERVAL = 40;     // 2秒

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public HeavyImpactEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "heavy_impact");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Location center = target.getLocation().clone();
        executeImpact(context, center);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Location center = blockLocation.clone().add(0.5, 1, 0.5);
        executeImpact(context, center);
    }

    private void executeImpact(SpellContext context, Location center) {
        Player caster = context.getCaster();
        if (caster == null) return;

        double baseDmg = config.getParam("heavy_impact", "base-damage-per-hit", BASE_DAMAGE_PER_HIT);
        double amplifyBonus = config.getParam("heavy_impact", "amplify-damage-bonus", AMPLIFY_DAMAGE_BONUS);
        double damagePerHit = baseDmg + context.getAmplifyLevel() * amplifyBonus;
        int radius = (int) config.getParam("heavy_impact", "base-radius", (double) BASE_RADIUS)
            + context.getAoeRadiusLevel();
        int hitCount = (int) config.getParam("heavy_impact", "hit-count", (double) HIT_COUNT);
        int lingerDurationTicks = (int) config.getParam("heavy_impact", "linger-duration-ticks", (double) LINGER_DURATION_TICKS);
        int lingerTickInterval = (int) config.getParam("heavy_impact", "linger-tick-interval", (double) LINGER_TICK_INTERVAL);
        boolean linger = context.isLingerPattern();

        // 衝撃波サウンド
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE,
            SoundCategory.PLAYERS, 1.0f, 0.5f);

        // 5段階連続ダメージ
        BukkitTask task = new BukkitRunnable() {
            int hitIndex = 0;

            @Override
            public void run() {
                if (hitIndex >= hitCount) {
                    cancel();
                    // 残留モード
                    if (linger) {
                        startLingerZone(center, radius, damagePerHit, caster, context, lingerDurationTicks, lingerTickInterval);
                    }
                    return;
                }

                // ダメージ適用（PvP保護準拠）
                double effectiveRadius = radius + 0.5;
                Collection<LivingEntity> targets = center.getNearbyLivingEntities(effectiveRadius);
                for (LivingEntity entity : targets) {
                    if (entity.equals(caster)) continue;
                    if (!context.isValidAoeTarget(entity, caster)) continue;
                    // 無敵時間を無視
                    entity.setNoDamageTicks(0);
                    double finalDamage = context.calculateSpellDamage(damagePerHit, entity);
                    entity.damage(finalDamage, caster);
                }

                // 同心円衝撃波演出（hitIndexに応じて広がる）
                spawnShockwaveRing(center, hitIndex, radius);

                hitIndex++;
            }
        }.runTaskTimer(plugin, 0L, HIT_INTERVAL_TICKS);
        SpellTaskLimiter.register("heavy_impact", task);
    }

    /**
     * 残留ダメージゾーンを開始する。
     */
    private void startLingerZone(Location center, int radius, double damagePerHit,
                                  Player caster, SpellContext context,
                                  int lingerDurationTicks, int lingerTickInterval) {
        java.util.UUID casterUUID = caster.getUniqueId();
        BukkitTask lingerTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += lingerTickInterval;
                if (ticks > lingerDurationTicks) {
                    cancel();
                    return;
                }

                Player onlineCaster = org.bukkit.Bukkit.getPlayer(casterUUID);
                if (onlineCaster == null) {
                    cancel();
                    return;
                }

                double effectiveRadius = radius + 0.5;
                for (LivingEntity entity : center.getNearbyLivingEntities(effectiveRadius)) {
                    if (entity.equals(onlineCaster)) continue;
                    if (!context.isValidAoeTarget(entity, onlineCaster)) continue;
                    entity.setNoDamageTicks(0);
                    double finalDamage = context.calculateSpellDamage(damagePerHit, entity);
                    entity.damage(finalDamage, onlineCaster);
                }

                // 残留パーティクル
                spawnLingerParticles(center, radius);
            }
        }.runTaskTimer(plugin, lingerTickInterval, lingerTickInterval);
        SpellTaskLimiter.register("heavy_impact_linger", lingerTask);
    }

    /**
     * 同心円状に波打つブロック演出。
     * hitIndex(0〜4)に応じて内側から外側へリング状にパーティクルを広げる。
     */
    private void spawnShockwaveRing(Location center, int hitIndex, int maxRadius) {
        // 衝撃波が内側から外側に広がる
        int cfgHitCount = (int) config.getParam("heavy_impact", "hit-count", (double) HIT_COUNT);
        double ringRadius = (hitIndex + 1) * ((maxRadius + 1.0) / cfgHitCount);

        int points = Math.max(8, (int) (ringRadius * 8));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = Math.cos(angle) * ringRadius;
            double z = Math.sin(angle) * ringRadius;
            Location point = center.clone().add(x, 0, z);

            // 足元のブロック素材でパーティクル
            Block below = point.clone().subtract(0, 1, 0).getBlock();
            BlockData blockData = below.getType().isAir()
                ? Material.STONE.createBlockData()
                : below.getBlockData();

            point.getWorld().spawnParticle(Particle.BLOCK, point,
                5, 0.2, 0.4, 0.2, 0.1, blockData);

            // 上向きのダスト演出（ブロックが跳ね上がる風）
            point.getWorld().spawnParticle(Particle.CLOUD, point.clone().add(0, 0.3, 0),
                1, 0.1, 0.1, 0.1, 0.02);
        }

        // 衝撃音（段階に応じてピッチ変化）
        float pitch = 0.5f + hitIndex * 0.15f;
        center.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK,
            SoundCategory.PLAYERS, 1.0f, pitch);
    }

    private void spawnLingerParticles(Location center, int radius) {
        center.getWorld().spawnParticle(Particle.DUST_PLUME, center,
            10, radius * 0.5, 0.2, radius * 0.5, 0.05);
        center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND,
            SoundCategory.PLAYERS, 0.3f, 1.5f);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "ヘビーインパクト"; }
    @Override public String getDescription() { return "地面に衝撃波を走らせ、5段階の連続ダメージを与える"; }
    @Override public int getManaCost() { return config.getManaCost("heavy_impact"); }
    @Override public int getTier() { return config.getTier("heavy_impact"); }
}
