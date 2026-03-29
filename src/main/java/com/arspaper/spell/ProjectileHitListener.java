package com.arspaper.spell;

import com.arspaper.spell.form.BurstForm;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

/**
 * ProjectileForm/BurstFormで発射した飛翔体の着弾を処理するリスナー。
 * PierceAugmentによる貫通をサポート。
 */
public class ProjectileHitListener implements Listener {

    private static final String META_KEY = "ars_spell_context";
    private static final String META_PIERCE_REMAINING = "ars_pierce_remaining";
    private static final String META_BURST = "ars_burst_form";

    public ProjectileHitListener() {
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        List<MetadataValue> metadata = event.getEntity().getMetadata(META_KEY);
        if (metadata.isEmpty()) return;

        Object value = metadata.get(0).value();
        if (!(value instanceof SpellContext context)) return;

        // 炸裂フォームの場合は炸裂処理に委譲
        boolean isBurst = !event.getEntity().getMetadata(META_BURST).isEmpty();

        if (isBurst) {
            // 炸裂: エンティティヒット時は貫通処理+炸裂
            if (event.getHitEntity() instanceof LivingEntity target) {
                int pierceRemaining = getPierceRemaining(event);
                if (pierceRemaining > 0) {
                    // 貫通: エンティティに効果適用して飛行継続
                    SpellContext hitContext = context.copy();
                    hitContext.resolveOnEntity(target);
                    event.setCancelled(true);
                    setPierceRemaining(event, pierceRemaining - 1);
                    return;
                }
            }
            // 炸裂起爆（ヒットまたはブロック着弾）
            Location detonationPoint = event.getHitBlock() != null
                ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
                : event.getEntity().getLocation();
            Player shooter = event.getEntity().getShooter() instanceof Player p ? p : null;
            GlyphConfig gc = com.arspaper.ArsPaper.getInstance().getGlyphConfig();
            double baseBurstRadius = gc.getParam("burst", "base-burst-radius", 2.0);
            double radiusPerAoe = gc.getParam("burst", "radius-per-aoe", 1.5);
            double burstRadius = baseBurstRadius + context.getAoeRadiusLevel() * radiusPerAoe;
            BurstForm.detonate(detonationPoint, context, burstRadius, shooter);
            event.getEntity().remove();
            return;
        }

        // 通常の投射フォーム処理
        if (event.getHitEntity() instanceof LivingEntity target) {
            // Pierce処理
            int pierceRemaining = getPierceRemaining(event);

            SpellFxUtil.spawnImpactBurst(target.getLocation());
            // 貫通時にオリジナルcontextのForm-level状態(trace等)が破壊されるのを防止
            SpellContext hitContext = context.copy();
            hitContext.resolveOnEntity(target);

            if (pierceRemaining > 0) {
                event.setCancelled(true);
                setPierceRemaining(event, pierceRemaining - 1);
                return;
            }
        } else if (event.getHitBlock() != null) {
            Location blockLoc = event.getHitBlock().getLocation();
            SpellFxUtil.spawnImpactBurst(blockLoc.clone().add(0.5, 0.5, 0.5));
            if (event.getHitBlockFace() != null) {
                context.setHitFace(event.getHitBlockFace());
            }
            context.resolveOnBlock(blockLoc);
        }

        event.getEntity().remove();
    }

    private int getPierceRemaining(ProjectileHitEvent event) {
        List<MetadataValue> meta = event.getEntity().getMetadata(META_PIERCE_REMAINING);
        if (meta.isEmpty()) {
            List<MetadataValue> ctxMeta = event.getEntity().getMetadata(META_KEY);
            if (!ctxMeta.isEmpty() && ctxMeta.get(0).value() instanceof SpellContext ctx) {
                return ctx.getPierceCount();
            }
            return 0;
        }
        return meta.get(0).asInt();
    }

    private void setPierceRemaining(ProjectileHitEvent event, int remaining) {
        MetadataValue contextMeta = event.getEntity().getMetadata(META_KEY).get(0);
        event.getEntity().setMetadata(META_PIERCE_REMAINING,
            new org.bukkit.metadata.FixedMetadataValue(contextMeta.getOwningPlugin(), remaining));
    }
}
