package com.arspaper.spell;

import com.arspaper.ArsPaper;
import com.arspaper.enchant.ArsEnchantments;
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

        // Lore遅延更新: スペル内容が変更されている場合にLoreを最新化
        updateBindLore(item, recipe);

        // スニーク+上を向いている場合はスペル構成を表示
        if (player.isSneaking() && player.getLocation().getPitch() < -60) {
            displaySpellComposition(player, recipe);
            return;
        }

        // 共有エンチャントチェック: 魔導書にshareエンチャントがあればグリフチェックをスキップ
        boolean sharedSpell = ArsEnchantments.hasShareEnchant(bookItem);
        ArsPaper.getInstance().getSpellCaster().cast(player, recipe, sharedSpell);
    }

    /**
     * スペル構成をチャットに表示する。
     * Form=緑, Effect=黄, Augment=水色 で色分けし、日本語名で表示。
     */
    private void displaySpellComposition(Player player, SpellRecipe recipe) {
        player.sendMessage(Component.text("━━━ スペル構成 ━━━", NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("名前: ", NamedTextColor.GRAY)
            .append(Component.text(recipe.getName(), NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.text("マナ消費: ", NamedTextColor.GRAY)
            .append(Component.text(recipe.getTotalManaCost(), NamedTextColor.AQUA)));

        // グリフ構成を種別ごとに色分けして表示
        Component glyphLine = Component.text("構成: ", NamedTextColor.GRAY);
        boolean first = true;
        for (SpellComponent comp : recipe.getComponents()) {
            if (!first) {
                glyphLine = glyphLine.append(Component.text(" → ", NamedTextColor.DARK_GRAY));
            }
            NamedTextColor color = switch (comp.getType()) {
                case FORM -> NamedTextColor.GREEN;
                case EFFECT -> NamedTextColor.YELLOW;
                case AUGMENT -> NamedTextColor.AQUA;
            };
            glyphLine = glyphLine.append(Component.text(comp.getDisplayName(), color));
            first = false;
        }
        player.sendMessage(glyphLine);
        player.sendMessage(Component.text("━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.3f, 1.8f);
    }

    /**
     * プレイヤーのインベントリから指定UUIDのスペルブックを検索する。
     * 検索順: オフハンド → インベントリ右端から左端（スロット35→0）
     */
    private static ItemStack findBookByUuid(Player player, String uuid) {
        // 1. オフハンド優先
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.hasItemMeta()) {
            String bookUuid = offhand.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.SPELL_BOOK_UUID, PersistentDataType.STRING);
            if (uuid.equals(bookUuid)) return offhand;
        }

        // 2. インベントリを右端から検索（スロット35→0）
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = contents.length - 1; i >= 0; i--) {
            ItemStack stack = contents[i];
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
                "このアイテムにはバインドできません", NamedTextColor.RED));
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
                    || plain.contains("魔導書") || plain.contains("オフハンド");
            });
            lore.add(Component.text("スペル: " + recipe.getName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("マナ: " + recipe.getTotalManaCost(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("魔導書必要", NamedTextColor.GRAY)
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
                        || plain.contains("魔導書");
                });
                meta.lore(lore.isEmpty() ? null : lore);
            }
        });

        player.sendMessage(Component.text("スペルバインドを解除しました", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.3f, 1.0f);
        return true;
    }

    /**
     * バインド済みアイテムのLoreを最新のスペル内容で更新する（遅延更新）。
     */
    private static void updateBindLore(ItemStack item, SpellRecipe recipe) {
        item.editMeta(meta -> {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            // 既存のバインド情報を除去
            lore.removeIf(line -> {
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                return plain.startsWith("スペル:") || plain.startsWith("マナ:")
                    || plain.contains("魔導書");
            });
            lore.add(Component.text("スペル: " + recipe.getName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("マナ: " + recipe.getTotalManaCost(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("魔導書必要", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
            meta.lore(lore);
        });
    }

    /**
     * アイテムがバインド可能か判定。
     * ArsPaperのカスタムアイテム（スペルブック等）のみバインド不可。
     * エンチャント・属性修飾子・耐久値を持つアイテムにもバインド可能。
     */
    public static boolean canBind(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        // ArsPaperのカスタムアイテムはバインド不可（スペルブック/ワンド等）
        if (item.hasItemMeta()) {
            String customId = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if (customId != null) return false;
        }

        return true;
    }
}
