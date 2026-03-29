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

/**
 * 炎に関連した物理ダメージを与えるEffect。
 * 基本ダメージ: 3.0 (ハート1.5個分)
 * 炎上中のエンティティには1.5倍ダメージ。
 * ※ 閃炎と異なり、炎上していなくても基本ダメージが入る。
 *
 * 互換増強: 増幅/減衰（威力）、半径増加（ダメージエリア）、遅延、初期化のみ。
 */
public class ScorchEffect implements SpellEffect {

    private static final double DEFAULT_BASE_DAMAGE = 3.0;
    private static final double DEFAULT_AMPLIFY_BONUS = 2.0;
    private static final double FIRE_MULTIPLIER = 1.5;

    private final NamespacedKey id;
    private final GlyphConfig config;

    public ScorchEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "scorch");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double baseDamage = config.getParam("scorch", "base-damage", DEFAULT_BASE_DAMAGE);
        double amplifyBonus = config.getParam("scorch", "amplify-bonus", DEFAULT_AMPLIFY_BONUS);
        double damage = Math.max(0, baseDamage + context.getAmplifyLevel() * amplifyBonus);

        // 炎上状態の敵にダメージ1.5倍
        if (target.getFireTicks() > 0) {
            damage *= config.getParam("scorch", "fire-multiplier", FIRE_MULTIPLIER);
        }

        Player caster = context.getCaster();
        damage = context.calculateSpellDamage(damage, target);
        target.damage(damage, caster);
        spawnScorchFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnScorchFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.FLAME, effectLoc,
            15, 0.4, 0.5, 0.4, 0.1);
        effectLoc.getWorld().spawnParticle(Particle.SMOKE, effectLoc,
            8, 0.3, 0.4, 0.3, 0.05);
        effectLoc.getWorld().spawnParticle(Particle.LAVA, effectLoc,
            3, 0.2, 0.3, 0.2, 0.0);
        effectLoc.getWorld().playSound(effectLoc, Sound.ITEM_FIRECHARGE_USE,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "焦熱"; }

    @Override
    public String getDescription() { return "灼熱ダメージ。炎上中の敵に1.5倍"; }

    @Override
    public int getManaCost() { return config.getManaCost("scorch"); }

    @Override
    public int getTier() { return config.getTier("scorch"); }
}
