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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 対象を指定方向にブーストするEffect。
 *
 * 自己使用: キャスターの視線方向にブースト（下方向も可）
 * 他エンティティ使用: キャスターの視線先の方向にブースト
 *   - 延長/短縮: 視線先の判定距離（デフォルト10ブロック、±5ブロック/レベル）
 *   - 範囲: 他エンティティへの判定範囲拡大
 * ブロック: 効果なし
 *
 * 増幅/減衰: ブースト力
 * 無作為: ランダム方向にブースト
 */
public class SpeedBoostEffect implements SpellEffect {

    private static final double BASE_SPEED = 1.2;
    private static final double AMPLIFY_BONUS = 0.5;
    private static final double MAX_SPEED = 4.0;
    private static final double BASE_AIM_RANGE = 10.0;
    private static final double AIM_RANGE_PER_DURATION = 5.0;

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SpeedBoostEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "speed_boost");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        if (caster == null) return;

        double baseSpeed = config.getParam("speed_boost", "base-speed", BASE_SPEED);
        double amplifyBonus = config.getParam("speed_boost", "amplify-bonus", AMPLIFY_BONUS);
        double maxSpeed = config.getParam("speed_boost", "max-speed", MAX_SPEED);
        double baseAimRange = config.getParam("speed_boost", "base-aim-range", BASE_AIM_RANGE);
        double speed = Math.min(
            baseSpeed + context.getAmplifyLevel() * amplifyBonus,
            maxSpeed
        );
        speed = Math.max(0.3, speed);

        Vector direction;

        if (context.isRandomizing()) {
            // 無作為: ランダム方向
            direction = randomDirection();
        } else if (target.equals(caster)) {
            // 自己使用: キャスターの視線方向
            direction = caster.getLocation().getDirection();
        } else {
            // 他エンティティ使用: キャスターの視線先の方向にブースト
            double aimRangePerReach = config.getParam("speed_boost", "aim-range-per-reach", AIM_RANGE_PER_DURATION);
            double aimRange = Math.max(5.0,
                baseAimRange + context.getReachLevel() * aimRangePerReach);
            direction = getAimDirection(caster, aimRange);
        }

        target.setVelocity(direction.normalize().multiply(speed));

        // エフェクト
        Location effectLoc = target.getLocation().add(0, 0.5, 0);
        effectLoc.getWorld().spawnParticle(Particle.CLOUD, effectLoc, 10, 0.2, 0.2, 0.2, 0.1);
        effectLoc.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 5, 0.1, 0.1, 0.1, 0.15);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_BREEZE_SHOOT,
            SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    /**
     * キャスターの視線先のブロック/空中地点への方向ベクトルを返す。
     * 視線先にブロックがあればその地点、なければ最大距離地点。
     */
    private Vector getAimDirection(Player caster, double aimRange) {
        Location eye = caster.getEyeLocation();
        Vector lookDir = eye.getDirection();

        RayTraceResult blockHit = caster.getWorld().rayTraceBlocks(
            eye, lookDir, aimRange,
            org.bukkit.FluidCollisionMode.NEVER, true);

        Location aimPoint;
        if (blockHit != null) {
            aimPoint = blockHit.getHitPosition().toLocation(caster.getWorld());
        } else {
            aimPoint = eye.clone().add(lookDir.clone().multiply(aimRange));
        }

        return aimPoint.toVector().subtract(caster.getLocation().toVector()).normalize();
    }

    /**
     * ランダムな方向ベクトルを返す。
     */
    private Vector randomDirection() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double x = rand.nextDouble(-1, 1);
        double y = rand.nextDouble(-0.5, 1); // 上方向バイアス
        double z = rand.nextDouble(-1, 1);
        Vector v = new Vector(x, y, z);
        return v.length() > 0 ? v.normalize() : new Vector(0, 1, 0);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象は効果なし
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "指向"; }

    @Override
    public String getDescription() { return "視線方向にブーストする"; }

    @Override
    public int getManaCost() { return config.getManaCost("speed_boost"); }

    @Override
    public int getTier() { return config.getTier("speed_boost"); }
}
