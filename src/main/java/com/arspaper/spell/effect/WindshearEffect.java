package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 空中にいるエンティティに地面からの高度に応じたダメージを与えるEffect。
 * Ars Nouveau Tier 2, mana 50相当。
 *
 * ダメージ計算:
 *   damage = 5.0 + 2.5 * amplifyLevel + min(heightAboveGround, 10.0)
 * エンティティが地面上にいる場合（heightAboveGround == 0）はダメージなし。
 */
public class WindshearEffect implements SpellEffect {

    private static final double BASE_DAMAGE = 5.0;
    private static final double AMPLIFY_BONUS = 2.5;
    private static final double MAX_HEIGHT_BONUS = 10.0;
    private static final int MAX_SCAN_DEPTH = 64; // 地面スキャンの最大深度
    private final NamespacedKey id;
    private final GlyphConfig config;

    public WindshearEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "windshear");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double height = getHeightAboveGround(target);

        // 地面にいる場合はダメージなし
        if (height <= 0.0) return;

        double heightBonus = Math.min(height, MAX_HEIGHT_BONUS);
        double damage = BASE_DAMAGE + AMPLIFY_BONUS * context.getAmplifyLevel() + heightBonus;

        target.damage(damage, context.getCaster());

        Location loc = target.getLocation().add(0, 1, 0);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 20, 0.4, 0.4, 0.4, 0.15);
        loc.getWorld().playSound(loc, Sound.ENTITY_WIND_CHARGE_WIND_BURST,
            SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    /**
     * エンティティの足元から下方向に走査し、地面までの距離（ブロック数）を返す。
     * 地面上にいる場合（足元ブロックが固体）は 0.0 を返す。
     */
    private double getHeightAboveGround(LivingEntity entity) {
        Location loc = entity.getLocation();
        double entityY = loc.getY();

        // 足元の実数Y座標から下を走査
        for (int i = 1; i <= MAX_SCAN_DEPTH; i++) {
            Location check = loc.clone().subtract(0, i, 0);
            if (check.getBlock().getType().isSolid()) {
                // iブロック下に地面が見つかった → 高度は i-1
                // (i=1 なら足元すぐ下が地面 = 実質地上)
                return (double) (i - 1);
            }
        }

        // スキャン範囲内に地面が見つからない場合は最大高度として扱う
        return MAX_SCAN_DEPTH;
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "烈風"; }

    @Override
    public String getDescription() { return "空中にいるほど強力なダメージを与える"; }

    @Override
    public int getManaCost() { return config.getManaCost("windshear"); }

    @Override
    public int getTier() { return config.getTier("windshear"); }
}
