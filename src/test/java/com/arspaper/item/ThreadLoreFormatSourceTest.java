package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * スレッド lore の体裁。Bukkit ランタイムが無いのでソース走査で固定する。
 */
class ThreadLoreFormatSourceTest {

    @Test
    @DisplayName("セット効果は Nセット + 全角インデントで、個以上の旧見出しを使わない")
    void setEffectUsesSetCountAndFullwidthIndent() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/com/arspaper/item/impl/ThreadItem.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("count + \"セット\""), "Nセット見出しが無い");
        assertTrue(src.contains("\"　\""), "全角スペースのインデントが無い");
        assertFalse(src.contains("個以上"), "旧見出し 個以上 が残っている");
        assertFalse(src.contains("同じ種類を装備した合計本数"), "セット効果の括弧説明が残っている");
    }

    @Test
    @DisplayName("フレーバーは LIGHT_PURPLE の1行")
    void flavorIsForcedLightPurple() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/com/arspaper/item/ThreadConfig.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("flavorLine"), "フレーバー専用経路が無い");
        assertTrue(src.contains("NamedTextColor.LIGHT_PURPLE"), "フレーバーが紫でない");
        assertTrue(src.contains("extra.get(0)"), "lore の先頭1行だけを使っていない");
    }
}
