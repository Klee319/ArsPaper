package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象に実際の雷を落とすEffect。Ars Nouveau Tier 3, mana 100相当。
 * - 基本ダメージ: 5.0 + Amplifyごとに 3.0（最大2スタック）
 * - strikeLightning()で火災・帯電クリーパー効果を伴う本物の雷を落とす
 * - 被弾後にShocked状態（Slowness I）を付与: base 100 ticks + durationLevel * 60 ticks
 * - 水中または雨中のエンティティは +2.0 ボーナスダメージ
 */
public class LightningEffect implements SpellEffect {

    private static final double BASE_DAMAGE = 5.0;
    private static final double AMPLIFY_BONUS = 3.0;
    private static final int MAX_AMPLIFY = 2;
    private static final double WET_BONUS_DAMAGE = 2.0;
    private static final int SHOCKED_BASE_TICKS = 100;    // 5秒
    private static final int SHOCKED_PER_LEVEL = 60;      // 3秒/stack
    private final NamespacedKey id;
    private final GlyphConfig config;

    public LightningEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "lightning");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // ダメージ計算（Amplifyは最大2スタック、Dampenで減少・0以下で演出雷）
        double baseDamage = config.getParam("lightning", "base-damage", BASE_DAMAGE);
        int maxAmplify = (int) config.getParam("lightning", "max-amplify", (double) MAX_AMPLIFY);
        int amp = Math.min(context.getAmplifyLevel(), maxAmplify);
        double amplifyBonus = config.getParam("lightning", "amplify-bonus", AMPLIFY_BONUS);
        double damage = baseDamage + amp * amplifyBonus;

        if (damage <= 0) {
            // 演出雷: ���メージ・火災なし（strikeLightningEffect）
            target.getWorld().strikeLightningEffect(target.getLocation());
        } else {
            // 本物の雷（火災・帯電クリーパー変化等の副作用込み）
            target.getWorld().strikeLightning(target.getLocation());

            // 濡れ判定（水中 or 雨中）
            double wetBonus = config.getParam("lightning", "wet-bonus-damage", WET_BONUS_DAMAGE);
            if (isWet(target)) {
                damage += wetBonus;
            }

            damage = context.calculateSpellDamage(damage, target);
            target.damage(damage, context.getCaster());

            // Shockedステータス: Slowness I を付与
            int shockedBase = (int) config.getParam("lightning", "shocked-base-ticks", (double) SHOCKED_BASE_TICKS);
            int shockedPerLevel = (int) config.getParam("lightning", "shocked-per-level", (double) SHOCKED_PER_LEVEL);
            int shockedDuration = shockedBase + context.getDurationLevel() * shockedPerLevel;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, shockedDuration, 0));
        }
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        double baseDamage = config.getParam("lightning", "base-damage", BASE_DAMAGE);
        int maxAmplify = (int) config.getParam("lightning", "max-amplify", (double) MAX_AMPLIFY);
        int amp = Math.min(context.getAmplifyLevel(), maxAmplify);
        double amplifyBonus = config.getParam("lightning", "amplify-bonus", AMPLIFY_BONUS);
        double damage = baseDamage + amp * amplifyBonus;

        if (damage <= 0) {
            blockLocation.getWorld().strikeLightningEffect(blockLocation);
        } else {
            blockLocation.getWorld().strikeLightning(blockLocation);
        }
    }

    /** エンティティが水中または雨に濡れているか判定する */
    private boolean isWet(LivingEntity entity) {
        Location loc = entity.getLocation();
        // 水・泡の中にいる場合
        Material blockType = loc.getBlock().getType();
        if (blockType == Material.WATER || blockType == Material.BUBBLE_COLUMN) {
            return true;
        }
        // 降雨中でかつ空が見えている（屋外）場合
        if (entity.getWorld().hasStorm() && loc.getBlock().getLightFromSky() == 15) {
            return true;
        }
        return false;
    }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "雷撃"; }

    @Override
    public String getDescription() { return "対象に雷を落とし感電させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("lightning"); }

    @Override
    public int getTier() { return config.getTier("lightning"); }
}
