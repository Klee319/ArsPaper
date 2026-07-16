package com.arspaper.command.handlers;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * /ars give 系サブコマンドの実装。
 * ArsCommand の Brigadier 配線から委譲される。
 */
public final class GiveCommands {

    private GiveCommands() {}

    public static int executeGiveEnchantBook(Player player, String spec) {
        // enchant_book.enchantId.level
        String[] parts = spec.split("\\.");
        if (parts.length < 3) {
            player.sendMessage(Component.text(
                "形式: enchant_book.<enchantId>.<level> (例: enchant_book.mana_regen.1)", NamedTextColor.RED));
            return 0;
        }
        String enchantId = parts[1];
        int level;
        try {
            level = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("レベルは数値で指定してください", NamedTextColor.RED));
            return 0;
        }

        var enchant = com.arspaper.enchant.ArsEnchantments.getFromId(enchantId);
        if (enchant == null) {
            player.sendMessage(Component.text(
                "不明なエンチャント: " + enchantId + " (mana_regen/mana_boost/share/soulbound)", NamedTextColor.RED));
            return 0;
        }

        level = Math.max(1, Math.min(level, com.arspaper.enchant.ArsEnchantments.MAX_LEVEL));
        String displayName = com.arspaper.enchant.ArsEnchantments.getDisplayName(enchantId);
        String roman = com.arspaper.enchant.ArsEnchantments.toRoman(level);

        ItemStack book = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        final int finalLevel = level;
        book.editMeta(meta -> {
            meta.displayName(Component.text(displayName + " " + roman, net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(
                ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING, "enchant_book"
            );
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchant, finalLevel, true);
            }
            meta.lore(java.util.List.of(
                Component.text(displayName + " " + roman, net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("金床でメイジアーマーに適用", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            ));
        });

        player.getInventory().addItem(book);
        player.sendMessage(Component.text(displayName + " " + roman + " のエンチャント本を付与しました", NamedTextColor.GREEN));
        return 1;
    }

    public static int executeGive(ArsPaper plugin, Player player, String itemId, int count) {
        var optItem = plugin.getItemRegistry().get(itemId);
        if (optItem.isEmpty()) {
            player.sendMessage(Component.text("不明なアイテム: " + itemId, NamedTextColor.RED));
            return 0;
        }
        for (int i = 0; i < count; i++) {
            ItemStack stack = optItem.get().createItemStack();
            player.getInventory().addItem(stack);
        }
        String msg = count > 1
            ? itemId + " x" + count + " を " + player.getName() + " に付与しました"
            : itemId + " を " + player.getName() + " に付与しました";
        player.sendMessage(Component.text(msg, NamedTextColor.GREEN));
        return 1;
    }
}
