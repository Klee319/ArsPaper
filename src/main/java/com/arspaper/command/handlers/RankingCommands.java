package com.arspaper.command.handlers;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /ars ranking glyphs / mana 系サブコマンドの実装。
 */
public final class RankingCommands {

    private RankingCommands() {}

    public static int executeRankingGlyphs(ArsPaper plugin, CommandSender sender) {
        int totalGlyphs = plugin.getSpellRegistry().getAll().size();
        record Entry(String name, int count, boolean online) {}
        java.util.Map<String, Entry> entryMap = new java.util.LinkedHashMap<>();

        // キャッシュからオフライン含む全プレイヤーを読み込み
        for (var cached : plugin.getManaManager().getRankingCache().getAll().entrySet()) {
            var data = cached.getValue();
            entryMap.put(cached.getKey(), new Entry(data.name(), data.glyphCount(), false));
        }

        // オンラインプレイヤーの最新データで上書き
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            String json = p.getPersistentDataContainer()
                .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
            int count = 0;
            if (json != null) {
                try {
                    JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                    count = arr.size();
                } catch (Exception ignored) {}
            }
            entryMap.put(p.getUniqueId().toString(), new Entry(p.getName(), count, true));
        }

        List<Entry> entries = new ArrayList<>(entryMap.values());
        entries.sort((a, b) -> Integer.compare(b.count(), a.count()));

        sender.sendMessage(Component.text("=== グリフ解放数ランキング ===", NamedTextColor.GOLD));
        int rank = 0;
        for (Entry entry : entries) {
            rank++;
            if (rank > 10) break;
            String medal = switch (rank) {
                case 1 -> "§6①";
                case 2 -> "§7②";
                case 3 -> "§c③";
                default -> "§8" + rank;
            };
            double pct = totalGlyphs > 0 ? (entry.count() * 100.0 / totalGlyphs) : 0;
            String onlineMarker = entry.online() ? "" : " §8[OFF]";
            sender.sendMessage(Component.text(
                medal + " " + entry.name() + onlineMarker + " §f" + entry.count() + "/" + totalGlyphs
                    + " §7(" + String.format("%.0f", pct) + "%)"
            ));
        }
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("データがありません", NamedTextColor.GRAY));
        }
        return 1;
    }

    public static int executeRankingMana(ArsPaper plugin, CommandSender sender) {
        record Entry(String name, long consumed, boolean online) {}
        java.util.Map<String, Entry> entryMap = new java.util.LinkedHashMap<>();

        // キャッシュからオフライン含む全プレイヤーを読み込み
        for (var cached : plugin.getManaManager().getRankingCache().getAll().entrySet()) {
            var data = cached.getValue();
            entryMap.put(cached.getKey(), new Entry(data.name(), data.manaConsumed(), false));
        }

        // オンラインプレイヤーの最新データで上書き
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            long consumed = plugin.getManaManager().getTotalManaConsumed(p);
            entryMap.put(p.getUniqueId().toString(), new Entry(p.getName(), consumed, true));
        }

        List<Entry> entries = new ArrayList<>(entryMap.values());
        entries.sort((a, b) -> Long.compare(b.consumed(), a.consumed()));

        sender.sendMessage(Component.text("=== マナ消費量ランキング ===", NamedTextColor.AQUA));
        int rank = 0;
        for (Entry entry : entries) {
            rank++;
            if (rank > 10) break;
            String medal = switch (rank) {
                case 1 -> "§6①";
                case 2 -> "§7②";
                case 3 -> "§c③";
                default -> "§8" + rank;
            };
            String formatted = formatNumber(entry.consumed());
            String onlineMarker = entry.online() ? "" : " §8[OFF]";
            sender.sendMessage(Component.text(
                medal + " " + entry.name() + onlineMarker + " §b" + formatted + " マナ"
            ));
        }
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("データがありません", NamedTextColor.GRAY));
        }
        return 1;
    }

    private static String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
