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
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象を現在の位置にマークし、一定時間後にテレポートで引き戻すEffect。
 * Ars Nouveau Tier 3準拠:
 *   - エンティティ対象: 現在位置を記録し、delay後にその位置へテレポート
 *   - 遅延: 60 tick (3秒) + durationLevel × 40 tick (2秒)
 *   - ブロック対象: NoOp
 */
public class RewindEffect implements SpellEffect {

    private static final int BASE_DELAY_TICKS = 60;
    private static final int DELAY_PER_LEVEL_TICKS = 40;

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public RewindEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "rewind");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int durationLevel = context.getDurationLevel();
        int delayTicks = Math.max(1, BASE_DELAY_TICKS + durationLevel * DELAY_PER_LEVEL_TICKS);

        // 現在位置を記録（マーク）
        Location markedLocation = target.getLocation().clone();

        // マーク時のエフェクト
        spawnRewindMarkFx(markedLocation);

        // delay後にマーク位置へテレポート
        final LivingEntity finalTarget = target;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (finalTarget.isDead() || !finalTarget.isValid()) {
                return;
            }
            Location returnLoc = markedLocation.clone();
            returnLoc.setYaw(finalTarget.getLocation().getYaw());
            returnLoc.setPitch(finalTarget.getLocation().getPitch());

            // テレポート前のエフェクト（現在位置）
            spawnRewindTeleportFx(finalTarget.getLocation());

            finalTarget.teleport(returnLoc);

            // テレポート後のエフェクト（戻り先）
            spawnRewindTeleportFx(returnLoc);
        }, delayTicks);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnRewindMarkFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, effectLoc, 25, 0.3, 0.5, 0.3, 0.05);
        effectLoc.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 10, 0.3, 0.5, 0.3, 0.05);
        effectLoc.getWorld().playSound(effectLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
            SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    private void spawnRewindTeleportFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, effectLoc, 40, 0.4, 0.6, 0.4, 0.1);
        effectLoc.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 15, 0.3, 0.5, 0.3, 0.08);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "巻き戻し"; }

    @Override
    public String getDescription() { return "対象を元の位置に引き戻す"; }

    @Override
    public int getManaCost() { return config.getManaCost("rewind"); }

    @Override
    public int getTier() { return config.getTier("rewind"); }
}
