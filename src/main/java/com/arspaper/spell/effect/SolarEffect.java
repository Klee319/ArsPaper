package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Color;
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

import com.arspaper.spell.SpellTaskLimiter;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 日輪 — 空中に太陽を召喚し、周囲の敵に防御無視の炎ダメージを与え炎上させる。
 *
 * 増幅: ダメージ威力上昇
 * 半径増加: 索敵範囲拡大
 * 延長/短縮: 召喚持続時間
 * 分裂: 発射頻度上昇（同時射出数増加）
 */
public class SolarEffect implements SpellEffect {

    private static final double BASE_DAMAGE = 4.0;
    private static final double AMPLIFY_DAMAGE_BONUS = 2.0;
    private static final double BASE_RADIUS = 8.0;
    private static final int BASE_DURATION = 200;            // 10秒
    private static final int DURATION_PER_LEVEL = 100;       // +5秒/段
    private static final int BASE_FIRE_INTERVAL = 20;        // 1秒ごと
    private static final int FIRE_TICKS = 60;                // 3秒炎上

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public SolarEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "solar");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティ頭上に召喚
        Location summonLoc = target.getLocation().add(0, target.getHeight() + 1.5, 0);
        startSolar(context, summonLoc);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック地面の2ブロック上
        Location summonLoc = blockLocation.clone().add(0.5, 2.5, 0.5);
        startSolar(context, summonLoc);
    }

    private void startSolar(SpellContext context, Location center) {
        Player caster = context.getCaster();
        if (caster == null) return;
        UUID casterUUID = caster.getUniqueId();

        double damage = config.getParam("solar", "base-damage", BASE_DAMAGE)
            + config.getParam("solar", "amplify-damage-bonus", AMPLIFY_DAMAGE_BONUS)
                * context.getAmplifyLevel();
        double radiusPerAoe = config.getParam("solar", "radius-per-aoe", 1.0);
        double radius = config.getParam("solar", "base-radius", BASE_RADIUS)
            + context.getAoeRadiusLevel() * radiusPerAoe;
        int baseDur = (int) config.getParam("solar", "base-duration", BASE_DURATION);
        int durPerLevel = (int) config.getParam("solar", "duration-per-level", DURATION_PER_LEVEL);
        int durationTicks = baseDur + context.getDurationLevel() * durPerLevel;
        int shotsPerVolley = 1 + context.getSplitCount();
        int cfgFireInterval = (int) config.getParam("solar", "base-fire-interval", (double) BASE_FIRE_INTERVAL);
        int fireInterval = Math.max(5, cfgFireInterval / Math.max(1, shotsPerVolley));

        // 召喚音
        center.getWorld().playSound(center, Sound.ITEM_FIRECHARGE_USE,
            SoundCategory.PLAYERS, 1.5f, 0.5f);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > durationTicks) {
                    cancel();
                    return;
                }

                Player onlineCaster = org.bukkit.Bukkit.getPlayer(casterUUID);
                if (onlineCaster == null) { cancel(); return; }

                // 太陽パーティクル（常時描画）
                if (ticks % 2 == 0) {
                    spawnSunParticles(center);
                }

                // 炎弾発射
                if (ticks % fireInterval == 0) {
                    List<LivingEntity> targets = center.getNearbyLivingEntities(radius).stream()
                        .filter(e -> !e.equals(onlineCaster))
                        .filter(e -> context.isValidAoeTarget(e, onlineCaster))
                        .sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)))
                        .limit(shotsPerVolley)
                        .toList();

                    for (LivingEntity target : targets) {
                        fireFlameProjectile(center, target, damage, onlineCaster);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        SpellTaskLimiter.register("solar", task);
    }

    private void fireFlameProjectile(Location from, LivingEntity target, double damage, Player caster) {
        Location targetLoc = target.getLocation().add(0, target.getHeight() / 2, 0);

        // 炎弾の軌跡パーティクル
        org.bukkit.util.Vector direction = targetLoc.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();
        for (double d = 0; d < distance; d += 0.5) {
            Location point = from.clone().add(direction.clone().multiply(d));
            from.getWorld().spawnParticle(Particle.FLAME, point, 1, 0.05, 0.05, 0.05, 0.01);
            from.getWorld().spawnParticle(Particle.SMALL_FLAME, point, 1, 0.1, 0.1, 0.1, 0.01);
        }

        // 防御無視ダメージ（直接HP減少）
        double finalHP = Math.max(0, target.getHealth() - damage);
        target.setHealth(finalHP);
        if (finalHP <= 0) return;

        // 炎上
        int fireTicks = (int) config.getParam("solar", "fire-ticks", (double) FIRE_TICKS);
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));

        // ヒット演出
        target.getWorld().spawnParticle(Particle.LAVA, targetLoc, 5, 0.2, 0.2, 0.2, 0);
        target.getWorld().playSound(targetLoc, Sound.ENTITY_BLAZE_SHOOT,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    private void spawnSunParticles(Location center) {
        // 太陽の球体パーティクル
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 200, 50), 2.0f);
        Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.5f);

        int points = 12;
        double radius = 0.6;
        double time = System.currentTimeMillis() / 500.0;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points + time;
            Location point = center.clone().add(
                Math.cos(angle) * radius, Math.sin(angle * 0.7) * 0.3, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, gold);
        }
        // 中心の炎
        center.getWorld().spawnParticle(Particle.FLAME, center, 3, 0.15, 0.15, 0.15, 0.02);
        center.getWorld().spawnParticle(Particle.DUST, center, 2, 0.1, 0.1, 0.1, 0, orange);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "日輪"; }
    @Override public String getDescription() { return "太陽を召喚し周囲の敵に炎ダメージを与える"; }
    @Override public int getManaCost() { return config.getManaCost("solar"); }
    @Override public int getTier() { return config.getTier("solar"); }
}
