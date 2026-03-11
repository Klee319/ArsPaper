package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.metadata.FixedMetadataValue;
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

    public ProjectileForm(JavaPlugin plugin) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "projectile");
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);

        double speed = 2.0 * context.getProjectileSpeedMultiplier();
        int totalProjectiles = 1 + context.getSplitCount();

        Vector baseDirection = caster.getLocation().getDirection();

        for (int i = 0; i < totalProjectiles; i++) {
            Vector direction = baseDirection.clone();
            if (totalProjectiles > 1 && i > 0) {
                double spread = 0.15 * i;
                direction.rotateAroundY((i % 2 == 0 ? 1 : -1) * spread * ((i + 1) / 2));
            }

            Snowball projectile = caster.launchProjectile(Snowball.class);
            projectile.setVelocity(direction.multiply(speed));
            projectile.setMetadata("ars_spell_context", new FixedMetadataValue(plugin, context));
            projectile.setGlowing(true);

            // 軌跡パーティクル
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (projectile.isDead() || !projectile.isValid()) {
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
    public String getDisplayName() { return "Projectile"; }

    @Override
    public int getManaCost() { return 5; }

    @Override
    public int getTier() { return 1; }
}
