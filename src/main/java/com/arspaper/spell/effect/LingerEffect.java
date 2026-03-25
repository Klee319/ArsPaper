package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.spell.*;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 着弾地点に持続効果領域を生成するEffect。
 * 後続のEffectチェーンを領域内で定期的に再適用する。
 * SpellContextが特別に処理する（DelayEffectと同様のパターン）。
 */
public class LingerEffect implements SpellEffect {

    public static final double ZONE_RADIUS = 3.0;
    public static final int BASE_DURATION_TICKS = 100;        // 5秒
    public static final int DURATION_BONUS_PER_LEVEL = 60;    // durationLevelあたり+3秒
    public static final int ZONE_TICK_INTERVAL = 20;           // 1秒ごとに効果適用
    private static final int RING_PARTICLE_COUNT = 16;

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    /** アクティブなリンガーゾーンのタスク。シャットダウン時にキャンセル。 */
    private static final Set<BukkitTask> activeZones = ConcurrentHashMap.newKeySet();

    public LingerEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "linger");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // SpellContextが特別処理する。ここではパーティクルのみ。
        spawnZoneParticles(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // SpellContextが特別処理する。ここではパーティクルのみ。
        spawnZoneParticles(blockLocation.clone().add(0.5, 0.5, 0.5));
    }

    /**
     * SpellContextから呼び出される。ゾーンを開始し後続effectGroupsを定期適用。
     */
    public void startZone(SpellContext context, Location center,
                          List<SpellEffect> remainingEffects, List<List<SpellAugment>> remainingAugments) {
        Player caster = context.getCaster();
        if (caster == null) return;

        double zoneRadius = config.getParam("linger", "zone-radius", ZONE_RADIUS);
        int baseDuration = (int) config.getParam("linger", "base-duration-ticks", BASE_DURATION_TICKS);
        int durationBonus = (int) config.getParam("linger", "duration-bonus-per-level", DURATION_BONUS_PER_LEVEL);
        int tickInterval = (int) config.getParam("linger", "zone-tick-interval", ZONE_TICK_INTERVAL);
        int duration = Math.max(1, baseDuration + context.getDurationLevel() * durationBonus);

        final BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            private int elapsed = 0;

            @Override
            public void run() {
                elapsed += tickInterval;

                if (elapsed > duration || !caster.isOnline()) {
                    cancel();
                    activeZones.remove(taskHolder[0]);
                    return;
                }

                spawnZoneParticles(center);

                // ゾーン内エンティティに効果適用（キャスターは除外、最大16体）
                int entityCount = 0;
                for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, zoneRadius)) {
                    if (entity.equals(caster)) continue;
                    if (!context.isValidAoeTarget(entity, caster)) continue;
                    applyRemainingEffects(context, entity, remainingEffects, remainingAugments);
                    if (++entityCount >= 16) break;
                }

                // 中心ブロックに効果適用
                applyRemainingBlockEffects(context, center, remainingEffects, remainingAugments);
            }
        }.runTaskTimer(plugin, 0L, tickInterval);

        activeZones.add(taskHolder[0]);
    }

    private void applyRemainingEffects(SpellContext baseContext, LivingEntity target,
                                       List<SpellEffect> effects, List<List<SpellAugment>> augments) {
        for (int i = 0; i < effects.size(); i++) {
            SpellContext ctx = baseContext.copy();
            ctx.resetPublicAugmentState();
            for (SpellAugment aug : augments.get(i)) {
                aug.modify(ctx);
            }
            effects.get(i).applyToEntity(ctx, target);
        }
    }

    private void applyRemainingBlockEffects(SpellContext baseContext, Location center,
                                            List<SpellEffect> effects, List<List<SpellAugment>> augments) {
        for (int i = 0; i < effects.size(); i++) {
            SpellContext ctx = baseContext.copy();
            ctx.resetPublicAugmentState();
            for (SpellAugment aug : augments.get(i)) {
                aug.modify(ctx);
            }
            effects.get(i).applyToBlock(ctx, center.getBlock().getLocation());
        }
    }

    private static void spawnZoneParticles(Location center) {
        center.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center, 5, 0.2, 0.2, 0.2, 0.3);

        for (int i = 0; i < RING_PARTICLE_COUNT; i++) {
            double angle = (2.0 * Math.PI / RING_PARTICLE_COUNT) * i;
            double x = Math.cos(angle) * ZONE_RADIUS;
            double z = Math.sin(angle) * ZONE_RADIUS;
            Location ringPoint = center.clone().add(x, 0.2, z);
            ringPoint.getWorld().spawnParticle(Particle.WITCH, ringPoint, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /**
     * サーバーシャットダウン時に全アクティブゾーンをキャンセルする。
     */
    public static void cleanupAll() {
        for (BukkitTask task : activeZones) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeZones.clear();
    }

    /**
     * LingerAugmentから呼び出される静的ゾーン開始メソッド。
     * LingerEffectのインスタンスなしでゾーンを生成する。
     */
    public static void startZoneStatic(JavaPlugin plugin, SpellContext context, Location center,
                                        List<SpellEffect> remainingEffects, List<List<SpellAugment>> remainingAugments) {
        Player caster = context.getCaster();
        if (caster == null) return;

        // Player強参照を避けるためUUIDを保持
        final java.util.UUID casterUuid = caster.getUniqueId();
        GlyphConfig glyphConfig = ArsPaper.getInstance().getGlyphConfig();
        double zoneRadius = glyphConfig.getParam("linger", "zone-radius", ZONE_RADIUS);
        int baseDuration = (int) glyphConfig.getParam("linger", "base-duration-ticks", BASE_DURATION_TICKS);
        int durationBonus = (int) glyphConfig.getParam("linger", "duration-bonus-per-level", DURATION_BONUS_PER_LEVEL);
        int tickInterval = (int) glyphConfig.getParam("linger", "zone-tick-interval", ZONE_TICK_INTERVAL);
        int duration = Math.max(1, baseDuration + context.getDurationLevel() * durationBonus);

        final BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = new BukkitRunnable() {
            private int elapsed = 0;

            @Override
            public void run() {
                elapsed += tickInterval;

                Player onlineCaster = org.bukkit.Bukkit.getPlayer(casterUuid);
                if (elapsed > duration || onlineCaster == null || !onlineCaster.isOnline()) {
                    cancel();
                    activeZones.remove(taskHolder[0]);
                    return;
                }

                spawnZoneParticles(center);

                // ゾーン内エンティティに効果適用（キャスターは除外、最大16体）
                int entityCount = 0;
                for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, zoneRadius)) {
                    if (entity.equals(onlineCaster)) continue;
                    if (!context.isValidAoeTarget(entity, onlineCaster)) continue;
                    for (int i = 0; i < remainingEffects.size(); i++) {
                        SpellContext ctx = context.copy();
                        ctx.resetPublicAugmentState();
                        for (SpellAugment aug : remainingAugments.get(i)) {
                            aug.modify(ctx);
                        }
                        remainingEffects.get(i).applyToEntity(ctx, entity);
                    }
                    if (++entityCount >= 16) break;
                }

                // 中心ブロックに効果適用
                for (int i = 0; i < remainingEffects.size(); i++) {
                    SpellContext ctx = context.copy();
                    ctx.resetPublicAugmentState();
                    for (SpellAugment aug : remainingAugments.get(i)) {
                        aug.modify(ctx);
                    }
                    remainingEffects.get(i).applyToBlock(ctx, center.getBlock().getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, tickInterval);

        activeZones.add(taskHolder[0]);
    }

    public int calculateDuration(SpellContext context) {
        int baseDuration = (int) config.getParam("linger", "base-duration-ticks", BASE_DURATION_TICKS);
        int durationBonus = (int) config.getParam("linger", "duration-bonus-per-level", DURATION_BONUS_PER_LEVEL);
        return Math.max(1, baseDuration + context.getDurationLevel() * durationBonus);
    }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "残留"; }

    @Override
    public String getDescription() { return "対象地点に持続効果領域を生成する"; }

    @Override
    public int getManaCost() { return config.getManaCost("linger"); }

    @Override
    public int getTier() { return config.getTier("linger"); }
}
