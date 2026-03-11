package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 効果範囲を拡大するAugment。
 * スタック可能（AOE + AOE = より広い範囲）。
 */
public class AoeAugment implements SpellAugment {

    private static final double RADIUS_PER_STACK = 3.0;
    private final NamespacedKey id;

    public AoeAugment(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "aoe");
    }

    @Override
    public void modify(SpellContext context) {
        context.setAoeRadius(context.getAoeRadius() + RADIUS_PER_STACK);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "AOE"; }

    @Override
    public int getManaCost() { return 15; }

    @Override
    public int getTier() { return 1; }
}
