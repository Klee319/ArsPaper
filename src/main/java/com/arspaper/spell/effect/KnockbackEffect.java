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
 * 対象を吹き飛ばすEffect。Ars NouveauのEffectKnockbackに準拠。
 * 術者から対象への方向に力を加える。Amplifyで威力増加。
 */
public class KnockbackEffect implements SpellEffect {

    private static final double BASE_FORCE = 1.5;
    private static final double AMPLIFY_BONUS = 0.5;
    private static final double MAX_FORCE = 5.0;
    private final NamespacedKey id;

    public KnockbackEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "knockback");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double force = Math.min(BASE_FORCE + context.getAmplifyLevel() * AMPLIFY_BONUS, MAX_FORCE);
        Vector direction = target.getLocation().toVector()
            .subtract(context.getCaster().getLocation().toVector())
            .normalize()
            .multiply(force)
            .setY(0.4);
        target.setVelocity(direction);
        SpellFxUtil.spawnKnockbackFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Knockback"; }

    @Override
    public int getManaCost() { return 8; }

    @Override
    public int getTier() { return 1; }
}
