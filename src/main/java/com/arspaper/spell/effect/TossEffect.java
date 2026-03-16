package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

/**
 * インベントリからアイテムを投出するEffect。
 * ブロック対象がコンテナ（チェスト・ホッパー等）: アイテムを挿入する。
 * それ以外: アイテムをその場所にドロップする。
 * Amplify: 投出数量を 1 + amplifyLevel 倍にする（最大スタック上限内）。
 * エンティティ対象: 対象の足元にドロップする。
 */
public class TossEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public TossEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "toss");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティ対象は足元にドロップ
        tossItems(context, target.getLocation(), null);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        // コンテナブロックの場合は挿入を試みる
        if (block.getState() instanceof Container container) {
            tossItems(context, blockLocation, container.getInventory());
        } else {
            tossItems(context, blockLocation, null);
        }
    }

    /**
     * キャスターのインベントリから先頭のアイテムを取り出し、
     * targetInv が null なら blockLocation にドロップ、
     * targetInv が指定されていれば挿入する。
     */
    private void tossItems(SpellContext context, Location dropLocation, Inventory targetInv) {
        Player caster = context.getCaster();
        if (caster == null) return;

        PlayerInventory inv = caster.getInventory();

        // オフハンド → ホットバー右端(8)から左端(0)の優先順で探索
        ItemStack source = null;
        int sourceSlot = -1;

        ItemStack offhand = inv.getItemInOffHand();
        if (!offhand.getType().isAir()) {
            source = offhand.clone();
            sourceSlot = 40;
        }

        if (source == null) {
            for (int slot = 8; slot >= 0; slot--) {
                ItemStack item = inv.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    source = item.clone();
                    sourceSlot = slot;
                    break;
                }
            }
        }

        if (source == null) return;

        // Amplify による数量計算 (amplifyLevel >= 0 なら倍増、< 0 は 1個)
        int amplify = context.getAmplifyLevel();
        int quantity;
        if (amplify <= 0) {
            quantity = 1;
        } else {
            quantity = Math.min(source.getAmount(), 1 + amplify);
        }

        // 投出アイテムスタックを作成
        ItemStack tossed = source.clone();
        tossed.setAmount(quantity);

        if (targetInv != null) {
            // コンテナに挿入
            HashMap<Integer, ItemStack> leftover = targetInv.addItem(tossed);
            for (ItemStack remaining : leftover.values()) {
                launchItem(caster, dropLocation, remaining);
            }
        } else {
            // ディスペンサー挙動: キャスターの向いている方向に射出
            launchItem(caster, dropLocation, tossed);
        }

        // インベントリから消費（スナップショット時点のアイテムを基に消費）
        ItemStack currentItem = inv.getItem(sourceSlot);
        if (currentItem != null && currentItem.getType() == source.getType()) {
            if (currentItem.getAmount() <= quantity) {
                inv.setItem(sourceSlot, null);
            } else {
                currentItem.setAmount(currentItem.getAmount() - quantity);
            }
        }

        // 投出エフェクト
        dropLocation.getWorld().spawnParticle(
            org.bukkit.Particle.ENCHANT, dropLocation, 6, 0.3, 0.3, 0.3, 0.5
        );
        dropLocation.getWorld().playSound(dropLocation,
            org.bukkit.Sound.ENTITY_ARROW_SHOOT,
            org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    /**
     * アイテムをキャスターの視線方向に射出する（ディスペンサー挙動）。
     */
    private void launchItem(Player caster, Location from, ItemStack item) {
        org.bukkit.util.Vector direction = caster.getLocation().getDirection().normalize().multiply(0.7);
        Location spawnLoc = from.clone().add(0, 0.5, 0);
        org.bukkit.entity.Item droppedItem = spawnLoc.getWorld().dropItem(spawnLoc, item);
        droppedItem.setVelocity(direction);
        droppedItem.setPickupDelay(20); // 1秒後に拾える
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "投擲"; }

    @Override
    public String getDescription() { return "アイテムを投出する"; }

    @Override
    public int getManaCost() { return config.getManaCost("toss"); }

    @Override
    public int getTier() { return config.getTier("toss"); }
}
