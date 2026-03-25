package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 衝撃位置周囲のエンティティを吹き飛ばす風爆発Effect。
 * Ars Nouveau Tier 2準拠:
 *   - 衝撃点から半径 (3.0 + aoeLevel) 内の全LivingEntityを吹き飛ばす
 *   - ノックバック力: 1.0 + 0.5 × amplifyLevel
 *   - Sensitive: 発動者はノックバックを受けない
 *   - ブロック対象にも適用可（衝撃点中心に周囲エンティティを吹き飛ばす）
 */
public class WindBurstEffect implements SpellEffect {

    private static final double BASE_RADIUS = 3.0;
    private static final double BASE_FORCE = 1.0;
    private static final double AMPLIFY_FORCE_BONUS = 0.5;

    private final NamespacedKey id;
    private final GlyphConfig config;

    public WindBurstEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "wind_burst");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        double baseForce = config.getParam("wind_burst", "base-force", BASE_FORCE);
        double amplifyForceBonus = config.getParam("wind_burst", "amplify-force-bonus", AMPLIFY_FORCE_BONUS);
        double force = baseForce + amplifyForceBonus * context.getAmplifyLevel();

        if (caster != null && caster.equals(target)) {
            // 自己対象: 視線と反対方向に飛び出す
            Vector backward = caster.getLocation().getDirection().multiply(-1).normalize();
            backward.setY(Math.max(backward.getY(), 0.3));
            backward.normalize().multiply(force);
            target.setVelocity(backward);
            spawnWindBurstFx(target.getLocation());
            return;
        }

        // 他者対象: 衝撃点からノックバック
        Location impactPoint = target.getLocation();
        knockbackAway(target, impactPoint, force);
        spawnWindBurstFx(impactPoint);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象: 衝撃点中心に周囲エンティティをノックバック
        // （ブロックにはSpellContextのAOEエンティティスキャンが効かないため自前でスキャン）
        Player caster = context.getCaster();
        int amplifyLevel = context.getAmplifyLevel();
        int aoeLevel = context.getAoeRadiusLevel();
        Location impactPoint = blockLocation.clone().add(0.5, 0.5, 0.5);
        double baseRadius = config.getParam("wind_burst", "base-radius", BASE_RADIUS);
        double baseForceB = config.getParam("wind_burst", "base-force", BASE_FORCE);
        double amplifyForceBonusB = config.getParam("wind_burst", "amplify-force-bonus", AMPLIFY_FORCE_BONUS);
        double radius = baseRadius + aoeLevel;
        double force = baseForceB + amplifyForceBonusB * amplifyLevel;

        for (LivingEntity nearby : impactPoint.getNearbyLivingEntities(radius)) {
            if (caster != null && nearby.equals(caster)) continue;
            knockbackAway(nearby, impactPoint, force);
        }

        spawnWindBurstFx(impactPoint);
    }

    /**
     * エンティティを衝撃点から遠ざける方向にノックバックさせる。
     */
    private void knockbackAway(LivingEntity entity, Location impactPoint, double force) {
        Vector direction = entity.getLocation().toVector()
            .subtract(impactPoint.toVector());

        // 距離が0の場合（エンティティが衝撃点に重なっている場合）はランダム上方向
        if (direction.lengthSquared() < 0.0001) {
            direction = new Vector(0, 1, 0);
        } else {
            direction.normalize();
        }

        // 上方向成分を追加して浮き上がるように
        direction.setY(Math.max(direction.getY(), 0.4));
        direction.normalize().multiply(force);

        entity.setVelocity(direction);
    }

    private void spawnWindBurstFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 30, 1.5, 0.5, 1.5, 0.15);
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 5, 0.5, 0.2, 0.5, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_WIND_CHARGE_WIND_BURST,
            SoundCategory.PLAYERS, 1.0f, 0.9f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "突風"; }

    @Override
    public String getDescription() { return "風の爆発で周囲を吹き飛ばす"; }

    @Override
    public int getManaCost() { return config.getManaCost("wind_burst"); }

    @Override
    public int getTier() { return config.getTier("wind_burst"); }
}
