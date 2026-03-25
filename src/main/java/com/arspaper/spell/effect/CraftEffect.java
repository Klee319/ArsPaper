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
 * 対象プレイヤーにクラフトテーブルGUIを強制的に開くEffect。
 * 対象がプレイヤーの場合、そのプレイヤーにGUIを開かせる。
 * 対象がプレイヤー以外の場合、発動者にGUIを開く。
 * 既にGUIを開いている場合は不発（処理干渉防止）。
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
        // プレイヤー対象のみ: 対象プレイヤーにGUIを開く
        if (!(target instanceof Player opener)) return;

        if (opener.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) return;

        opener.openWorkbench(null, true);
        spawnCraftFx(opener.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック着弾時はNoOp（プレイヤー対象のみ動作）
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
