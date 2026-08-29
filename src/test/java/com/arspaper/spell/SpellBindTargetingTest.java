package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellBindTargetingTest {

    @Test
    @DisplayName("オフハンドが空でなければ常にオフハンド。空ならホットバー9")
    void offhandWinsEvenWhenHotbarNineIsOccupied() {
        assertEquals(SpellBindTargeting.Slot.OFFHAND, SpellBindTargeting.resolve(true, true));
        assertEquals(SpellBindTargeting.Slot.OFFHAND, SpellBindTargeting.resolve(true, false));
        assertEquals(SpellBindTargeting.Slot.HOTBAR_9, SpellBindTargeting.resolve(false, true));
        assertEquals(SpellBindTargeting.Slot.NONE, SpellBindTargeting.resolve(false, false));
        assertEquals(8, SpellBindTargeting.HOTBAR_SLOT_9);
    }

    @Test
    @DisplayName("GUI とコマンドが occupiedTarget を呼んでいる")
    void bindUnbindCallSitesUseOccupiedTarget() throws IOException {
        String gui = Files.readString(Path.of("src/main/java/com/arspaper/gui/SpellSettingsGui.java"),
                StandardCharsets.UTF_8);
        assertTrue(gui.contains("SpellBindTargeting.occupiedTarget(player)"),
                "設定GUIのバインドがオフハンド固定の解決を使っていない");
        assertFalse(gui.contains("getItemInMainHand()"),
                "設定GUIがメインハンドをバインド先にしている");

        String commands = Files.readString(
                Path.of("src/main/java/com/arspaper/command/handlers/SpellCommands.java"),
                StandardCharsets.UTF_8);
        int unbindAt = commands.indexOf("executeSpellUnbind");
        assertTrue(unbindAt >= 0, "unbind コマンドが無い");
        String unbindMethod = commands.substring(unbindAt, commands.indexOf("executeSpellList", unbindAt));
        assertTrue(unbindMethod.contains("SpellBindTargeting.occupiedTarget(player)"),
                "unbind がオフハンド固定の解決を使っていない");
        assertFalse(unbindMethod.contains("getItemInMainHand"),
                "unbind がメインハンドを見ている");
    }
}
