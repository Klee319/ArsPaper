package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 直後のEffectの貫通回数を増加させるAugment。
 * Projectile Formと連携し、ヒット後もエンティティを貫通して次の対象にも効果を適用。
 * 1スタックにつき+1貫通。
 */
public class PierceAugment implements SpellAugment {

    private final NamespacedKey id;

    public PierceAugment(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "pierce");
    }

    @Override
    public void modify(SpellContext context) {
        context.setPierceCount(context.getPierceCount() + 1);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Pierce"; }

    @Override
    public int getManaCost() { return 12; }

    @Override
    public int getTier() { return 2; }
}
