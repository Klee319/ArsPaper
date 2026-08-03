package com.arspaper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yml 生文字列 → 表示テキストの唯一の変換点 {@link DisplayText}。
 *
 * <p>ここが壊れると「レシピGUIに {@code &6&l無限ソース核精製} が出る」
 * (2026-08-03 実サーバ報告) の再発になる。
 */
class DisplayTextTest {

    @Test
    @DisplayName("レガシー &記法を解釈して記号を残さない")
    void parsesLegacyAmpersand() {
        assertEquals("無限ソース核", DisplayText.plain("&6&l無限ソース核"));
        assertEquals("ソースジャー II", DisplayText.plain("&bソースジャー II"));
        assertEquals("ヴォルカニックソースリンク II", DisplayText.plain("&cヴォルカニックソースリンク II"));
    }

    @Test
    @DisplayName("§ 形式のレガシーコードも解釈する")
    void parsesSectionSign() {
        assertEquals("特異点の壺", DisplayText.plain("§5特異点の壺"));
    }

    @Test
    @DisplayName("MiniMessage タグを解釈して記号を残さない")
    void parsesMiniMessage() {
        assertEquals("木のハルバード", DisplayText.plain("木の<b>ハルバード</b>"));
        assertEquals("深淵の剣", DisplayText.plain("<gradient:#333:#999>深淵の剣</gradient>"));
    }

    @Test
    @DisplayName("記法が無い素のテキストはそのまま")
    void keepsPlainText() {
        assertEquals("ソースジャー", DisplayText.plain("ソースジャー"));
        assertEquals("", DisplayText.plain((String) null));
    }

    @Test
    @DisplayName("component() は色を落とさず斜体だけ明示OFFにする")
    void componentKeepsColorAndDisablesItalic() {
        Component c = DisplayText.component("&6&l無限ソース核");
        assertEquals(TextDecoration.State.FALSE, c.decoration(TextDecoration.ITALIC));
        // 色/太字は子側に載る場合があるのでプレーン文字列と「色が失われていないこと」だけ見る。
        assertEquals("無限ソース核", DisplayText.plain(c));
        assertTrue(hasGold(c), "&6 が GOLD として解釈されていること");
    }

    @Test
    @DisplayName("hasMarkup は記法の有無を判定する")
    void detectsMarkup() {
        assertTrue(DisplayText.hasMarkup("&bソースジャー II"));
        assertTrue(DisplayText.hasMarkup("木の<b>ハルバード</b>"));
        assertFalse(DisplayText.hasMarkup("ソースジャー"));
        assertFalse(DisplayText.hasMarkup(null));
    }

    @Test
    @DisplayName("壊れた MiniMessage でも例外を投げず表示を諦めない")
    void survivesBrokenMarkup() {
        String broken = "<unknownTag>あいうえお";
        String plain = DisplayText.plain(broken);
        assertTrue(plain.contains("あいうえお"), "本文は残ること: " + plain);
    }

    private static boolean hasGold(Component c) {
        if (NamedTextColor.GOLD.equals(c.color())) {
            return true;
        }
        return c.children().stream().anyMatch(DisplayTextTest::hasGold);
    }
}
