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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.arspaper.spell.SpellTaskLimiter;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 月輪 — 空中に月を召喚し、周囲の敵に防御無視の凍結ダメージを与え拘束する。
 *
 * 増幅: ダメージ威力上昇
 * 半径増加: 索敵範囲拡大
 * 延長/短縮: 召喚持続時間
 * 分裂: 発射頻度上昇（同時射出数増加）
 */
public class LunarEffect implements SpellEffect {

    private static final double BASE_DAMAGE = 4.0;
    private static final double AMPLIFY_DAMAGE_BONUS = 2.0;
    private static final double BASE_RADIUS = 8.0;
    private static final int BASE_DURATION = 200;            // 10秒
    private static final int DURATION_PER_LEVEL = 100;       // +5秒/段
    private static final int BASE_FIRE_INTERVAL = 20;        // 1秒ごと
    private static final int FREEZE_TICKS = 140;             // 7秒凍結（=最大凍結）
    private static final int SNARE_DURATION = 60;            // 3秒拘束

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public LunarEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "lunar");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティ頭上に召喚
        Location summonLoc = target.getLocation().add(0, target.getHeight() + 1.5, 0);
        startLunar(context, summonLoc);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック地面の2ブロック上
        Location summonLoc = blockLocation.clone().add(0.5, 2.5, 0.5);
        startLunar(context, summonLoc);
    }

    private void startLunar(SpellContext context, Location center) {
        Player caster = context.getCaster();
        if (caster == null) return;
        UUID casterUUID = caster.getUniqueId();

        double damage = config.getParam("lunar", "base-damage", BASE_DAMAGE)
            + config.getParam("lunar", "amplify-damage-bonus", AMPLIFY_DAMAGE_BONUS)
                * context.getAmplifyLevel();
        double radiusPerAoe = config.getParam("lunar", "radius-per-aoe", 1.0);
        double radius = config.getParam("lunar", "base-radius", BASE_RADIUS)
            + context.getAoeRadiusLevel() * radiusPerAoe;
        int baseDur = (int) config.getParam("lunar", "base-duration", BASE_DURATION);
        int durPerLevel = (int) config.getParam("lunar", "duration-per-level", DURATION_PER_LEVEL);
        int durationTicks = baseDur + context.getDurationLevel() * durPerLevel;
        int shotsPerVolley = 1 + context.getSplitCount();
        int cfgFireInterval = (int) config.getParam("lunar", "base-fire-interval", (double) BASE_FIRE_INTERVAL);
        int fireInterval = Math.max(5, cfgFireInterval / Math.max(1, shotsPerVolley));

        // 召喚音
        center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,
            SoundCategory.PLAYERS, 1.2f, 1.8f);

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

                // 月パーティクル（常時描画）
                if (ticks % 2 == 0) {
                    spawnMoonParticles(center);
                }

                // 凍結弾発射
                if (ticks % fireInterval == 0) {
                    List<LivingEntity> targets = center.getNearbyLivingEntities(radius).stream()
                        .filter(e -> !e.equals(onlineCaster))
                        .filter(e -> context.isValidAoeTarget(e, onlineCaster))
                        .sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)))
                        .limit(shotsPerVolley)
                        .toList();

                    for (LivingEntity target : targets) {
                        fireFrostProjectile(center, target, damage);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        SpellTaskLimiter.register("lunar", task);
    }

    private void fireFrostProjectile(Location from, LivingEntity target, double damage) {
        Location targetLoc = target.getLocation().add(0, target.getHeight() / 2, 0);

        // 凍結弾の軌跡パーティクル
        org.bukkit.util.Vector direction = targetLoc.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();
        for (double d = 0; d < distance; d += 0.5) {
            Location point = from.clone().add(direction.clone().multiply(d));
            from.getWorld().spawnParticle(Particle.SNOWFLAKE, point, 1, 0.05, 0.05, 0.05, 0.01);
            from.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0.05, 0.05, 0.05, 0.005);
        }

        // 防御無視ダメージ（直接HP減少）
        double finalHP = Math.max(0, target.getHealth() - damage);
        target.setHealth(finalHP);
        if (finalHP <= 0) return;

        // 凍結 + 拘束
        int freezeTicks = (int) config.getParam("lunar", "freeze-ticks", (double) FREEZE_TICKS);
        int snareDuration = (int) config.getParam("lunar", "snare-duration", (double) SNARE_DURATION);
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), freezeTicks));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, snareDuration, 2));

        // ヒット演出
        target.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, targetLoc,
            10, 0.2, 0.3, 0.2, 0, org.bukkit.Material.BLUE_ICE.createBlockData());
        target.getWorld().playSound(targetLoc, Sound.BLOCK_GLASS_BREAK,
            SoundCategory.PLAYERS, 0.5f, 1.8f);
    }

    private void spawnMoonParticles(Location center) {
        // 月の球体パーティクル（青白い三日月風）
        Particle.DustOptions silver = new Particle.DustOptions(Color.fromRGB(180, 200, 255), 2.0f);
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(100, 150, 255), 1.5f);

        int points = 12;
        double radius = 0.6;
        double time = System.currentTimeMillis() / 800.0;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points + time;
            // 三日月風: 半分だけ描画
            if (Math.cos(angle - time) > -0.3) {
                Location point = center.clone().add(
                    Math.cos(angle) * radius, Math.sin(angle * 0.7) * 0.3, Math.sin(angle) * radius);
                center.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, silver);
            }
        }
        // 中心の冷気
        center.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 1, 0.1, 0.1, 0.1, 0.01);
        center.getWorld().spawnParticle(Particle.DUST, center, 2, 0.1, 0.1, 0.1, 0, blue);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "月輪"; }
    @Override public String getDescription() { return "月を召喚し周囲の敵に凍結ダメージを与え拘束する"; }
    @Override public int getManaCost() { return config.getManaCost("lunar"); }
    @Override public int getTier() { return config.getTier("lunar"); }
}
