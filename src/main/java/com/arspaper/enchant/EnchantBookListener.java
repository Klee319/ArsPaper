package com.arspaper.enchant;

import com.arspaper.item.ArmorManaListener;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 金床でエンチャント本をメイジアーマーに適用するリスナー。
 * スロット0: メイジアーマー、スロット1: エンチャント本 → 結果にエンチャント済み防具を表示。
 */
public class EnchantBookListener implements Listener {

    /**
     * 金床にアイテムがセットされた時、結果スロットにプレビューを表示する。
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack armor = anvil.getItem(0);
        ItemStack book = anvil.getItem(1);

        if (!isMageArmor(armor) || !isEnchantBook(book)) return;

        PersistentDataContainer bookPdc = book.getItemMeta().getPersistentDataContainer();

        for (String enchantId : new String[]{"mana_regen", "mana_boost"}) {
            NamespacedKey key = ArsEnchantments.getKeyFromId(enchantId);
            if (key == null) continue;
            int level = bookPdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
            if (level <= 0) continue;

            int existingLevel = armor.getItemMeta().getPersistentDataContainer()
                .getOrDefault(key, PersistentDataType.INTEGER, 0);

            if (existingLevel >= level) continue;

            // 結果アイテムを生成
            ItemStack result = armor.clone();
            String displayName = ArsEnchantments.getDisplayName(enchantId);
            result.editMeta(meta -> {
                meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.removeIf(line -> {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(line);
                    return plain.startsWith(displayName);
                });
                lore.add(Component.text(
                    displayName + " " + ArsEnchantments.toRoman(level), NamedTextColor.LIGHT_PURPLE
                ).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });

            event.setResult(result);
            anvil.setRepairCost(1); // 経験値コスト1
            return;
        }
    }

    /**
     * 金床の結果スロットからアイテムを取り出した時、素材を消費する。
     */
    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || !isMageArmor(result)) return;

        ItemStack book = anvil.getItem(1);
        if (!isEnchantBook(book)) return;

        // エンチャントが実際に適用されたか確認
        PersistentDataContainer resultPdc = result.getItemMeta().getPersistentDataContainer();
        PersistentDataContainer bookPdc = book.getItemMeta().getPersistentDataContainer();
        boolean hasEnchant = false;
        for (String enchantId : new String[]{"mana_regen", "mana_boost"}) {
            NamespacedKey key = ArsEnchantments.getKeyFromId(enchantId);
            if (key == null) continue;
            int bookLevel = bookPdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
            int resultLevel = resultPdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
            if (bookLevel > 0 && resultLevel >= bookLevel) {
                hasEnchant = true;
                break;
            }
        }
        if (!hasEnchant) return;

        // 取り出し後にボーナス再計算をスケジュール
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            com.arspaper.ArsPaper.getInstance(), () -> {
                if (player.isOnline()) {
                    ArmorManaListener.recalculateArmorBonus(player);
                }
            }, 2L
        );
    }

    private boolean isEnchantBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String customId = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        return "enchant_book".equals(customId);
    }

    private boolean isMageArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String customId = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        return customId != null && customId.startsWith("mage_");
    }
}
