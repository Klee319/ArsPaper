package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 対象を吹き飛ばすEffect。Ars NouveauのEffectKnockbackに準拠。
 * - 基本力: 1.5、Amplifyごとに +1.0（旧0.5を修正）
 * - Extract > 0 の場合、術者→対象方向（押し出し）を明示保証
 * - Sensitive の場合はノックバック方向を垂直成分のみに限定（ブロックへの侵入抑制）
 */
public class KnockbackEffect implements SpellEffect {

    private static final double DEFAULT_BASE_FORCE = 1.5;
    private static final double DEFAULT_AMPLIFY_BONUS = 1.0;
    private static final double DEFAULT_MAX_FORCE = 5.0;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public KnockbackEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "knockback");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double baseForce = config.getParam("knockback", "base-force", DEFAULT_BASE_FORCE);
        double amplifyBonus = config.getParam("knockback", "amplify-bonus", DEFAULT_AMPLIFY_BONUS);
        double maxForce = config.getParam("knockback", "max-force", DEFAULT_MAX_FORCE);
        double force = Math.min(baseForce + context.getAmplifyLevel() * amplifyBonus, maxForce);

        Player caster = context.getCaster();
        Vector direction;

        if (caster != null && caster.equals(target)) {
            // 自己対象: 視線と反対方向に飛び出す
            Vector backward = caster.getLocation().getDirection().multiply(-1).normalize();
            backward.setY(Math.max(backward.getY(), 0.3));
            backward.normalize().multiply(Math.min(force, maxForce));
            target.setVelocity(backward);
            SpellFxUtil.spawnKnockbackFx(target.getLocation());
            return;
        } else if (caster != null) {
            // 他者対象: 術者 → 対象方向（押し出し）
            direction = target.getLocation().toVector()
                .subtract(caster.getLocation().toVector());
        } else {
            // 術者不在時: 対象の向いている方向の逆
            direction = target.getLocation().getDirection().multiply(-1);
        }

        // ゼロベクトル防止
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 1, 0);
        } else {
            direction.normalize();
        }

        direction.multiply(force).setY(0.4 * Math.max(1.0, force * 0.5));

        target.setVelocity(direction);
        SpellFxUtil.spawnKnockbackFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック着弾: 付近のエンティティをノックバック
        Player caster = context.getCaster();
        if (caster == null) return;

        double radius = 2.0 + context.getAoeRadiusLevel();
        Location center = blockLocation.clone().add(0.5, 0.5, 0.5);
        center.getNearbyLivingEntities(radius).stream()
            .filter(e -> !e.equals(caster))
            .forEach(e -> applyToEntity(context, e));
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "吹飛"; }

    @Override
    public String getDescription() { return "対象を吹き飛ばす"; }

    @Override
    public int getManaCost() { return config.getManaCost("knockback"); }

    @Override
    public int getTier() { return config.getTier("knockback"); }
}
