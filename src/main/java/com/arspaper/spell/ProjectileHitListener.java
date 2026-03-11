package com.arspaper.spell;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

/**
 * ProjectileFormで発射したSnowballの着弾を処理するリスナー。
 * PierceAugmentによる貫通をサポート。
 */
public class ProjectileHitListener implements Listener {

    private static final String META_KEY = "ars_spell_context";
    private static final String META_PIERCE_REMAINING = "ars_pierce_remaining";

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        List<MetadataValue> metadata = event.getEntity().getMetadata(META_KEY);
        if (metadata.isEmpty()) return;

        Object value = metadata.get(0).value();
        if (!(value instanceof SpellContext context)) return;

        if (event.getHitEntity() instanceof LivingEntity target) {
            SpellFxUtil.spawnImpactBurst(target.getLocation());
            context.resolveOnEntity(target);

            // Pierce処理: 残り貫通回数が0より大きければ弾を維持
            int pierceRemaining = getPierceRemaining(event);
            if (pierceRemaining > 0) {
                event.setCancelled(true); // 弾の消滅をキャンセル
                setPierceRemaining(event, pierceRemaining - 1);
                return;
            }
        } else if (event.getHitBlock() != null) {
            SpellFxUtil.spawnImpactBurst(event.getHitBlock().getLocation().add(0.5, 0.5, 0.5));
            context.resolveOnBlock(event.getHitBlock().getLocation());
        }

        event.getEntity().remove();
    }

    private int getPierceRemaining(ProjectileHitEvent event) {
        List<MetadataValue> meta = event.getEntity().getMetadata(META_PIERCE_REMAINING);
        if (meta.isEmpty()) {
            // 初回ヒット: SpellContextからpierceCountを取得して初期化
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
