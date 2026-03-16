package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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

        double speed = 2.0 * Math.min(context.getProjectileSpeedMultiplier(), 4.0);
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

            // Velocity をアトミックに設定（launch後のsetVelocityによるレースを回避）
            Snowball projectile = caster.launchProjectile(Snowball.class, direction.multiply(speed));
            projectile.setMetadata("ars_spell_context", new FixedMetadataValue(plugin, projectileContext));
            projectile.setGlowing(true);

            // 軌跡パーティクル（タイムアウト付き）
            new BukkitRunnable() {
                private int ticks = 0;

                @Override
                public void run() {
                    ticks++;
                    if (ticks > MAX_TRAIL_TICKS || projectile.isDead() || !projectile.isValid()) {
                        cancel();
                        return;
                    }
                    SpellFxUtil.spawnProjectileTrail(projectile.getLocation());
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
