package com.arspaper.spell.augment;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 前のAugment効果をリセットする増強。
 * amplifyLevel, aoeLevel, durationLevel等を0に戻す。
 * 例: [Harm] [Amplify] [Amplify] [Reset] → Harmにamplify=0が適用される
 */
public class ResetAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public ResetAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "reset");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setAmplifyLevel(0);
        context.setAoeLevel(0);
        context.setAoeHeight(0);
        context.setAoeDepth(0);
        context.setAoeRadiusLevel(0);
        context.setDurationLevel(0);
        context.setWallPattern(false);
        context.setLingerPattern(false);
        context.setDelayTicks(0);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "初期化"; }

    @Override
    public String getDescription() { return "増強の効果をリセットする"; }

    @Override
    public int getManaCost() { return config.getManaCost("reset"); }

    @Override
    public int getTier() { return config.getTier("reset"); }
}
