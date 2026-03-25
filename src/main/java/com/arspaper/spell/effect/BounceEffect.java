package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 対象にBounce（跳弾）効果を付与するEffect。
 * スライムブロックのような挙動: 落下ダメージ無効 + 着地時に跳ね返る。
 * 基本持続: 30秒(600tick) + durationLevel毎に8秒(160tick)
 * Amplify: バウンス時のエネルギー保存率が向上
 */
public class BounceEffect implements SpellEffect, Listener {

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;
    private boolean taskStarted = false;

    /** 跳弾中エンティティの管理データ */
    private static final Map<UUID, BounceData> bouncingEntities = new ConcurrentHashMap<>();

    private record BounceData(long expiryTick, int amplifyLevel, double lastYVelocity) {}

    public BounceEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "bounce");
        this.config = config;
        this.plugin = plugin;
    }

    /**
     * バウンス検出タスクを開始する（プラグイン起動時に1回だけ呼ぶ）。
     */
    public void startBounceTask() {
        if (taskStarted) return;
        taskStarted = true;

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            var iterator = bouncingEntities.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                UUID uuid = entry.getKey();
                BounceData data = entry.getValue();

                Entity entity = Bukkit.getEntity(uuid);
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    iterator.remove();
                    continue;
                }

                long currentTick = entity.getWorld().getGameTime();
                if (currentTick > data.expiryTick) {
                    iterator.remove();
                    continue;
                }

                double currentY = entity.getVelocity().getY();

                // 着地検出: 前tickで落下中 + 今tickで地面
                if (data.lastYVelocity < -0.1 && entity.isOnGround()) {
                    // バウンス力計算: 落下速度に比例
                    double bounceMultiplier = 0.7 + data.amplifyLevel * 0.1;
                    bounceMultiplier = Math.min(bounceMultiplier, 1.2);
                    double bounceSpeed = Math.abs(data.lastYVelocity) * bounceMultiplier;
                    bounceSpeed = Math.max(0.35, Math.min(bounceSpeed, 4.0));

                    Vector vel = entity.getVelocity();
                    entity.setVelocity(new Vector(vel.getX() * 0.9, bounceSpeed, vel.getZ() * 0.9));
                    entity.setFallDistance(0f);

                    // バウンスエフェクト
                    Location loc = entity.getLocation();
                    loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 6, 0.3, 0.1, 0.3, 0.05);
                    loc.getWorld().playSound(loc, Sound.BLOCK_SLIME_BLOCK_FALL,
                        SoundCategory.PLAYERS, 0.5f, 1.2f);

                    // 更新（バウンス後の速度を記録）
                    entry.setValue(new BounceData(data.expiryTick, data.amplifyLevel, bounceSpeed));
                } else {
                    // lastYVelocityを更新
                    entry.setValue(new BounceData(data.expiryTick, data.amplifyLevel, currentY));
                }
            }
        }, 0L, 1L);
    }

    /**
     * 落下ダメージをキャンセルする。
     */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        BounceData data = bouncingEntities.get(entity.getUniqueId());
        if (data == null) return;

        long currentTick = entity.getWorld().getGameTime();
        if (currentTick > data.expiryTick) {
            bouncingEntities.remove(entity.getUniqueId());
            return;
        }

        event.setCancelled(true);
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("bounce", "base-duration", 600);
        int durationPerLevel = (int) config.getParam("bounce", "duration-per-level", 160);
        int durationTicks = baseDuration + context.getDurationLevel() * durationPerLevel;
        int amplifyLevel = Math.max(0, context.getAmplifyLevel());

        long expiryTick = target.getWorld().getGameTime() + durationTicks;
        bouncingEntities.put(target.getUniqueId(), new BounceData(expiryTick, amplifyLevel, 0));

        // タスクが未開始なら開始
        startBounceTask();

        SpellFxUtil.spawnBounceFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    /**
     * 全バウンス状態をクリアする（プラグイン終了時）。
     */
    public static void cleanupAll() {
        bouncingEntities.clear();
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "跳弾"; }

    @Override
    public String getDescription() { return "着地時に自動で跳ね返る効果を付与"; }

    @Override
    public int getManaCost() { return config.getManaCost("bounce"); }

    @Override
    public int getTier() { return config.getTier("bounce"); }
}
