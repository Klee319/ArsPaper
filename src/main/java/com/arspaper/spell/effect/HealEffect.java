package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象を回復するEffect。
 * Amplifyで回復量が増加。
 */
public class HealEffect implements SpellEffect {

    private static final double BASE_HEAL = 4.0;
    private static final double AMPLIFY_BONUS = 2.0;
    private final NamespacedKey id;

    public HealEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "heal");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        AttributeInstance maxHealthAttr = target.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;

        double heal = BASE_HEAL + context.getAmplifyLevel() * AMPLIFY_BONUS;
        double newHealth = Math.min(target.getHealth() + heal, maxHealthAttr.getValue());
        target.setHealth(newHealth);
        SpellFxUtil.spawnHealFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // HealはブロックにはNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Heal"; }

    @Override
    public int getManaCost() { return 15; }

    @Override
    public int getTier() { return 1; }
}
