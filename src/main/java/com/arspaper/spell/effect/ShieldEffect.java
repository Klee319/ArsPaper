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
 * 対象にダメージ耐性を付与するEffect。Ars Nouveau Tier 3, mana 150相当。
 * Resistance II（固定40%軽減）とAbsorption（増幅でレベル上昇、+4HP/段）を同時付与して
 * マナ消費シールドに近い保護感を再現する。
 */
public class ShieldEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION = 100;          // 5秒
    private static final int DEFAULT_DURATION_PER_LEVEL = 160;     // ExtendTimeごと +8秒
    private static final int RESISTANCE_AMPLIFIER = 1;             // Resistance II = 40%軽減（固定）
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
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;

        // Resistance II（固定、amplifyの影響を受けない）
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, RESISTANCE_AMPLIFIER));
        // Absorptionは増幅でレベル上昇（+4HP/段）
        int absorptionAmplifier = Math.max(0, context.getAmplifyLevel());
        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, absorptionAmplifier));

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
