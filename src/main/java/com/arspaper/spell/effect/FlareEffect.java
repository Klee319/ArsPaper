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
 * 炎上中のエンティティに追加バーストダメージを与えるEffect。
 * Ars Nouveau Tier 2準拠:
 *   対象が炎上中の場合: 6.0 + 2.5 × amplifyLevel のバーストダメージ
 *   炎上していない場合: 何もしない
 * 着弾時に炎パーティクルエフェクトを表示する。
 */
public class FlareEffect implements SpellEffect {

    private static final double BASE_DAMAGE = 6.0;
    private static final double AMPLIFY_BONUS = 2.5;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public FlareEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "flare");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // 炎上していない場合は何もしない
        if (target.getFireTicks() <= 0) {
            return;
        }

        double baseDamage = config.getParam("flare", "base-damage", BASE_DAMAGE);
        double amplifyBonus = config.getParam("flare", "amplify-bonus", AMPLIFY_BONUS);
        double damage = baseDamage + context.getAmplifyLevel() * amplifyBonus;
        target.damage(damage, context.getCaster());

        spawnFlareFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    /**
     * 炎バースト時のパーティクル・サウンドエフェクト。
     */
    private void spawnFlareFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.FLAME, effectLoc, 20, 0.4, 0.5, 0.4, 0.15);
        effectLoc.getWorld().spawnParticle(Particle.FIREWORK, effectLoc, 10, 0.3, 0.4, 0.3, 0.08);
        effectLoc.getWorld().spawnParticle(Particle.LAVA, effectLoc, 5, 0.2, 0.3, 0.2, 0.0);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_BLAZE_SHOOT,
            SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "閃炎"; }

    @Override
    public String getDescription() { return "炎上中の対象にバーストダメージを与える"; }

    @Override
    public int getManaCost() { return config.getManaCost("flare"); }

    @Override
    public int getTier() { return config.getTier("flare"); }
}
