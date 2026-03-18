package com.arspaper.spell.effect;

import com.arspaper.spell.*;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * 地面にスペルトラップを設置するEffect。
 * エンティティがルーン上を通過すると、後続のEffectチェーンが発動し消滅する。
 * SpellContextがRuneEffectを検出した場合、後続グループは設置時には実行されず、
 * トリガー時に遅延実行される。
 */
public class RuneEffect implements SpellEffect {

    private static final int PARTICLE_INTERVAL = 10;
    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public RuneEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "rune");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティの足元にルーンを設置
        applyToBlock(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // 後続グループなしでルーンのみ設置（SpellContextからの直接呼び出し用）
        placeRuneWithEffects(context, blockLocation, null, null);
    }

    /**
     * SpellContextから呼び出される。ルーンを設置し、トリガー時に後続Effectを実行する。
     */
    public void placeRuneWithEffects(SpellContext context, Location blockLocation,
                                     List<SpellEffect> remainingEffects,
                                     List<List<SpellAugment>> remainingAugments) {
        Player caster = context.getCaster();
        if (caster == null) return;

        int baseLifetime = (int) config.getParam("rune", "base-lifetime", 300);
        int lifetimePerDur = (int) config.getParam("rune", "lifetime-per-duration", 200);
        double trigRadius = config.getParam("rune", "trigger-radius", 1.5);

        int lifetime = Math.max(1, baseLifetime + context.getDurationLevel() * lifetimePerDur);
        Location runeLoc = blockLocation.clone().add(0.5, 0.1, 0.5);
        double triggerRadiusSq = trigRadius * trigRadius;

        SpellContext runeContext = context.copy();
        boolean persistent = runeContext.isLingerPattern(); // 残留増強: トリガー後も消えない

        spawnRunePlaceFx(runeLoc);

        BukkitRunnable detectRunnable = new BukkitRunnable() {
            private int ticksElapsed = 0;
            private static final int GRACE_PERIOD = 20; // 設置後1秒間はトリガーしない
            private final java.util.Set<java.util.UUID> recentlyTriggered = new java.util.HashSet<>();
            private static final int RETRIGGER_COOLDOWN = 20; // 1秒クールダウン
            private final java.util.Map<java.util.UUID, Integer> triggerCooldowns = new java.util.HashMap<>();

            @Override
            public void run() {
                ticksElapsed++;

                if (ticksElapsed % PARTICLE_INTERVAL == 0) {
                    spawnRuneIdleFx(runeLoc);
                }

                // グレース期間中はトリガーしない
                if (ticksElapsed < GRACE_PERIOD) return;

                for (LivingEntity entity : runeLoc.getWorld().getNearbyLivingEntities(runeLoc, trigRadius)) {
                    if (entity.getLocation().distanceSquared(runeLoc) > triggerRadiusSq) continue;

                    // キャスター自身はルーンの発動対象外
                    if (entity.getUniqueId().equals(runeContext.getCasterUuid())) {
                        continue;
                    }

                    // 残留モード: クールダウンチェック
                    if (persistent) {
                        Integer lastTrigger = triggerCooldowns.get(entity.getUniqueId());
                        if (lastTrigger != null && (ticksElapsed - lastTrigger) < RETRIGGER_COOLDOWN) continue;
                        triggerCooldowns.put(entity.getUniqueId(), ticksElapsed);
                    }

                    if (!persistent) {
                        cancel();
                    }

                    // 後続グループがある場合はそれらを実行（遅延対応）
                    if (remainingEffects != null && !remainingEffects.isEmpty()) {
                        executeRemainingEffects(runeContext, remainingEffects, remainingAugments, 0, entity);
                    }
                    // 後続エフェクトなしの場合は何もしない
                    // (resolveOnEntityNoAoeを呼ぶとRuneEffectが再帰的に設置され無限ループになる)

                    spawnRuneTriggerFx(runeLoc);
                    if (!persistent) return;
                }
            }
        };
        detectRunnable.runTaskTimer(plugin, 1L, 1L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            detectRunnable.cancel();
            spawnRuneFadeFx(runeLoc);
        }, lifetime);
    }

    /**
     * 後続エフェクトを遅延対応で実行する。
     * delayTicksが設定されている場合、現在のエフェクトを実行後に残りを遅延スケジュールする。
     */
    private void executeRemainingEffects(SpellContext baseContext,
                                         List<SpellEffect> effects,
                                         List<List<SpellAugment>> augments,
                                         int startIndex,
                                         LivingEntity entity) {
        for (int i = startIndex; i < effects.size(); i++) {
            SpellContext ctx = baseContext.copy();
            ctx.resetPublicAugmentState();
            for (SpellAugment aug : augments.get(i)) {
                aug.modify(ctx);
            }

            if (ctx.getDelayTicks() > 0) {
                // 遅延エフェクト: 現在のエフェクトを実行し、残りを遅延後に再帰実行
                int delay = ctx.getDelayTicks();
                effects.get(i).applyToEntity(ctx, entity);
                final int nextIndex = i + 1;
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (entity.isValid() && !entity.isDead()) {
                        executeRemainingEffects(baseContext, effects, augments, nextIndex, entity);
                    }
                }, delay);
                return;
            }

            effects.get(i).applyToEntity(ctx, entity);
        }
    }

    private void spawnRunePlaceFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 30, 0.5, 0.2, 0.5, 0.5);
        loc.getWorld().spawnParticle(Particle.WITCH, loc, 15, 0.3, 0.1, 0.3, 0.1);
        loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    private void spawnRuneIdleFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 5, 0.4, 0.1, 0.4, 0.3);
    }

    private void spawnRuneTriggerFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.WITCH, loc, 25, 0.5, 0.5, 0.5, 0.2);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 20, 0.3, 0.3, 0.3, 1.0);
        loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    private void spawnRuneFadeFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 10, 0.3, 0.1, 0.3, 0.3);
    }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "ルーン"; }
    @Override public String getDescription() { return "地面にスペルトラップを設置する"; }
    @Override public int getManaCost() { return config.getManaCost("rune"); }
    @Override public int getTier() { return config.getTier("rune"); }
}
