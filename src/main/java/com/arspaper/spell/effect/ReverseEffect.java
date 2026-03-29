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
import org.bukkit.util.Vector;

/**
 * 反転エフェクト。対象の移動方向を反転させる。
 * 毎tick、対象の水平移動ベクトルを逆転する（視点方向は正常のまま）。
 * 延長/短縮: 効果時間
 */
public class ReverseEffect implements SpellEffect {

    private static final int BASE_DURATION = 100;            // 5秒
    private static final int DURATION_PER_LEVEL = 60;        // +3秒/段

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

        new BukkitRunnable() {
            int ticks = 0;
            Location prevLoc = target.getLocation().clone();

            @Override
            public void run() {
                ticks += 3;
                if (ticks > durationTicks || target.isDead() || !target.isValid()) {
                    cancel();
                    return;
                }

                // 3tick間の移動差分を計算して水平方向を反転
                Location currentLoc = target.getLocation();
                double dx = currentLoc.getX() - prevLoc.getX();
                double dz = currentLoc.getZ() - prevLoc.getZ();

                if (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01) {
                    // 移動量を反転して追加適用（元の移動 + 逆方向の2倍 = 逆移動）
                    Vector reversal = new Vector(-dx * 2, 0, -dz * 2);
                    target.setVelocity(target.getVelocity().add(reversal));
                }

                prevLoc = currentLoc.clone();

                // 15tickごとにパーティクル
                if (ticks % 15 == 0) {
                    target.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        target.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0.05);
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
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
