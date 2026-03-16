package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 対象をその視線方向に飛び出させるEffect。
 * params: base-velocity(1.0), amplify-bonus(0.3), max-velocity(3.0)
 */
public class LeapEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public LeapEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "leap");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double baseVelocity = config.getParam("leap", "base-velocity", 1.0);
        double amplifyBonus = config.getParam("leap", "amplify-bonus", 0.3);
        double maxVelocity = config.getParam("leap", "max-velocity", 3.0);

        double velocity = Math.min(baseVelocity + context.getAmplifyLevel() * amplifyBonus, maxVelocity);
        Vector direction = target.getLocation().getDirection().normalize().multiply(velocity);
        if (direction.getY() < 0.2) {
            direction.setY(0.2);
        }
        target.setVelocity(direction);
        SpellFxUtil.spawnLeapFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "跳躍"; } // Leap: 前方跳躍（Bounce:弾跳とは別）
    @Override public String getDescription() { return "視線方向に飛び出す"; }
    @Override public int getManaCost() { return config.getManaCost("leap"); }
    @Override public int getTier() { return config.getTier("leap"); }
}
