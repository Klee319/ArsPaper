package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 旅路の魔法 — 対象に体力増強を付与するEffect。
 * Tier 3, 高コスト高効果。
 * 増幅: 体力増強レベル上昇（+4HP/段）
 * 延長/短縮: 効果時間
 */
public class JourneyEffect implements SpellEffect {

    private static final int BASE_DURATION = 6000;            // 5分
    private static final int DURATION_PER_LEVEL = 2400;       // +2分/段

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public JourneyEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "journey");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("journey", "base-duration", BASE_DURATION);
        int durationPerLevel = (int) config.getParam("journey", "duration-per-level", DURATION_PER_LEVEL);
        int baseAmplifyLevel = (int) config.getParam("journey", "base-amplify-level", 0.0);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;

        int amplifyLevel = baseAmplifyLevel + context.getAmplifyLevel();

        if (amplifyLevel >= 0) {
            // 体力増強（最大HP増加）
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.HEALTH_BOOST, duration, amplifyLevel, false, true, true));

            spawnJourneyFx(target.getLocation());

            // 1tick後にHP回復（ポーション適用後の属性更新を待つ）
            final int amp = amplifyLevel;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (target.isDead() || !target.isValid()) return;
                    AttributeInstance maxHealthAttr = target.getAttribute(Attribute.MAX_HEALTH);
                    if (maxHealthAttr == null) return;
                    double maxHealth = maxHealthAttr.getValue();
                    target.setHealth(Math.min(maxHealth, target.getHealth() + (amp + 1) * 4.0));
                }
            }.runTaskLater(plugin, 1L);
        } else {
            // 減衰: 最大体力減少（Attribute Modifierで一時的に最大HPを下げる）
            int reductionLevel = Math.abs(amplifyLevel);
            // HEALTH_BOOST amplifier -1 は存在しないため、代替としてダメージ吸収の逆を使う
            // 最大HP減少量 = reductionLevel × 4HP
            org.bukkit.attribute.AttributeInstance maxHealthAttr = target.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                org.bukkit.NamespacedKey modKey = new org.bukkit.NamespacedKey(plugin, "journey_debuff");
                // 既存のmodifierがあれば除去
                maxHealthAttr.getModifiers().stream()
                    .filter(m -> m.getKey().equals(modKey))
                    .forEach(maxHealthAttr::removeModifier);
                // 最大HP減少
                double reduction = reductionLevel * 4.0;
                maxHealthAttr.addModifier(new org.bukkit.attribute.AttributeModifier(
                    modKey, -reduction, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
                // HP調整（最大HPを超えないように）
                double newMax = maxHealthAttr.getValue();
                if (target.getHealth() > newMax) {
                    target.setHealth(Math.max(1, newMax));
                }
                // 持続時間後にModifier除去
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (target.isDead() || !target.isValid()) return;
                        org.bukkit.attribute.AttributeInstance attr = target.getAttribute(Attribute.MAX_HEALTH);
                        if (attr != null) {
                            attr.getModifiers().stream()
                                .filter(m -> m.getKey().equals(modKey))
                                .forEach(attr::removeModifier);
                        }
                    }
                }.runTaskLater(plugin, duration);
            }
            spawnDebuffFx(target.getLocation());
        }
    }

    private void spawnDebuffFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc.clone().add(0, 1.5, 0),
            8, 0.3, 0.3, 0.3, 0.02);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_HURT,
            SoundCategory.PLAYERS, 0.4f, 1.5f);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    private void spawnJourneyFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 2, 0),
            10, 0.5, 0.3, 0.5, 0);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0),
            30, 0.5, 0.8, 0.5, 0.3);
        loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "旅路の魔法"; }
    @Override public String getDescription() { return "対象に体力増強を付与する"; }
    @Override public int getManaCost() { return config.getManaCost("journey"); }
    @Override public int getTier() { return config.getTier("journey"); }
}
