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
 * 対象にBounce（着地時跳躍）効果を付与するEffect。
 * Ars Nouveau準拠: JUMP_BOOST付与で着地時の反発を模倣。
 * 基本持続: 30秒(600tick) + durationLevel毎に8秒(160tick)
 * Amplify付き: SPEEDも付与して前方運動量を保持する
 */
public class BounceEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public BounceEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "bounce");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("bounce", "base-duration", 600);
        int durationPerLevel = (int) config.getParam("bounce", "duration-per-level", 160);
        int durationTicks = baseDuration + context.getDurationLevel() * durationPerLevel;
        int amplifyLevel = context.getAmplifyLevel();

        // JUMP_BOOSTでBounce効果を再現（着地後も繰り返し跳ねる）
        int jumpLevel = Math.max(0, amplifyLevel);
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.JUMP_BOOST, durationTicks, jumpLevel, false, true, true));

        // Amplifyがある場合: SPEEDを付与して前方運動量を保持
        if (amplifyLevel > 0) {
            int speedLevel = Math.min(amplifyLevel - 1, 4);
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, durationTicks, speedLevel, false, true, true));
        }

        SpellFxUtil.spawnBounceFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "弾跳"; }

    @Override
    public String getDescription() { return "対象にBounce効果を付与し、着地時に跳躍させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("bounce"); }

    @Override
    public int getTier() { return config.getTier("bounce"); }
}
