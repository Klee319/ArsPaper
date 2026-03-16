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
 * 対象を呪い、被ダメージを増加させるEffect。Ars NouveauのHexに準拠。
 * エンティティ: UNLUCK（不運）+ WEAKNESS（弱体化）を付与。
 *   Sensitive付き: 対象の有益なポーション効果を全て除去する。
 * ブロック: NoOp
 */
public class HexEffect implements SpellEffect {

    private static final int BASE_DURATION = 400;          // 20秒
    private static final int DURATION_PER_LEVEL = 160;     // ExtendTimeごと +8秒
    private static final int MAX_AMPLIFIER = 3;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public HexEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "hex");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int duration = Math.max(1, BASE_DURATION + context.getDurationLevel() * DURATION_PER_LEVEL);
        int amplifier = Math.min(Math.max(0, context.getAmplifyLevel()), MAX_AMPLIFIER);

        // UNLUCK: 防御力低下を表現（Bad Luck）
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.UNLUCK, duration, amplifier, false, true, true));

        // WEAKNESS: 脆弱性を表現（攻撃力低下 + 被ダメージ増加感）
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.WEAKNESS, duration, amplifier, false, true, true));

        // 有益な効果を全て除去（Dispelの害悪版）
        target.getActivePotionEffects().stream()
            .map(PotionEffect::getType)
            .filter(this::isBeneficial)
            .forEach(target::removePotionEffect);

        spawnHexFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    /**
     * 有益なポーション効果かどうかを判定する。
     */
    private boolean isBeneficial(PotionEffectType type) {
        return type.equals(PotionEffectType.SPEED)
            || type.equals(PotionEffectType.HASTE)
            || type.equals(PotionEffectType.STRENGTH)
            || type.equals(PotionEffectType.INSTANT_HEALTH)
            || type.equals(PotionEffectType.JUMP_BOOST)
            || type.equals(PotionEffectType.REGENERATION)
            || type.equals(PotionEffectType.RESISTANCE)
            || type.equals(PotionEffectType.FIRE_RESISTANCE)
            || type.equals(PotionEffectType.WATER_BREATHING)
            || type.equals(PotionEffectType.INVISIBILITY)
            || type.equals(PotionEffectType.NIGHT_VISION)
            || type.equals(PotionEffectType.ABSORPTION)
            || type.equals(PotionEffectType.SATURATION)
            || type.equals(PotionEffectType.SLOW_FALLING)
            || type.equals(PotionEffectType.CONDUIT_POWER)
            || type.equals(PotionEffectType.HERO_OF_THE_VILLAGE)
            || type.equals(PotionEffectType.LUCK);
    }

    private void spawnHexFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0),
            20, 0.4, 0.5, 0.4, 0.1);
        loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1, 0),
            10, 0.3, 0.4, 0.3, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_PREPARE_WOLOLO,
            SoundCategory.PLAYERS, 0.8f, 0.8f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "呪詛"; }

    @Override
    public String getDescription() { return "対象を呪い、被ダメージを増加させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("hex"); }

    @Override
    public int getTier() { return config.getTier("hex"); }
}
