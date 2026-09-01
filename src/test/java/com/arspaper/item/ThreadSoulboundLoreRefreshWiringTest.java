package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 魂縛スレッドの再描画で、PDCだけ残って表示が消える回帰を防ぐ。 */
class ThreadSoulboundLoreRefreshWiringTest {

    @Test
    @DisplayName("スレッド全lore再構築はTF所有者行と魂縛行をPDCから復元する")
    void fullLoreRestoresBothOwnerLines() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/arspaper/item/impl/ThreadItem.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("TrinityForgeBridge.appendOwnerLoreIfMissing(meta, lore)"),
                "スレッド専用lore再構築がTF所有者行を復元しない");
        assertTrue(source.contains("appendSoulboundLore(meta, lore)"),
                "スレッド専用lore再構築が魂縛所有者行を復元しない");
    }

    @Test
    @DisplayName("スレッド枠からの返却はArs台帳だけでなくTF所有者台帳も復元する")
    void socketReturnRestoresTheAuthoritativeOwner() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/arspaper/gui/SocketedThreadReturn.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("TrinityForgeBridge.bindSoulbound(meta, owner)"),
                "返却時にArs側PDCだけを復元するとTF所有者loreが消え、所有者台帳も二重化する");
        assertTrue(source.contains("TrinityForgeBridge.appendOwnerLoreIfMissing(meta, lore)"),
                "返却時にTF所有者行を直ちに戻していない。次の持ち替えまで表示だけ消える");
    }
}
