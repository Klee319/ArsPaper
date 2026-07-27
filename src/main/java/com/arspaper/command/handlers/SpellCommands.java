package com.arspaper.command.handlers;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import com.arspaper.spell.GlyphNames;
import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.SpellComponent;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellRecipe;
import com.arspaper.spell.SpellRegistry;
import com.arspaper.spell.SpellSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /ars spell 系サブコマンド（list / set / bind / unbind）の実装。
 */
public final class SpellCommands {

    private SpellCommands() {}

    public static int executeSpellBind(ArsPaper plugin, Player player, int slot) {
        // オフハンドまたはホットバー9番スロットからバインド対象を検索
        ItemStack offhand = findBindTarget(player);
        if (offhand == null) {
            player.sendMessage(Component.text("オフハンドまたはホットバー9番スロットにバインド先のアイテムを持ってください", NamedTextColor.RED));
            return 0;
        }

        // メインハンドのスペルブックからスペルを取得
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        String customId = mainHand.hasItemMeta()
            ? mainHand.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING)
            : null;
        if (customId == null || (!customId.startsWith("spell_book_") && !customId.startsWith("wand_"))) {
            player.sendMessage(Component.text("メインハンドにスペルブック/ワンドを持ってください", NamedTextColor.RED));
            return 0;
        }

        String slotsJson = mainHand.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) {
            player.sendMessage(Component.text("スペルが設定されていません", NamedTextColor.RED));
            return 0;
        }

        java.util.List<com.arspaper.spell.SpellRecipe> slots =
            com.arspaper.spell.SpellSerializer.deserializeSlots(slotsJson, plugin.getSpellRegistry());
        int idx = slot - 1;
        if (idx >= slots.size() || slots.get(idx) == null) {
            player.sendMessage(Component.text("スロット" + slot + "にスペルがありません", NamedTextColor.RED));
            return 0;
        }

        // スペルブックにUUIDがなければ付与
        com.arspaper.item.impl.SpellBook.getOrCreateUUID(mainHand);
        // mainHandはスペルブック、offhandがバインド先アイテム
        return com.arspaper.spell.SpellBindListener.bindSpell(player, offhand, mainHand, idx, slots.get(idx)) ? 1 : 0;
    }

    /**
     * バインド対象アイテムを検索。オフハンド → ホットバースロット8(固定)。
     */
    private static ItemStack findBindTarget(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!offhand.getType().isAir()) return offhand;
        ItemStack slot8 = player.getInventory().getItem(8);
        if (slot8 != null && !slot8.getType().isAir()) {
            if (!slot8.hasItemMeta()) return slot8;
            String customId = slot8.getItemMeta().getPersistentDataContainer()
                .get(com.arspaper.item.ItemKeys.CUSTOM_ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING);
            if (customId == null || (!customId.contains("spell_book") && !customId.contains("wand"))) {
                return slot8;
            }
        }
        return null;
    }

    public static int executeSpellUnbind(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir()) {
            player.sendMessage(Component.text("バインド解除するアイテムを手に持ってください", NamedTextColor.RED));
            return 0;
        }
        return com.arspaper.spell.SpellBindListener.unbindSpell(player, mainHand) ? 1 : 0;
    }

    public static int executeSpellList(ArsPaper plugin, Player player) {
        player.sendMessage(Component.text("=== 利用可能なグリフ ===", NamedTextColor.GOLD));

        player.sendMessage(Component.text("形態(Form): ", NamedTextColor.GREEN)
            .append(Component.text(
                plugin.getSpellRegistry().getForms().stream()
                    .map(c -> GlyphNames.display(c))
                    .collect(Collectors.joining(", ")),
                NamedTextColor.WHITE
            )));

        player.sendMessage(Component.text("効果(Effect): ", NamedTextColor.YELLOW)
            .append(Component.text(
                plugin.getSpellRegistry().getEffects().stream()
                    .map(c -> GlyphNames.display(c))
                    .collect(Collectors.joining(", ")),
                NamedTextColor.WHITE
            )));

        player.sendMessage(Component.text("増強(Augment): ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(
                plugin.getSpellRegistry().getAugments().stream()
                    .map(c -> GlyphNames.display(c))
                    .collect(Collectors.joining(", ")),
                NamedTextColor.WHITE
            )));

        return 1;
    }

    public static int executeSpellSet(ArsPaper plugin, Player player, int slot, String spellDef) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String customId = hand.hasItemMeta()
            ? hand.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING)
            : null;

        if (customId == null || !customId.startsWith("spell_book_")) {
            player.sendMessage(Component.text("スペルブックを手に持って実行してください！", NamedTextColor.RED));
            return 0;
        }

        String[] parts = spellDef.split("\\s+");
        if (parts.length < 1) {
            player.sendMessage(Component.text("使い方: /ars spell set <スロット> <名前>:<形態> <効果/増強...>", NamedTextColor.RED));
            return 0;
        }

        String nameAndForm = parts[0];
        String[] nf = nameAndForm.split(":", 2);
        String spellName = nf[0];
        if (nf.length < 2) {
            player.sendMessage(Component.text("形式: <スペル名>:<形態> <効果...>", NamedTextColor.RED));
            return 0;
        }

        SpellRegistry registry = plugin.getSpellRegistry();
        List<SpellComponent> components = new ArrayList<>();

        SpellComponent form = registry.get("arspaper:" + nf[1].toLowerCase());
        if (form == null || form.getType() != SpellComponent.ComponentType.FORM) {
            player.sendMessage(Component.text("不明な形態: " + nf[1], NamedTextColor.RED));
            return 0;
        }
        components.add(form);

        for (int i = 1; i < parts.length; i++) {
            SpellComponent comp = registry.get("arspaper:" + parts[i].toLowerCase());
            if (comp == null) {
                player.sendMessage(Component.text("不明なグリフ: " + parts[i], NamedTextColor.RED));
                return 0;
            }
            components.add(comp);
        }

        SpellRecipe recipe = new SpellRecipe(spellName, components);
        if (!recipe.isValid()) {
            player.sendMessage(Component.text("無効なスペルです！先頭はFormである必要があります", NamedTextColor.RED));
            return 0;
        }

        // 使用ゲート(α): perk未所持のグリフが含まれていれば組み込み不可
        String missingPerkGlyph = plugin.getSpellCaster().firstMissingPerkGlyph(player, recipe);
        if (missingPerkGlyph != null) {
            player.sendMessage(Component.text(
                "使用権限のないグリフが含まれています: " + missingPerkGlyph, NamedTextColor.RED));
            return 0;
        }

        String existingSlotsJson = hand.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);

        List<SpellRecipe> slots;
        if (existingSlotsJson != null) {
            slots = new ArrayList<>(SpellSerializer.deserializeSlots(existingSlotsJson, registry));
        } else {
            slots = new ArrayList<>();
        }

        int slotIndex = slot - 1;
        while (slots.size() <= slotIndex) {
            slots.add(null);
        }
        slots.set(slotIndex, recipe);

        String newSlotsJson = SpellSerializer.serializeSlots(slots);
        hand.editMeta(meta ->
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOTS, PersistentDataType.STRING, newSlotsJson
            )
        );

        player.sendMessage(Component.text(
            "スロット" + slot + "に設定: " + spellName + " (マナコスト: " + recipe.getTotalManaCost() + ")",
            NamedTextColor.GREEN
        ));
        return 1;
    }
}
