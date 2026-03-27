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
import org.bukkit.event.block.BlockBreakEvent;
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
 *
 * Extract付き: 範囲内ブロックをドロップさせる。
 * breakBlocks=false, setFire=false（安全な爆発）。
 */
public class ExplosionEffect implements SpellEffect {

    private static final double DEFAULT_BASE_POWER = 1.5;
    private static final double DEFAULT_AOE_POWER_BONUS = 0.8;
    private static final double DEFAULT_MAX_POWER = 6.0;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public ExplosionEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "explosion");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        float power = calcPower(context);
        Location loc = target.getLocation();
        loc.getWorld().createExplosion(loc, power, false, false, context.getCaster());
        spawnExplosionFx(loc, power);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        float power = calcPower(context);
        boolean hasExtract = context.getExtractCount() > 0;

        // 爆発を先に実行（演出＋エンティティダメージ。breakBlocks=false）
        blockLocation.getWorld().createExplosion(
            blockLocation, power, false, false, context.getCaster());
        spawnExplosionFx(blockLocation, power);

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
                                BlockBreakEvent evt = new BlockBreakEvent(block, caster);
                                Bukkit.getPluginManager().callEvent(evt);
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
