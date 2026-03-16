package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 術者自身に効果を適用するForm。
 */
public class SelfForm implements SpellForm {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SelfForm(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "self");
        this.config = config;
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);
        SpellFxUtil.spawnSelfCastParticles(caster);
        context.resolveOnEntity(caster);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "自己"; }

    @Override
    public String getDescription() { return "自分自身に効果を適用する"; }

    @Override
    public int getManaCost() { return config.getManaCost("self"); }

    @Override
    public int getTier() { return config.getTier("self"); }
}
