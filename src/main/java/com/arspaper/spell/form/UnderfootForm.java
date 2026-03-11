package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 術者の足元のブロックに対して効果を適用するForm。
 */
public class UnderfootForm implements SpellForm {

    private final NamespacedKey id;

    public UnderfootForm(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "underfoot");
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);
        Location underfoot = caster.getLocation().subtract(0, 1, 0);
        context.resolveOnBlock(underfoot);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Underfoot"; }

    @Override
    public int getManaCost() { return 2; }

    @Override
    public int getTier() { return 1; }
}
