package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 直後のEffectの持続時間を延長するAugment。
 * 1スタックにつき+200tick (10秒)。
 */
public class ExtendTimeAugment implements SpellAugment {

    private static final int TICKS_PER_STACK = 200; // 10秒
    private final NamespacedKey id;

    public ExtendTimeAugment(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "extend_time");
    }

    @Override
    public void modify(SpellContext context) {
        context.setDurationTicks(context.getDurationTicks() + TICKS_PER_STACK);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Extend Time"; }

    @Override
    public int getManaCost() { return 8; }

    @Override
    public int getTier() { return 2; }
}
