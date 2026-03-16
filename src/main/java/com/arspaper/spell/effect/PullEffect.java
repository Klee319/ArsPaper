package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 対象を術者の方へ引き寄せるEffect。
 * params: base-force(0.8), amplify-bonus(0.5), max-force(3.0), min-y(0.15)
 */
public class PullEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public PullEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "pull");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        if (caster == null) return;

        double baseForce = config.getParam("pull", "base-force", 0.8);
        double amplifyBonus = config.getParam("pull", "amplify-bonus", 0.5);
        double maxForce = config.getParam("pull", "max-force", 3.0);
        double minY = config.getParam("pull", "min-y", 0.15);

        double force = Math.min(baseForce + context.getAmplifyLevel() * amplifyBonus, maxForce);

        Vector direction = caster.getLocation().toVector()
            .subtract(target.getLocation().toVector());

        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 1, 0);
        } else {
            direction.normalize();
        }
        direction.multiply(force).setY(Math.max(direction.getY(), minY));
        target.setVelocity(direction);
        SpellFxUtil.spawnPullFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "引寄"; }
    @Override public String getDescription() { return "対象を術者の方へ引き寄せる"; }
    @Override public int getManaCost() { return config.getManaCost("pull"); }
    @Override public int getTier() { return config.getTier("pull"); }
}
