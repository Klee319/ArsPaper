package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象に物理ダメージを与えるEffect。
 * Ars Nouveau準拠: damage = 5.0 + 2.0 * amplifyLevel
 * ExtendTimeがある場合: 直接ダメージの代わりにPoisonを付与
 *   Poison持続 = 5秒 + 3秒 × durationLevel、レベル = amplifyLevel
 */
public class HarmEffect implements SpellEffect {

    private static final double DEFAULT_BASE_DAMAGE = 5.0;
    private static final double DEFAULT_AMPLIFY_BONUS = 2.0;
    private static final int DEFAULT_BASE_POISON_SECONDS = 5;
    private static final int DEFAULT_POISON_SECONDS_PER_DURATION = 3;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public HarmEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "harm");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int durationLevel = context.getDurationLevel();
        if (durationLevel > 0) {
            // ExtendTime付き: 継続ダメージ（DoT）付与
            int basePoisonSeconds = (int) config.getParam("harm", "base-poison-seconds", DEFAULT_BASE_POISON_SECONDS);
            int poisonSecondsPerDuration = (int) config.getParam("harm", "poison-seconds-per-duration", DEFAULT_POISON_SECONDS_PER_DURATION);
            int seconds = basePoisonSeconds + poisonSecondsPerDuration * durationLevel;
            int durationTicks = seconds * 20;
            int amplifier = Math.max(0, context.getAmplifyLevel());
            // アンデッドMobはPoisonが無効 → WITHERで代替
            PotionEffectType dotType = isUndead(target)
                ? PotionEffectType.WITHER
                : PotionEffectType.POISON;
            target.addPotionEffect(new PotionEffect(
                dotType, durationTicks, amplifier, false, true, true));
        } else {
            // 通常: 直接ダメージ
            double baseDamage = config.getParam("harm", "base-damage", DEFAULT_BASE_DAMAGE);
            double amplifyBonus = config.getParam("harm", "amplify-bonus", DEFAULT_AMPLIFY_BONUS);
            double damage = Math.max(0, baseDamage + context.getAmplifyLevel() * amplifyBonus);
            damage = context.calculateSpellDamage(damage, target);
            target.damage(damage, context.getCaster());
        }
        SpellFxUtil.spawnHarmFx(target.getLocation());
    }

    /**
     * アンデッドMobかどうかを判定する。
     * Paper 1.21+ではEntityCategory廃止のため、EntityTypeで直接判定。
     */
    private boolean isUndead(LivingEntity entity) {
        return entity.getType().getKey().getKey().matches(
            "zombie|skeleton|wither_skeleton|stray|husk|drowned|phantom|"
            + "zombified_piglin|zoglin|wither|skeleton_horse|zombie_horse|"
            + "zombie_villager|bogged"
        );
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // HarmはブロックにはNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "害悪"; }

    @Override
    public String getDescription() { return "対象にダメージを与える"; }

    @Override
    public int getManaCost() { return config.getManaCost("harm"); }

    @Override
    public int getTier() { return config.getTier("harm"); }
}
