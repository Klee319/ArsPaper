package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象を呪い、被ダメージを増加させるEffect。Ars NouveauのHexに準拠。
 * エンティティ:
 *   - WEAKNESS（弱体化）を付与
 *   - 有益なポーション効果を全て除去
 *   - 被ダメージ追撃: 受けたダメージの (10 + 5*amplify)% を追加ダメージとして与える
 * ブロック: NoOp
 */
public class HexEffect implements SpellEffect, Listener {

    private static final int BASE_DURATION = 400;          // 20秒
    private static final int DURATION_PER_LEVEL = 160;     // ExtendTimeごと +8秒
    private static final int MAX_AMPLIFIER = 3;
    private static final double BASE_DAMAGE_PERCENT = 0.10; // 基本追撃率10%
    private static final double AMPLIFY_BONUS_PERCENT = 0.05; // 増幅1段あたり+5%
    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    /** 呪詛の追撃倍率をPDCに保存するキー（double値: 0.10 = 10%） */
    private static final NamespacedKey HEX_DAMAGE_KEY = new NamespacedKey("arspaper", "hex_damage_mult");

    public HexEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "hex");
        this.config = config;
        this.plugin = plugin;
        // ダメージ追撃リスナーを登録
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double basePercent = config.getParam("hex", "base-damage-percent", BASE_DAMAGE_PERCENT);
        double ampBonus = config.getParam("hex", "amplify-bonus-percent", AMPLIFY_BONUS_PERCENT);
        int baseDuration = (int) config.getParam("hex", "base-duration", (double) BASE_DURATION);
        int durationPerLevel = (int) config.getParam("hex", "duration-per-level", (double) DURATION_PER_LEVEL);
        int duration = Math.max(1, baseDuration + context.getDurationLevel() * durationPerLevel);
        int maxAmp = (int) config.getParam("hex", "max-amplifier", (double) MAX_AMPLIFIER);
        int amplifier = Math.min(Math.max(0, context.getAmplifyLevel()), maxAmp);

        // 追撃倍率を計算してPDCに保存
        double damageMult = basePercent + amplifier * ampBonus;
        target.getPersistentDataContainer().set(HEX_DAMAGE_KEY, PersistentDataType.DOUBLE, damageMult);

        // WEAKNESS: 攻撃力低下
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.WEAKNESS, duration, amplifier, false, true, true));

        // 有益な効果を全て除去
        target.getActivePotionEffects().stream()
            .map(PotionEffect::getType)
            .filter(this::isBeneficial)
            .forEach(target::removePotionEffect);

        // 持続時間後にPDCマーカーを除去
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid() && !target.isDead()) {
                target.getPersistentDataContainer().remove(HEX_DAMAGE_KEY);
            }
        }, duration);

        spawnHexFx(target.getLocation());
    }

    /**
     * 呪詛マーカー付きのエンティティがダメージを受けた時、追撃ダメージを適用する。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        Double mult = target.getPersistentDataContainer()
            .get(HEX_DAMAGE_KEY, PersistentDataType.DOUBLE);
        if (mult == null || mult <= 0) return;

        double bonusDamage = event.getFinalDamage() * mult;
        if (bonusDamage < 0.5) return;

        // 次tickで追撃（再帰防止: 同tickだとこのリスナーが再度発火する）
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isValid() && !target.isDead()) {
                target.damage(bonusDamage);
                target.getWorld().spawnParticle(Particle.WITCH,
                    target.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.05);
            }
        });
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
