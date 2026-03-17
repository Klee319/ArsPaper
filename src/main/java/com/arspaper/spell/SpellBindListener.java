package com.arspaper.spell;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * スペルバインドシステム。
 * 任意アイテムにスペルをバインドし、右クリックでスペル発動。
 *
 * バインド条件: displayName以外のNBT（エンチャント等）を持たないアイテムのみ。
 * バインド/解除はコマンド /ars spell bind / unbind で行う。
 */
public class SpellBindListener implements Listener {

    private static final String BIND_SPELL_KEY = "bound_spell";
    private static final org.bukkit.NamespacedKey BOUND_SPELL =
        new org.bukkit.NamespacedKey("arspaper", BIND_SPELL_KEY);

    /**
     * バインド済みアイテムの右クリックでスペル発動。
     */
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        // カスタムアイテム（スペルブック等）は既存のリスナーで処理
        String customId = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        if (customId != null) return;

        // バインドされたスペルデータを取得
        String spellJson = item.getItemMeta().getPersistentDataContainer()
            .get(BOUND_SPELL, PersistentDataType.STRING);
        if (spellJson == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        SpellRegistry registry = ArsPaper.getInstance().getSpellRegistry();
        SpellRecipe recipe = SpellSerializer.deserialize(spellJson, registry);

        if (recipe == null || !recipe.isValid()) {
            player.sendMessage(Component.text("バインドされたスペルが無効です", NamedTextColor.RED));
            return;
        }

        ArsPaper.getInstance().getSpellCaster().cast(player, recipe);
    }

    /**
     * アイテムにスペルをバインドする。
     * @return 成功したらtrue
     */
    public static boolean bindSpell(Player player, ItemStack item, SpellRecipe recipe) {
        if (!canBind(item)) {
            player.sendMessage(Component.text(
                "エンチャント等のNBTを持つアイテムにはバインドできません", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        String spellJson = SpellSerializer.serialize(recipe);

        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(BOUND_SPELL, PersistentDataType.STRING, spellJson);

            // Loreにスペル情報を追加
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            // 既存のバインド情報を除去
            lore.removeIf(line -> {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line);
                return plain.startsWith("スペル:") || plain.startsWith("マナ:");
            });
            lore.add(Component.text("スペル: " + recipe.getName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("マナ: " + recipe.getTotalManaCost(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });

        player.sendMessage(Component.text(
            recipe.getName() + " をバインドしました", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);
        return true;
    }

    /**
     * アイテムのスペルバインドを解除する。
     */
    public static boolean unbindSpell(Player player, ItemStack item) {
        if (!item.hasItemMeta()) return false;

        String existing = item.getItemMeta().getPersistentDataContainer()
            .get(BOUND_SPELL, PersistentDataType.STRING);
        if (existing == null) {
            player.sendMessage(Component.text("このアイテムにはスペルがバインドされていません", NamedTextColor.RED));
            return false;
        }

        item.editMeta(meta -> {
            meta.getPersistentDataContainer().remove(BOUND_SPELL);

            // バインドLoreを除去
            List<Component> lore = meta.lore();
            if (lore != null) {
                lore.removeIf(line -> {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(line);
                    return plain.startsWith("スペル:") || plain.startsWith("マナ:");
                });
                meta.lore(lore.isEmpty() ? null : lore);
            }
        });

        player.sendMessage(Component.text("スペルバインドを解除しました", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.3f, 1.0f);
        return true;
    }

    /**
     * アイテムがバインド可能か判定。
     * displayName以外のNBT（エンチャント、属性等）を持つアイテムはバインド不可。
     */
    public static boolean canBind(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        // カスタムアイテムはバインド不可
        if (item.hasItemMeta()) {
            String customId = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if (customId != null) return false;
        }

        // エンチャントがあるアイテムはバインド不可
        if (!item.getEnchantments().isEmpty()) return false;

        // ItemMetaの追加チェック
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            // 耐久値が変更されているアイテムは不可
            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                if (damageable.getDamage() > 0) return false;
            }
            // 属性修飾子があるアイテムは不可
            if (meta.hasAttributeModifiers()) return false;
        }

        return true;
    }
}
