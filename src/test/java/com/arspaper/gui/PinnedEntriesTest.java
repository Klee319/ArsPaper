package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ピン止め(お気に入り)の符号化とトグルを固定する
 * （2026-08-23 ユーザー要望「レシピのピン止めをできるようにし、お気に入りだけに絞り込みたい」）。
 *
 * <p>PDC 入出力({@code load}/{@code save})はこのフォークのテスト基盤では動かせない
 * （MockBukkit が無く {@code Player} を作れない）。そのため保存形式とトグル規則を
 * 純粋関数として切り出し、ここで固定している。
 */
class PinnedEntriesTest {

    @Test
    @DisplayName("符号化→復号で往復し、順序も保たれる")
    void encodeDecodeRoundTrip() {
        Set<String> pins = new LinkedHashSet<>(List.of("catalog_sword", "stone_3x", "ritual_dawn"));

        Set<String> restored = PinnedEntries.decode(PinnedEntries.encode(pins));

        assertIterableEquals(pins, restored);
    }

    @Test
    @DisplayName("壊れた保存値でも例外を投げず空集合になる(一覧GUIが開かなくなるのを防ぐ)")
    void brokenPayloadFallsBackToEmpty() {
        for (String broken : new String[] {null, "", "   ", "{\"a\":1}", "[", "not json", "42"}) {
            assertTrue(PinnedEntries.decode(broken).isEmpty(), "壊れた値: " + broken);
        }
    }

    @Test
    @DisplayName("配列内の空文字・非文字列は捨てる")
    void junkElementsAreDropped() {
        Set<String> restored = PinnedEntries.decode("[\"a\", \"\", null, {\"x\":1}, \"b\"]");

        assertIterableEquals(List.of("a", "b"), restored);
    }

    @Test
    @DisplayName("トグルは引数を変更せず、追加と解除を往復する")
    void toggleIsPureAndReversible() {
        Set<String> original = new LinkedHashSet<>(List.of("keep"));

        Set<String> added = PinnedEntries.toggled(original, "new");
        assertIterableEquals(List.of("keep"), original, "引数の集合を書き換えてはいけない");
        assertIterableEquals(List.of("keep", "new"), added);

        Set<String> removed = PinnedEntries.toggled(added, "new");
        assertIterableEquals(List.of("keep"), removed);
    }

    @Test
    @DisplayName("上限に達したら追加できないが、解除は常にできる")
    void capBlocksAddsButNeverRemovals() {
        Set<String> full = new LinkedHashSet<>();
        for (int i = 0; i < PinnedEntries.MAX_PINS; i++) {
            full.add("id" + i);
        }

        assertTrue(PinnedEntries.wouldExceedCap(full, "one_more"));
        assertEquals(PinnedEntries.MAX_PINS, PinnedEntries.toggled(full, "one_more").size(),
                "上限を超えて増えてはいけない(PDC はプレイヤーデータ同期に丸ごと乗る)");

        assertFalse(PinnedEntries.wouldExceedCap(full, "id0"),
                "既にピン済みの id は解除なので上限に関係なく押せること");
        assertEquals(PinnedEntries.MAX_PINS - 1, PinnedEntries.toggled(full, "id0").size());
    }

    @Test
    @DisplayName("null/空の id はトグルしても何も起きない")
    void blankIdsAreIgnored() {
        Set<String> pins = new LinkedHashSet<>(List.of("a"));

        assertIterableEquals(pins, PinnedEntries.toggled(pins, null));
        assertIterableEquals(pins, PinnedEntries.toggled(pins, "  "));
        assertFalse(PinnedEntries.wouldExceedCap(pins, null));
    }
}
