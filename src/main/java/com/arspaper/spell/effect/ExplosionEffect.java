package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象位置で爆発を起こすEffect。
 * createExplosion()を使用。半径増加(aoe_radius)のみでpowerをスケール。
 * 増幅(amplify)は非互換 — 威力調整はglyphs.ymlの定数で行う。
 *
 * params:
 *   base-power: 基本爆発power (デフォルト: 1.5)
 *   aoe-power-bonus: 半径増加1段あたりのpower増加 (デフォルト: 0.8)
 *   max-power: 爆発power上限 (デフォルト: 6.0)
 *   damage-per-power: power1あたりの中心ダメージ (デフォルト: 3.0)
 *   damage-radius-multiplier: ダメージ判定半径 = power × この値 (デフォルト: 2.0)
 *
 * Extract付き: 範囲内ブロックをドロップさせる。
 * breakBlocks=false, setFire=false（安全な爆発）。
 *
 * <p>エンティティダメージはTFの対称パイプライン({@link SpellContext#dealSpellDamage})経由で与える。
 * バニラの{@code createExplosion}が生成するENTITY_EXPLOSION起因の{@link EntityDamageEvent}は
 * 自前の爆発だけを判定してキャンセルし（{@link #onVanillaExplosionDamage}）、二重ダメージを防ぐ
 * （TF側 {@code CombatListener#resolveAttacker} はENTITY_EXPLOSIONのattackerを解決できず
 * バニラの生ダメージがそのまま素通りしていたため=TFスケール未適用のバグ。2026-08-04修正）。
 */
public class ExplosionEffect implements SpellEffect, Listener {

    private static final double DEFAULT_BASE_POWER = 1.5;
    private static final double DEFAULT_AOE_POWER_BONUS = 0.8;
    private static final double DEFAULT_MAX_POWER = 6.0;
    private static final double DEFAULT_DAMAGE_PER_POWER = 3.0;
    private static final double DEFAULT_DAMAGE_RADIUS_MULTIPLIER = 2.0;
    private final NamespacedKey id;
    private final GlyphConfig config;

    /**
     * 「今まさに自分の createExplosion 呼び出し中」を示す深度カウンタ。
     * createExplosion は同期呼び出しの中でENTITY_EXPLOSIONのEntityDamageEventを即座に発火するため、
     * 呼び出しをこのカウンタで囲むだけで「バニラの爆発ダメージを判定してキャンセルする」ことができる。
     * boolean ではなく深度カウンタにするのは、Linger等で自爆発中に再度 applyToEntity/applyToBlock が
     * 呼ばれる（ネスト呼び出し）場合でも内側のfinallyが外側のマークを剥がさないようにするため
     * （{@code com.trinityforge.combat.MagicPipelineDamage} と同型のパターン）。
     */
    private int activeExplosionDepth = 0;

    public ExplosionEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "explosion");
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 自前の爆発が発生させたENTITY_EXPLOSIONダメージだけをキャンセルする。
     * バニラ側の生ダメージ適用を止め、エンティティへのダメージは
     * {@link #dealExplosionDamage} が別途TFパイプライン経由で与える。
     * 自分の createExplosion 呼び出し中(=activeExplosionDepth&gt;0)以外のENTITY_EXPLOSION
     * （TNT・クリーパー等）には一切干渉しない。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaExplosionDamage(EntityDamageEvent event) {
        if (activeExplosionDepth <= 0) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        event.setCancelled(true);
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        float power = calcPower(context);
        Location loc = target.getLocation();
        dealExplosionDamage(context, loc, power);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        float power = calcPower(context);
        boolean hasExtract = context.getExtractCount() > 0;

        // 爆発を先に実行（演出。エンティティダメージはdealExplosionDamage内でTF経由）
        dealExplosionDamage(context, blockLocation, power);

        // 抽出付き: 爆発後に範囲内ブロックをドロップ回収
        // 爆発後に行うことで、ドロップアイテムが爆発に巻き込まれて消失するのを防ぐ
        if (hasExtract) {
            Player caster = context.getCaster();
            int r = (int) Math.ceil(power);
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        Location loc = blockLocation.clone().add(dx, dy, dz);
                        if (loc.distanceSquared(blockLocation) > power * power) continue;
                        Block block = loc.getBlock();
                        if (!block.getType().isAir() && block.getType() != Material.BEDROCK) {
                            if (caster != null) {
                                BlockBreakEvent evt =
                                        SpellBreakMarker.callMarkedBreakEvent(block, caster);
                                if (evt.isCancelled()) continue;
                            }
                            block.breakNaturally();
                        }
                    }
                }
            }
        }
    }

    /** power = base + aoe_radius × bonus, maxで上限 */
    private float calcPower(SpellContext context) {
        double base = config.getParam("explosion", "base-power", DEFAULT_BASE_POWER);
        double bonus = config.getParam("explosion", "aoe-power-bonus", DEFAULT_AOE_POWER_BONUS);
        double max = config.getParam("explosion", "max-power", DEFAULT_MAX_POWER);
        return (float) Math.min(base + context.getAoeRadiusLevel() * bonus, max);
    }

    /**
     * 爆発の演出(音・見た目・ブロックへの物理挙動)はバニラ{@code createExplosion}に任せつつ、
     * エンティティへのダメージだけをTFの対称パイプライン経由で与える。
     *
     * <p>手順: (1) {@code activeExplosionDepth}を立ててから{@code createExplosion}を呼ぶ
     * （この間に発火するENTITY_EXPLOSIONの{@link EntityDamageEvent}は
     * {@link #onVanillaExplosionDamage}が全てキャンセルするので、バニラの生ダメージは
     * 一切適用されない＝二重ダメージにならない）。 (2) 中心からの距離減衰を掛けた基礎ダメージを
     * 対象範囲内の全{@link LivingEntity}へ{@link SpellContext#dealSpellDamage}で個別に与える。
     *
     * <p>増幅(amplify)は非互換（クラスjavadoc参照）なので常に無効化（{@code applyAmplifyDamageMultiplier=false}）。
     */
    private void dealExplosionDamage(SpellContext context, Location center, float power) {
        Player caster = context.getCaster();

        activeExplosionDepth++;
        try {
            center.getWorld().createExplosion(center, power, false, false, caster);
        } finally {
            activeExplosionDepth--;
        }
        spawnExplosionFx(center, power);

        if (caster == null) {
            // dealSpellDamageもcaster未特定では素通し(no-op)になるため、ここで早期return。
            // isValidAoeTarget(entity, null)はTameable判定でNPEになるため必ずcaster確定後に呼ぶこと。
            return;
        }

        double damagePerPower = config.getParam("explosion", "damage-per-power", DEFAULT_DAMAGE_PER_POWER);
        double radiusMultiplier = config.getParam(
                "explosion", "damage-radius-multiplier", DEFAULT_DAMAGE_RADIUS_MULTIPLIER);
        double centerDamage = damagePerPower * power;
        double radius = power * radiusMultiplier;
        if (centerDamage <= 0 || radius <= 0) {
            return;
        }

        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (!context.isValidAoeTarget(entity, caster)) continue;
            double distance = entity.getLocation().distance(center);
            double falloff = Math.max(0.0, 1.0 - distance / radius);
            double damage = centerDamage * falloff;
            if (damage <= 0) continue;
            context.dealSpellDamage(entity, damage, id.getKey(), false);
        }
    }

    private void spawnExplosionFx(Location loc, float power) {
        int particleCount = (int)(8 + power * 4);
        double spread = 0.3 + power * 0.15;
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, Math.min(particleCount, 25),
            spread, spread, spread, 0.05);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, particleCount,
            spread, spread, spread, 0.03);
    }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "爆発"; }

    @Override
    public String getDescription() { return "対象位置で爆発を起こす"; }

    @Override
    public int getManaCost() { return config.getManaCost("explosion"); }

    @Override
    public int getTier() { return config.getTier("explosion"); }

    @Override
    public boolean handlesAoeInternally() { return true; }
}
