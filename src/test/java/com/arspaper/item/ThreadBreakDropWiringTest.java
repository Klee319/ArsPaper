package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装備が壊れると装着スレッドが PDC ごと消える件の配線固定。
 *
 * <p>このフォークに MockBukkit は無いのでリスナー本体は実行できない。
 * 「誰が壊れたスタックを見るか」「組み直しが GUI 取り外しと同じか」だけをソースで縛る。
 */
class ThreadBreakDropWiringTest {

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("破壊イベントで装着スレッドを GUI と同じ経路で返す")
    void breakListenerIsRegisteredAndUsesGuiRestore() throws IOException {
        String plugin = read("src/main/java/com/arspaper/ArsPaper.java");
        assertTrue(plugin.contains("new ThreadBreakDropListener()"),
                "ThreadBreakDropListener が registerEvents されていない"
                        + " = バニラ破壊でスレッドが装備と一緒に消える");

        String listener = read("src/main/java/com/arspaper/item/ThreadBreakDropListener.java");
        assertTrue(listener.contains("PlayerItemBreakEvent"),
                "PlayerItemBreakEvent を見ていない");
        assertTrue(listener.contains("SocketedThreadReturn.returnFromBrokenGear"),
                "組み直しが GUI 取り外しと別経路になっている");

        String restore = read("src/main/java/com/arspaper/gui/SocketedThreadReturn.java");
        assertTrue(restore.contains("SocketedThreads.readAll"),
                "枠上限で打ち切ると、効いていなかった超過枠が装備と一緒に消える");
        assertTrue(restore.contains("restoreRoll"),
                "厳選を戻さないと外して付け直す無限リロールになる");
        assertTrue(restore.contains("BackpackGui.transferDataToThread"),
                "バックパック中身が装備 PDC に残ったまま消える");

        String caster = read("src/main/java/com/arspaper/spell/SpellCaster.java");
        assertTrue(caster.contains("PlayerItemBreakEvent"),
                "詠唱で触媒が壊れる経路がイベントを飛ばないとスレッドが消える");
    }
}
