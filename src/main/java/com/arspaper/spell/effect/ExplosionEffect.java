package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象位置で爆発を起こすEffect。
 * Ars Nouveau準拠:
 *   power = 0.75 + 0.5 × amp + 1.5 × aoe  (AOEで爆発範囲を大幅拡大)
 *   damage = 6.0 + 2.5 × amp
 *   setFire=false, breakBlocks=false (安全な爆発)
 *   Extract付き: 対象ブロックのアイテムドロップを有効化
 */
public class ExplosionEffect implements SpellEffect {

    private static final double DEFAULT_BASE_POWER = 0.5;
    private static final double DEFAULT_AMPLIFY_POWER_BONUS = 0.3;
    private static final double DEFAULT_AOE_POWER_BONUS = 1.0;
    private static final double DEFAULT_BASE_DAMAGE = 3.0;
    private static final double DEFAULT_AMPLIFY_DAMAGE_BONUS = 1.5;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public ExplosionEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "explosion");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int amplifyLevel = context.getAmplifyLevel();
        int aoeLevel = context.getAoeRadiusLevel();

        float power = (float)(config.getParam("explosion", "base-power", DEFAULT_BASE_POWER)
            + amplifyLevel * config.getParam("explosion", "amplify-power-bonus", DEFAULT_AMPLIFY_POWER_BONUS)
            + aoeLevel * config.getParam("explosion", "aoe-power-bonus", DEFAULT_AOE_POWER_BONUS));

        // 爆発エフェクト（ブロック破壊なし・着火なし）
        // createExplosion()が爆発範囲内のエンティティに自動でダメージを与えるため、
        // 別途target.damage()を呼ぶと二重ダメージになる
        target.getWorld().createExplosion(
            target.getLocation(), power, false, false, context.getCaster());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        int amplifyLevel = context.getAmplifyLevel();
        int aoeLevel = context.getAoeRadiusLevel();
        boolean hasExtract = context.getExtractCount() > 0;

        float power = (float)(config.getParam("explosion", "base-power", DEFAULT_BASE_POWER)
            + amplifyLevel * config.getParam("explosion", "amplify-power-bonus", DEFAULT_AMPLIFY_POWER_BONUS)
            + aoeLevel * config.getParam("explosion", "aoe-power-bonus", DEFAULT_AOE_POWER_BONUS));

        if (hasExtract) {
            Player caster = context.getCaster();
            // Extract付き: 爆発範囲のブロックをドロップさせる
            int radius = (int) Math.ceil(power);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        Location loc = blockLocation.clone().add(dx, dy, dz);
                        if (loc.distanceSquared(blockLocation) <= power * power) {
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

        // 爆発エフェクト（ブロック破壊なし・着火なし）
        blockLocation.getWorld().createExplosion(
            blockLocation, power, false, false, context.getCaster());
    }

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
