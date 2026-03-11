package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 対象を上方に打ち上げるEffect。
 * エンティティ: 上方向に打ち上げ（基礎1.2 + Amplify毎+0.5）。
 * ブロック: NoOp。
 */
public class BounceEffect implements SpellEffect {

    private static final double BASE_VELOCITY = 1.2;
    private static final double AMPLIFY_BONUS = 0.5;
    private static final double MAX_VELOCITY = 4.0;
    private final NamespacedKey id;

    public BounceEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "bounce");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double velocity = Math.min(BASE_VELOCITY + context.getAmplifyLevel() * AMPLIFY_BONUS, MAX_VELOCITY);
        target.setVelocity(new Vector(0, velocity, 0));
        SpellFxUtil.spawnBounceFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Bounce"; }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }
}
