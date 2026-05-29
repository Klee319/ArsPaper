package com.arspaper.enchant;

import com.arspaper.item.ArmorManaListener;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
import java.util.logging.Logger;

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
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        try {
            handlePrepareAnvil(event);
        } catch (Exception e) {
            com.arspaper.ArsPaper.getInstance().getLogger().warning(
                "[Anvil] Exception in PrepareAnvilEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePrepareAnvil(PrepareAnvilEvent event) {
        Logger log = com.arspaper.ArsPaper.getInstance().getLogger();
        AnvilInventory anvil = event.getInventory();
        ItemStack slot0 = anvil.getItem(0);
        ItemStack slot1 = anvil.getItem(1);

        log.fine("[Anvil-Debug] PrepareAnvil fired: slot0=" + (slot0 != null ? slot0.getType() : "null")
            + ", slot1=" + (slot1 != null ? slot1.getType() : "null"));

        // エンチャント本同士の合成をブロック（レベル加算防止）
        if (isEnchantBook(slot0) && isEnchantBook(slot1)) {
            event.setResult(null);
            return;
        }

        // スロットの向きを検出: どちらがエンチャント本でどちらがターゲットか
        ItemStack target;
        ItemStack book;
        if (isEnchantBook(slot1)) {
            target = slot0;
            book = slot1;
        } else if (isEnchantBook(slot0)) {
            target = slot1;
            book = slot0;
        } else {
            log.fine("[Anvil-Debug] No enchant book detected. slot0 isEB=" + isEnchantBook(slot0)
                + ", slot1 isEB=" + isEnchantBook(slot1));
            return; // エンチャント本がない
        }

        if (target == null || target.getType().isAir()) {
            log.fine("[Anvil-Debug] Target is null or air");
            return;
        }

        boolean isArmor = isMageArmor(target);
        boolean isSpellBook = isSpellBook(target);
        boolean isDamageable = target.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable;

        log.fine("[Anvil-Debug] Target: " + target.getType() + " isArmor=" + isArmor
            + " isSpellBook=" + isSpellBook + " isDamageable=" + isDamageable);

        if (!isArmor && !isSpellBook && !isDamageable) {
            log.fine("[Anvil-Debug] Target is not armor, spellbook, or damageable - skipping");
            return;
        }

        // エンチャント本からエンチャントを読み取る
        if (!(book.getItemMeta() instanceof EnchantmentStorageMeta bookMeta)) {
            log.fine("[Anvil-Debug] Book meta is not EnchantmentStorageMeta: " + book.getItemMeta().getClass().getName());
            return;
        }

        log.fine("[Anvil-Debug] Stored enchants: " + bookMeta.getStoredEnchants());

        for (String enchantId : new String[]{"mana_regen", "mana_boost", "soulbound", "share"}) {
            // 適用対象フィルタ
            if ("share".equals(enchantId) && !isSpellBook) continue;
            if ("soulbound".equals(enchantId) && !isDamageable) continue;
            if (!"share".equals(enchantId) && !"soulbound".equals(enchantId) && !isArmor) continue;

            Enchantment enchant = ArsEnchantments.getFromId(enchantId);
            if (enchant == null) {
                log.fine("[Anvil-Debug] Enchant lookup returned null for: " + enchantId);
                continue;
            }
            int level = bookMeta.getStoredEnchantLevel(enchant);
            log.fine("[Anvil-Debug] " + enchantId + " storedLevel=" + level
                + " existingLevel=" + target.getEnchantmentLevel(enchant));
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

                // 累積ペナルティをリセット（バニラの"Too Expensive"回避）
                if (meta instanceof org.bukkit.inventory.meta.Repairable repairable) {
                    repairable.setRepairCost(0);
                }
            });
            // Bukkit Enchantment APIでエンチャント適用
            result.addUnsafeEnchantment(enchant, Math.min(finalLevel, ArsEnchantments.MAX_LEVEL));

            event.setResult(result);

            // コスト設定: 回生は10、それ以外は1
            int repairCost = "soulbound".equals(enchantId) ? 10 : 1;
            anvil.setRepairCost(repairCost);
            anvil.setMaximumRepairCost(100); // "Too Expensive" 回避

            log.info("[Anvil] Applied " + enchantId + " Lv" + finalLevel
                + " to " + target.getType() + " (cost=" + repairCost + ")");
            return;
        }
    }

    /**
     * 金床の結果スロットからアイテムを取り出した時の処理。
     *
     * カスタムエンチャント適用結果に限り、イベントをキャンセルして結果付与・素材消費・
     * XP徴収を完全に手動制御する。バニラの金床消費処理と併用すると、結果スロットの
     * カスタム結果に対してバニラが入力スロットを別途消費し、本が二重に減る（ロスト）ため。
     */
    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;

        // カスタムエンチャントが実際に適用された結果か確認（そうでなければバニラ処理に委ねる）
        boolean hasCustomEnchant = false;
        for (String enchantId : new String[]{"mana_regen", "mana_boost", "soulbound", "share"}) {
            Enchantment enchant = ArsEnchantments.getFromId(enchantId);
            if (enchant != null && result.getEnchantmentLevel(enchant) > 0) {
                hasCustomEnchant = true;
                break;
            }
        }
        if (!hasCustomEnchant) return;

        // 本とターゲットのスロット位置を特定
        int bookSlot = isEnchantBook(anvil.getItem(1)) ? 1
            : isEnchantBook(anvil.getItem(0)) ? 0 : -1;
        if (bookSlot == -1) return;
        int targetSlot = bookSlot == 1 ? 0 : 1;
        ItemStack book = anvil.getItem(bookSlot);
        if (book == null) return;

        // ここからは完全手動制御。二重消費を防ぐためバニラのクリック処理を抑止する。
        event.setCancelled(true);

        // 連打防止（キャンセル確定後にチェックし、ブロック時は何も起きない）
        if (!anvilCooldown.add(player.getUniqueId())) return;
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            com.arspaper.ArsPaper.getInstance(),
            () -> anvilCooldown.remove(player.getUniqueId()), 5L);

        // 結果の受け取り方を決定（通常クリック=カーソル / シフト=インベントリ）。
        // それ以外のクリック（数字キー・ドロップ等）はキャンセルのみで何もしない。
        boolean shift = event.isShiftClick();
        boolean normalPickup = event.getClick() == org.bukkit.event.inventory.ClickType.LEFT
            || event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT;
        if (!shift && !normalPickup) return;

        // カーソルに別アイテムがある場合は通常受け取り不可
        ItemStack cursor = event.getCursor();
        boolean cursorOccupied = !shift && cursor != null && !cursor.getType().isAir();
        if (cursorOccupied) return;

        // XPコストを徴収（クリエイティブは免除）
        int cost = anvil.getRepairCost();
        boolean chargeXp = player.getGameMode() != org.bukkit.GameMode.CREATIVE;
        if (chargeXp && player.getLevel() < cost) {
            return; // レベル不足 → 何も消費しない
        }

        // 結果をプレイヤーへ渡す（渡せなかった場合は消費せず中断）
        ItemStack toGive = result.clone();
        if (shift) {
            java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
            if (!leftover.isEmpty()) {
                return; // インベントリ満杯 → 消費せず中断
            }
        } else {
            player.setItemOnCursor(toGive);
        }

        // 受け渡し成功 → XPと入力スロットを消費
        if (chargeXp) {
            player.setLevel(player.getLevel() - cost);
        }
        anvil.setItem(targetSlot, null);
        int newBookAmount = book.getAmount() - 1;
        if (newBookAmount <= 0) {
            anvil.setItem(bookSlot, null);
        } else {
            ItemStack newBook = book.clone();
            newBook.setAmount(newBookAmount);
            anvil.setItem(bookSlot, newBook);
        }
        player.updateInventory();

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
