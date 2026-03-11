package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 術者自身に効果を適用するForm。
 */
public class SelfForm implements SpellForm {

    private final NamespacedKey id;

    public SelfForm(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "self");
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
    public String getDisplayName() { return "Self"; }

    @Override
    public int getManaCost() { return 2; }

    @Override
    public int getTier() { return 1; }
}
