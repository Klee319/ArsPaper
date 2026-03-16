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
 * 対象エンティティにアルカナム名を付与するEffect。
 * Ars Nouveau Tier 2準拠:
 *   - 対象のカスタム名を "§5Arcane " + エンティティ種別名に設定
 *   - カスタム名を常時表示する
 *   - ブロック対象はNoOp
 */
public class NameEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public NameEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "name");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        String displayName = (caster != null) ? caster.getName() : "Unknown";

        target.setCustomName("§f" + displayName);
        target.setCustomNameVisible(true);

        spawnNameFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnNameFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 2, 0);
        effectLoc.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 10, 0.3, 0.3, 0.3, 0.5);
        effectLoc.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "命名"; }

    @Override
    public String getDescription() { return "対象に名前を付ける"; }

    @Override
    public int getManaCost() { return config.getManaCost("name"); }

    @Override
    public int getTier() { return config.getTier("name"); }
}
