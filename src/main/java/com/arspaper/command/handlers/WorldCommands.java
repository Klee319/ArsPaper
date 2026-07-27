package com.arspaper.command.handlers;

import com.arspaper.ArsPaper;
import com.arspaper.spell.GlyphNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * /ars world 系サブコマンド（ban / maxmana / fixmana / info 等）の実装。
 */
public final class WorldCommands {

    private WorldCommands() {}

    public static int executeWorldBan(ArsPaper plugin, Player player) {
        String worldName = player.getWorld().getName();
        new com.arspaper.gui.SpellBanGui(player, plugin, worldName).open();
        return 1;
    }

    public static int executeWorldManaSetting(ArsPaper plugin, Player player, String type, int value) {
        String worldName = player.getWorld().getName();
        var wsm = plugin.getWorldSettingsManager();
        String label;

        switch (type) {
            case "maxmana" -> {
                wsm.setWorldManaMaxBonus(worldName, value);
                label = "最大マナ補正";
            }
            case "maxrgmana" -> {
                wsm.setWorldManaRegenBonus(worldName, value);
                label = "回復量補正";
            }
            case "fixmana" -> {
                wsm.setWorldManaFixMax(worldName, value);
                label = "固定最大マナ" + (value < 0 ? " (無効)" : "");
            }
            case "fixrgmana" -> {
                wsm.setWorldManaFixRegen(worldName, value);
                label = "固定回復量" + (value < 0 ? " (無効)" : "");
            }
            default -> {
                return 0;
            }
        }

        player.sendMessage(Component.text(
            worldName + " の" + label + "を " + value + " に設定しました", NamedTextColor.GREEN));
        return 1;
    }

    public static int executeWorldInfo(ArsPaper plugin, Player player) {
        String worldName = player.getWorld().getName();
        var wsm = plugin.getWorldSettingsManager();
        var mana = wsm.getWorldMana(worldName);
        var bans = wsm.getBannedSpells(worldName);

        player.sendMessage(Component.text("═══ ワールド設定: " + worldName + " ═══", NamedTextColor.GOLD));

        // マナ設定
        player.sendMessage(Component.text("マナ補正:", NamedTextColor.AQUA));
        if (mana.hasFixedMax()) {
            player.sendMessage(Component.text("  固定最大マナ: " + mana.fixMax(), NamedTextColor.WHITE));
        } else {
            player.sendMessage(Component.text("  最大マナ補正: " + (mana.maxBonus() >= 0 ? "+" : "") + mana.maxBonus(), NamedTextColor.WHITE));
        }
        if (mana.hasFixedRegen()) {
            player.sendMessage(Component.text("  固定回復量: " + mana.fixRegen(), NamedTextColor.WHITE));
        } else {
            player.sendMessage(Component.text("  回復量補正: " + (mana.regenBonus() >= 0 ? "+" : "") + mana.regenBonus(), NamedTextColor.WHITE));
        }

        // BAN設定
        if (bans.isEmpty()) {
            player.sendMessage(Component.text("BAN: なし", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("BAN (" + bans.size() + "件):", NamedTextColor.RED));
            StringBuilder sb = new StringBuilder("  ");
            for (String key : bans) {
                var comp = plugin.getSpellRegistry().get(key);
                if (comp != null) {
                    if (sb.length() > 2) sb.append(", ");
                    sb.append(GlyphNames.display(comp));
                }
            }
            player.sendMessage(Component.text(sb.toString(), NamedTextColor.GRAY));
        }
        return 1;
    }
}
