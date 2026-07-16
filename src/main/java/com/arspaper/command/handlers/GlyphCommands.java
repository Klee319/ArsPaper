package com.arspaper.command.handlers;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.SpellComponent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;

/**
 * /ars glyph unlockall / lockall 系サブコマンドの実装。
 */
public final class GlyphCommands {

    private GlyphCommands() {}

    public static int executeGlyphUnlockAll(ArsPaper plugin, Player player) {
        Set<String> allGlyphs = new HashSet<>();
        for (SpellComponent comp : plugin.getSpellRegistry().getAll()) {
            allGlyphs.add(comp.getId().toString());
        }

        JsonArray arr = new JsonArray();
        allGlyphs.forEach(arr::add);
        player.getPersistentDataContainer().set(
            ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING, new Gson().toJson(arr)
        );

        player.sendMessage(Component.text(
            allGlyphs.size() + " 個の全グリフをアンロックしました", NamedTextColor.GREEN));
        return 1;
    }

    public static int executeGlyphLockAll(Player player) {
        player.getPersistentDataContainer().remove(ManaKeys.UNLOCKED_GLYPHS);
        player.sendMessage(Component.text(
            "全グリフをロックしました", NamedTextColor.YELLOW));
        return 1;
    }
}
