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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象に浮遊（Levitation）を付与するEffect。
 * エンティティ: 浮遊ポーション効果を付与して上昇させる。
 * Amplify: 浮遊レベル上昇（上昇速度↑）
 * ExtendTime: 持続時間延長
 * Dampen: 浮遊レベル低下（ゆっくり上昇）
 * ブロック: NoOp
 */
public class LevitateEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION_TICKS = 60;   // 3秒
    private static final int DEFAULT_DURATION_BONUS_TICKS = 40;   // +2秒/段階

    private final NamespacedKey id;
    private final GlyphConfig config;

    public LevitateEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "levitate");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int amplify = context.getAmplifyLevel(); // 負の値(Dampen)も許容
        int level = Math.max(0, amplify);        // ポーションレベル（0-indexed）
        int baseDurationTicks = (int) config.getParam("levitate", "base-duration-ticks", DEFAULT_BASE_DURATION_TICKS);
        int durationBonusTicks = (int) config.getParam("levitate", "duration-bonus-ticks", DEFAULT_DURATION_BONUS_TICKS);
        int durationTicks = Math.max(1,
            baseDurationTicks + context.getDurationLevel() * durationBonusTicks);

        target.addPotionEffect(
            new PotionEffect(PotionEffectType.LEVITATION, durationTicks, level, false, true, true));

        spawnLevitateFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnLevitateFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 15, 0.3, 0.5, 0.3, 0.05);
        effectLoc.getWorld().spawnParticle(Particle.CLOUD, effectLoc, 8, 0.3, 0.2, 0.3, 0.02);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_SHULKER_SHOOT,
            SoundCategory.PLAYERS, 0.6f, 1.5f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "浮遊"; }

    @Override
    public String getDescription() { return "対象を浮遊させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("levitate"); }

    @Override
    public int getTier() { return config.getTier("levitate"); }
}
