package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;

/**
 * 発動者のインベントリにあるポーションを対象エンティティに適用するEffect。
 * Ars Nouveau Tier 2準拠:
 *   - 発動者インベントリから最初のPOTION/SPLASH_POTIONを探し対象に効果を適用
 *   - ポーションアイテムを消費する
 *   - AOEあり (aoeLevel > 0): 周囲のエンティティにもスプラッシュ効果を付与
 */
public class InfuseEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public InfuseEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "infuse");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        if (caster == null) return;

        // オフハンド → ホットバー右端(8)から左端(0)の優先順でポーションを探索
        int potionSlot = findPotionSlot(caster);
        if (potionSlot < 0) return;

        org.bukkit.inventory.PlayerInventory inv = caster.getInventory();
        ItemStack potionItem = potionSlot == 40 ? inv.getItemInOffHand() : inv.getItem(potionSlot);
        if (potionItem == null) return;

        Collection<PotionEffect> effects = getPotionEffects(potionItem);
        if (effects.isEmpty()) return;

        // 対象に効果を適用（AOEはSpellContext.resolveOnEntity()が処理するため内部では不要）
        applyEffectsToEntity(target, effects);

        // ポーションを消費（スロット追跡型）
        consumePotionAtSlot(caster, potionSlot);

        // エフェクト
        spawnInfuseFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    /**
     * アイテムからポーション効果のコレクションを取得する。
     */
    private Collection<PotionEffect> getPotionEffects(ItemStack item) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return java.util.Collections.emptyList();
        }
        return meta.getCustomEffects().isEmpty()
            ? meta.getBasePotionType() != null
                ? meta.getBasePotionType().getPotionEffects()
                : java.util.Collections.emptyList()
            : meta.getCustomEffects();
    }

    /**
     * エンティティにポーション効果リストを付与する。
     */
    private void applyEffectsToEntity(LivingEntity entity, Collection<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            entity.addPotionEffect(effect);
        }
    }

    /**
     * オフハンド → ホットバー右端(8)から左端(0)の優先順でポーションスロットを探索する。
     * @return スロット番号（40=オフハンド, 0-8=ホットバー, -1=未検出）
     */
    private int findPotionSlot(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();

        ItemStack offhand = inv.getItemInOffHand();
        if (!offhand.getType().isAir() && isPotion(offhand)) return 40;

        for (int slot = 8; slot >= 0; slot--) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir() && isPotion(item)) return slot;
        }
        return -1;
    }

    private boolean isPotion(ItemStack item) {
        return item.getType() == Material.POTION
            || item.getType() == Material.SPLASH_POTION
            || item.getType() == Material.LINGERING_POTION;
    }

    /**
     * 指定スロットのポーションを1個消費する。
     */
    private void consumePotionAtSlot(Player player, int slot) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack item = slot == 40 ? inv.getItemInOffHand() : inv.getItem(slot);
        if (item == null) return;

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            inv.setItem(slot, null);
        }
    }

    private void spawnInfuseFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.WITCH, effectLoc, 20, 0.3, 0.5, 0.3, 0.1);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_SPLASH_POTION_BREAK,
            SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "注入"; }

    @Override
    public String getDescription() { return "ポーションを対象に適用する"; }

    @Override
    public int getManaCost() { return config.getManaCost("infuse"); }

    @Override
    public int getTier() { return config.getTier("infuse"); }
}
