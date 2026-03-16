package com.arspaper.spell.augment;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 残留増強: 効果を持続領域として展開する。
 * 後続のEffectチェーンを着弾地点で定期的に再適用するゾーンを生成する。
 */
public class LingerAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public LingerAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "linger");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        context.setLingerPattern(true);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "残留"; }

    @Override
    public String getDescription() { return "効果を持続領域として展開する"; }

    @Override
    public int getManaCost() { return config.getManaCost("linger"); }

    @Override
    public int getTier() { return config.getTier("linger"); }
}
