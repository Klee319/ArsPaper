package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 術者の頭上のブロックに対して効果を適用するForm。
 * UnderfootFormの上方向版。
 */
public class OverheadForm implements SpellForm {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public OverheadForm(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "overhead");
        this.config = config;
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);
        // 目線位置の1ブロック上 = 確実に頭上のブロック
        Location overhead = caster.getEyeLocation().add(0, 1, 0);
        context.resolveOnBlock(overhead);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "頭上"; }

    @Override
    public String getDescription() { return "頭上のブロックに効果を適用する"; }

    @Override
    public int getManaCost() { return config.getManaCost("overhead"); }

    @Override
    public int getTier() { return config.getTier("overhead"); }
}
