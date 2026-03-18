package com.arspaper.spell;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

/**
 * スペルバインドシステム（参照ベース）。
 * バインド済みアイテムには魔導書のUUIDとスロット番号を保持し、
 * 発動時にプレイヤーインベントリ内の該当魔導書からスペルを読み取る。
 */
public class SpellBindListener implements Listener {

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

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String bookUuid = pdc.get(ItemKeys.BOUND_BOOK_UUID, PersistentDataType.STRING);
        Integer spellSlot = pdc.get(ItemKeys.BOUND_SPELL_SLOT, PersistentDataType.INTEGER);
        if (bookUuid == null || spellSlot == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        // プレイヤーのインベントリから該当UUIDのスペルブックを検索
        ItemStack bookItem = findBookByUuid(player, bookUuid);
        if (bookItem == null) {
            player.sendMessage(Component.text("§c魔導書が必要です"));
            return;
        }

        // スペルブックからスペルを読み取る
        String slotsJson = bookItem.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) {
            player.sendMessage(Component.text("魔導書にスペルが設定されていません", NamedTextColor.RED));
            return;
        }

        SpellRegistry registry = ArsPaper.getInstance().getSpellRegistry();
        List<SpellRecipe> slots = SpellSerializer.deserializeSlots(slotsJson, registry);
        if (spellSlot >= slots.size() || slots.get(spellSlot) == null) {
            player.sendMessage(Component.text("指定スロットにスペルが設定されていません", NamedTextColor.RED));
            return;
        }

        SpellRecipe recipe = slots.get(spellSlot);
        if (!recipe.isValid()) {
            player.sendMessage(Component.text("バインドされたスペルが無効です", NamedTextColor.RED));
            return;
        }

        ArsPaper.getInstance().getSpellCaster().cast(player, recipe);
    }

    /**
     * プレイヤーのインベントリから指定UUIDのスペルブックを検索する。
     */
    private static ItemStack findBookByUuid(Player player, String uuid) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !stack.hasItemMeta()) continue;
            String bookUuid = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.SPELL_BOOK_UUID, PersistentDataType.STRING);
            if (uuid.equals(bookUuid)) return stack;
        }
        return null;
    }

    /**
     * アイテムにスペルをバインドする（参照ベース）。
     * バインド先アイテムには魔導書のUUIDとスロット番号のみを保持する。
     *
     * @param player プレイヤー
     * @param item バインド先アイテム
     * @param bookItem スペルブックアイテム
     * @param spellSlot スペルスロット番号（0-indexed）
     * @param recipe 表示用のスペルレシピ（Lore生成に使用）
     * @return 成功したらtrue
     */
    public static boolean bindSpell(Player player, ItemStack item, ItemStack bookItem,
                                     int spellSlot, SpellRecipe recipe) {
        if (!canBind(item)) {
            player.sendMessage(Component.text(
                "エンチャント等のNBTを持つアイテムにはバインドできません", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        String bookUuid = bookItem.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_BOOK_UUID, PersistentDataType.STRING);
        if (bookUuid == null) {
            player.sendMessage(Component.text("魔導書にUUIDがありません", NamedTextColor.RED));
            return false;
        }

        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ItemKeys.BOUND_BOOK_UUID, PersistentDataType.STRING, bookUuid);
            pdc.set(ItemKeys.BOUND_SPELL_SLOT, PersistentDataType.INTEGER, spellSlot);

            // Loreにスペル情報を追加
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            // 既存のバインド情報を除去
            lore.removeIf(line -> {
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                return plain.startsWith("スペル:") || plain.startsWith("マナ:")
                    || plain.contains("魔導書を持っている必要があります");
            });
            lore.add(Component.text("スペル: " + recipe.getName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("マナ: " + recipe.getTotalManaCost(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("発動時には魔導書を持っている必要があります", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
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

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String existing = pdc.get(ItemKeys.BOUND_BOOK_UUID, PersistentDataType.STRING);
        if (existing == null) {
            player.sendMessage(Component.text("このアイテムにはスペルがバインドされていません", NamedTextColor.RED));
            return false;
        }

        item.editMeta(meta -> {
            meta.getPersistentDataContainer().remove(ItemKeys.BOUND_BOOK_UUID);
            meta.getPersistentDataContainer().remove(ItemKeys.BOUND_SPELL_SLOT);

            // バインドLoreを除去
            List<Component> lore = meta.lore();
            if (lore != null) {
                lore = new ArrayList<>(lore);
                lore.removeIf(line -> {
                    String plain = PlainTextComponentSerializer.plainText().serialize(line);
                    return plain.startsWith("スペル:") || plain.startsWith("マナ:")
                        || plain.contains("魔導書を持っている必要があります");
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
