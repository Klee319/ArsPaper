package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.arspaper.spell.SpellTaskLimiter;

/**
 * 反転エフェクト。対象の移動方向を反転させる。
 * テレポートベース: 移動差分を検出し、逆方向にテレポートで強制移動。
 * velocity操作ではなくテレポートを使用するためクライアント予測との衝突が発生しない。
 * 延長/短縮: 効果時間
 */
public class ReverseEffect implements SpellEffect {

    private static final int BASE_DURATION = 100;            // 5秒
    private static final int DURATION_PER_LEVEL = 60;        // +3秒/段
    private static final int CHECK_INTERVAL = 1;             // 毎tickチェック（単体対象のため負荷軽微）

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public ReverseEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "reverse");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("reverse", "base-duration", BASE_DURATION);
        int durationPerLevel = (int) config.getParam("reverse", "duration-per-level", DURATION_PER_LEVEL);
        int durationTicks = baseDuration + context.getDurationLevel() * durationPerLevel;

        spawnReverseFx(target.getLocation());

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            double prevX = target.getLocation().getX();
            double prevZ = target.getLocation().getZ();

            @Override
            public void run() {
                ticks++;
                if (ticks > durationTicks || target.isDead() || !target.isValid()) {
                    cancel();
                    return;
                }

                Location currentLoc = target.getLocation();
                double dx = currentLoc.getX() - prevX;
                double dz = currentLoc.getZ() - prevZ;

                // 移動閾値（静止中は無視）
                if (Math.abs(dx) > 0.03 || Math.abs(dz) > 0.03) {
                    // 移動方向を反転してテレポート（元の位置から逆方向へ）
                    Location reversed = currentLoc.clone();
                    reversed.setX(prevX - dx);
                    reversed.setZ(prevZ - dz);
                    // yaw/pitchは現在の視線方向を維持
                    reversed.setYaw(currentLoc.getYaw());
                    reversed.setPitch(currentLoc.getPitch());

                    target.teleport(reversed);

                    // 次回の基準は反転後の位置
                    prevX = reversed.getX();
                    prevZ = reversed.getZ();
                } else {
                    prevX = currentLoc.getX();
                    prevZ = currentLoc.getZ();
                }

                // パーティクル（10tickごと）
                if (ticks % 10 == 0) {
                    target.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        target.getLocation().add(0, 1, 0), 5, 0.2, 0.3, 0.2, 0.05);
                }
            }
        }.runTaskTimer(plugin, 0L, CHECK_INTERVAL);
        SpellTaskLimiter.register("reverse", task);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    private void spawnReverseFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 1, 0),
            20, 0.4, 0.5, 0.4, 0.1);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0),
            15, 0.3, 0.4, 0.3, 0.5);
        loc.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER,
            SoundCategory.PLAYERS, 0.4f, 2.0f);
    }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "反転"; }
    @Override public String getDescription() { return "対象の移動方向を反転させる"; }
    @Override public int getManaCost() { return config.getManaCost("reverse"); }
    @Override public int getTier() { return config.getTier("reverse"); }
}
