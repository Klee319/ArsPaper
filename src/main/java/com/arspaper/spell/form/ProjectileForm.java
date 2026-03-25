package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.metadata.FixedMetadataValue;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * 飛び道具を発射し、命中した対象にEffectチェーンを適用するForm。
 * バニラのSnowballエンティティを利用。
 */
public class ProjectileForm implements SpellForm {

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public ProjectileForm(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "projectile");
        this.config = config;
    }

    private static final int MAX_TRAIL_TICKS = 200; // 10秒でタイムアウト
    private static final double SPREAD_ANGLE_STEP = 0.15; // ラジアン単位のスプレッド角度

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);

        double speed = 2.0 * Math.min(context.getProjectileSpeedMultiplier(), 4.0)
            + context.getReachLevel() * 0.5; // 延伸: 射程延長（速度加算）
        int totalProjectiles = 1 + Math.min(context.getSplitCount(), 8);

        Vector baseDirection = caster.getLocation().getDirection();

        for (int i = 0; i < totalProjectiles; i++) {
            Vector direction = baseDirection.clone();
            if (totalProjectiles > 1) {
                // 対称的な扇形スプレッド: 中心を基準に均等配置
                double angle = (i - (totalProjectiles - 1) / 2.0) * SPREAD_ANGLE_STEP;
                direction.rotateAroundY(angle);
            }

            // 各プロジェクタイルに独立したSpellContextコピーを渡す
            SpellContext projectileContext = context.copy();

            // 手元から発射（目線位置から-0.4Y下）
            Location handPos = caster.getEyeLocation().clone().add(0, -0.4, 0);
            final Vector finalDir = direction.clone().multiply(speed);
            Snowball projectile = caster.getWorld().spawn(handPos, Snowball.class, s -> {
                s.setShooter(caster);
                s.setVelocity(finalDir);
            });
            projectile.setMetadata("ars_spell_context", new FixedMetadataValue(plugin, projectileContext));
            projectile.setGlowing(true);

            // 軌跡パーティクル + 軌跡エフェクト（タイムアウト付き）
            boolean traceMode = projectileContext.isTraceActive();
            new BukkitRunnable() {
                private int ticks = 0;
                private final java.util.Set<Location> processedBlocks = new java.util.HashSet<>();

                @Override
                public void run() {
                    ticks++;
                    if (ticks > MAX_TRAIL_TICKS || projectile.isDead() || !projectile.isValid()) {
                        cancel();
                        return;
                    }
                    if (ticks % 2 == 0) {
                        SpellFxUtil.spawnProjectileTrail(projectile.getLocation());
                    }

                    // 軌跡モード: 飛行経路上のブロックにも効果適用（召喚系等はスキップ）
                    if (traceMode) {
                        Location blockLoc = projectile.getLocation().getBlock().getLocation();
                        if (processedBlocks.add(blockLoc)) {
                            SpellContext trailCtx = projectileContext.copy();
                            trailCtx.resolveOnBlockTrace(blockLoc);
                        }
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "投射"; }

    @Override
    public String getDescription() { return "魔力弾を発射し、命中した対象に効果を適用する"; }

    @Override
    public int getManaCost() { return config.getManaCost("projectile"); }

    @Override
    public int getTier() { return config.getTier("projectile"); }
}
