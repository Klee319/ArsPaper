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
 * 対象にダメージ耐性を付与するEffect。Ars Nouveau Tier 3, mana 400相当。
 * Resistance（最大80%軽減、amplifier上限3）とAbsorptionを同時付与して
 * マナ消費シールドに近い保護感を再現する。
 */
public class ShieldEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION = 200;          // 10秒
    private static final int DEFAULT_DURATION_PER_LEVEL = 160;     // ExtendTimeごと +8秒
    private static final int DEFAULT_MAX_AMPLIFIER = 3;            // Resistance IV相当 = 80%軽減
    private final NamespacedKey id;
    private final GlyphConfig config;

    public ShieldEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "shield");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("shield", "base-duration", DEFAULT_BASE_DURATION);
        int durationPerLevel = (int) config.getParam("shield", "duration-per-level", DEFAULT_DURATION_PER_LEVEL);
        int maxAmplifier = (int) config.getParam("shield", "max-amplifier", DEFAULT_MAX_AMPLIFIER);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;
        int amplifier = Math.min(Math.max(0, context.getAmplifyLevel()), maxAmplifier);

        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier));
        // Absorptionで追加HP層を付与（amplifierに応じてHP量が増加）
        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier));

        SpellFxUtil.spawnShieldFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "盾"; }

    @Override
    public String getDescription() { return "ダメージ耐性と吸収HPを付与する"; }

    @Override
    public int getManaCost() { return config.getManaCost("shield"); }

    @Override
    public int getTier() { return config.getTier("shield"); }
}
