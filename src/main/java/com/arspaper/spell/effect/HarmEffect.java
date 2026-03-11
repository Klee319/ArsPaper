package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象に物理ダメージを与えるEffect。
 * Amplifyで威力が増加。
 */
public class HarmEffect implements SpellEffect {

    private static final double BASE_DAMAGE = 5.0;
    private static final double AMPLIFY_BONUS = 2.5;
    private final NamespacedKey id;

    public HarmEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "harm");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double damage = (BASE_DAMAGE + context.getAmplifyLevel() * AMPLIFY_BONUS)
            * context.getDamageMultiplier();
        target.damage(damage, context.getCaster());
        SpellFxUtil.spawnHarmFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // HarmはブロックにはNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Harm"; }

    @Override
    public int getManaCost() { return 15; }

    @Override
    public int getTier() { return 1; }
}
