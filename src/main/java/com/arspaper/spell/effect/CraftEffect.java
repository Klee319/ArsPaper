package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 発動者にクラフトテーブルGUIを開くEffect。
 * Ars Nouveau Tier 2準拠:
 *   - 発動者自身にのみ作用（SelfForm/TouchFormと組み合わせて使う）
 *   - Paper API: caster.openWorkbench(null, true) を使用
 */
public class CraftEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public CraftEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "craft");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        if (caster == null) return;

        // クラフトテーブルGUIを開く（targetが発動者自身でも他エンティティでも発動者が開く）
        caster.openWorkbench(null, true);

        spawnCraftFx(caster.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象: 発動者に対してクラフトGUIを開く
        Player caster = context.getCaster();
        if (caster == null) return;

        caster.openWorkbench(null, true);

        spawnCraftFx(blockLocation);
    }

    private void spawnCraftFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 15, 0.4, 0.4, 0.4, 0.5);
        effectLoc.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS, 0.7f, 1.0f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "製作"; }

    @Override
    public String getDescription() { return "クラフトテーブルを開く"; }

    @Override
    public int getManaCost() { return config.getManaCost("craft"); }

    @Override
    public int getTier() { return config.getTier("craft"); }
}
