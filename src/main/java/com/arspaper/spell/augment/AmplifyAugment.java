package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 直前のEffect/Formの威力を増加させるAugment。
 */
public class AmplifyAugment implements SpellAugment {

    private final NamespacedKey id;

    public AmplifyAugment(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "amplify");
    }

    @Override
    public void modify(SpellContext context) {
        context.setAmplifyLevel(context.getAmplifyLevel() + 1);
        context.setDamageMultiplier(context.getDamageMultiplier() + 0.5);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Amplify"; }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }
}
