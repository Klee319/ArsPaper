package com.arspaper.enchant;

import com.arspaper.item.ArmorManaListener;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 金床でカスタムエンチャント本をメイジアーマー/スペルブックに適用するリスナー。
 * Paper Registry APIで登録された正式なエンチャントを使用。
 */
public class EnchantBookListener implements Listener {

    /** 金床連打による二重消費を防止するクールダウン */
    private final java.util.Set<java.util.UUID> anvilCooldown = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 金床にアイテムがセットされた時、結果スロットにプレビューを表示する。
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack target = anvil.getItem(0);
        ItemStack book = anvil.getItem(1);

        // エンチャント本同士の合成をブロック（レベル加算防止）
        if (isEnchantBook(target) && isEnchantBook(book)) {
            event.setResult(null);
            return;
        }

        if (!isEnchantBook(book)) return;
        boolean isArmor = isMageArmor(target);
        boolean isSpellBook = isSpellBook(target);
        boolean isDamageable = target != null && target.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable;

        if (!isArmor && !isSpellBook && !isDamageable) return;

        // エンチャント本からエンチャントを読み取る
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) book.getItemMeta();

        for (String enchantId : new String[]{"mana_regen", "mana_boost", "soulbound", "share"}) {
            // 適用対象フィルタ
            if ("share".equals(enchantId) && !isSpellBook) continue;
            if ("soulbound".equals(enchantId) && !isDamageable) continue;
            if (!"share".equals(enchantId) && !"soulbound".equals(enchantId) && !isArmor) continue;

            Enchantment enchant = ArsEnchantments.getFromId(enchantId);
            if (enchant == null) continue;
            int level = bookMeta.getStoredEnchantLevel(enchant);
            if (level <= 0) continue;

            if ("share".equals(enchantId)) level = 1;

            int existingLevel = target.getEnchantmentLevel(enchant);
            if (existingLevel >= level) continue;

            // 結果アイテムを生成
            ItemStack result = target.clone();
            String displayName = ArsEnchantments.getDisplayName(enchantId);
            int finalLevel = level;
            result.editMeta(meta -> {
                // loreから旧エンチャント表示を除去
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.removeIf(line -> {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(line);
                    return plain.startsWith(displayName);
                });
                String loreText = "share".equals(enchantId)
                    ? displayName
                    : displayName + " " + ArsEnchantments.toRoman(finalLevel);
                lore.add(Component.text(loreText, NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            // Bukkit Enchantment APIでエンチャント適用（MAX_LEVELを超えないよう制限）
            result.addUnsafeEnchantment(enchant, Math.min(finalLevel, ArsEnchantments.MAX_LEVEL));

            event.setResult(result);
            // 回生エンチャントは付けなおしを考慮してコスト10固定
            if ("soulbound".equals(enchantId)) {
                result.editMeta(meta -> {
                    if (meta instanceof org.bukkit.inventory.meta.Repairable repairable) {
                        repairable.setRepairCost(0); // 累積ペナルティをリセット
                    }
                });
                anvil.setRepairCost(10);
            } else {
                anvil.setRepairCost(1);
            }
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
        if (result == null || result.getType().isAir()) return;

        // 連打防止
        if (!anvilCooldown.add(player.getUniqueId())) return;
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            com.arspaper.ArsPaper.getInstance(),
            () -> anvilCooldown.remove(player.getUniqueId()), 5L);

        ItemStack book = anvil.getItem(1);
        if (!isEnchantBook(book)) return;

        // カスタムエンチャントが実際に適用されたか確認
        boolean hasCustomEnchant = false;
        for (String enchantId : new String[]{"mana_regen", "mana_boost", "soulbound", "share"}) {
            Enchantment enchant = ArsEnchantments.getFromId(enchantId);
            if (enchant != null && result.getEnchantmentLevel(enchant) > 0) {
                hasCustomEnchant = true;
                break;
            }
        }
        if (!hasCustomEnchant) return;

        // エンチャント本を消費
        ItemStack bookSlot = anvil.getItem(1);
        if (bookSlot != null) {
            bookSlot.setAmount(bookSlot.getAmount() - 1);
        }

        // 取り出し後にボーナス再計算をスケジュール（防具の場合のみ）
        if (isMageArmor(result)) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                com.arspaper.ArsPaper.getInstance(), () -> {
                    if (player.isOnline()) {
                        ArmorManaListener.recalculateArmorBonus(player);
                    }
                }, 2L
            );
        }
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
        if (customId != null && customId.startsWith("mage_")) return true;
        // ConfigurableArmor
        return item.getItemMeta().getPersistentDataContainer()
            .has(ItemKeys.ARMOR_SET_ID, PersistentDataType.STRING);
    }

    private boolean isSpellBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String customId = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        return customId != null && customId.startsWith("spell_book_");
    }
}
