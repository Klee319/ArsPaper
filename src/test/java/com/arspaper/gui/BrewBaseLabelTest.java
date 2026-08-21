package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * レシピ画面のベースポーション名が<b>バニラ ja_jp と同じ語</b>であることを固定する
 * (2026-08-21 実サーバ報告「醸造レシピ表示で翻訳が間違っている。濃厚なポーションが濃厚な水、
 * ありふれたポーションがただの水になっている」)。
 *
 * <p>ここだけ独自訳にすると、<b>同じ瓶がレシピ画面とインベントリで別名になる</b>。
 * プレイヤーは「レシピに書いてあるアイテムが手元に無い」と読むので、
 * 作れないバグに見える ── 表示ズレの中でも特に高くつく型。
 *
 * <p>翻訳キーを直接使えない事情（効果の無いベースには
 * {@code item.minecraft.potion.effect.<名前>} の訳語が引けない）は実装側の javadoc にある。
 * だからこそ「手で書いた語がバニラと一致していること」を機械で縛る必要がある。
 *
 * <p>Bukkit ランタイムを持たないフォークなので、{@code SourcelinkYieldWiringTest} と同じ
 * ソース検査で固定する。
 */
class BrewBaseLabelTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/arspaper/gui/RecipeBrowserGui.java");

    /** バニラ ja_jp の訳語（{@code item.minecraft.potion.effect.*}）。 */
    private static final String[][] VANILLA_LABELS = {
            {"WATER", "水入り瓶"},
            {"MUNDANE", "ありふれたポーション"},
            {"THICK", "濃厚なポーション"},
            {"AWKWARD", "奇妙なポーション"},
    };

    @Test
    @DisplayName("ベースポーションの表示名はバニラ ja_jp と同じ語を使う")
    void brewBaseLabelsMatchVanillaJapaneseNames() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        for (String[] pair : VANILLA_LABELS) {
            String expected = "case \"" + pair[0] + "\" -> \"" + pair[1] + "\";";
            assertTrue(source.contains(expected),
                    pair[0] + " の表示名は「" + pair[1] + "」でなければならない"
                            + "（バニラのインベントリ表示と food/brew 画面で名前が食い違うと、"
                            + "手元にある瓶がレシピの素材と結び付かなくなる）。期待する行: "
                            + expected);
        }
    }
}
