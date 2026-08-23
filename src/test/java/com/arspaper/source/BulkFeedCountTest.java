package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「くべる」操作で1回の右クリックが消費する個数の共通規約（2026-08-24 要望）。
 *
 * <p>要望は「ソースリンクにアイテムをくべる時にソースベリーと同様に一括でくべられるように」
 * → その後「全部スニークで一括に揃えよう」。ソースリンク（燃料）とソースジャー（ソースベリー）の
 * <b>両方</b>が {@link BulkFeed} を通る。<b>スニーク = 手持ち全部 / 通常 = 1個</b>。
 *
 * <p>1個だけ入れる手段を残すのが要点。上位ソースリンクの燃料は1個あたりの価値が桁違い
 * （{@code custom:source_engine} = 3,000万）で、逃げ道が無いと握ったまま右クリックした瞬間に
 * スタック全部が消える。
 */
class BulkFeedCountTest {

    @Test
    @DisplayName("スニーク中は手持ちスタックを全部くべる")
    void sneakFeedsWholeStack() {
        assertEquals(64, BulkFeed.count(64, true));
        assertEquals(7, BulkFeed.count(7, true));
        assertEquals(1, BulkFeed.count(1, true));
    }

    @Test
    @DisplayName("通常の右クリックは1個だけ(高価な燃料の逃げ道)")
    void normalClickFeedsExactlyOne() {
        assertEquals(1, BulkFeed.count(64, false));
        assertEquals(1, BulkFeed.count(1, false));
    }

    @Test
    @DisplayName("手ぶら/負値は0個。消費も加算も起こさせない")
    void emptyHandFeedsNothing() {
        assertEquals(0, BulkFeed.count(0, true));
        assertEquals(0, BulkFeed.count(0, false));
        assertEquals(0, BulkFeed.count(-3, true));
    }

    @Test
    @DisplayName("返す個数は手持ちを絶対に超えない(超えると複製になる)")
    void neverExceedsHandAmount() {
        for (int hand = 1; hand <= 64; hand++) {
            assertEquals(hand, BulkFeed.count(hand, true));
            assertEquals(1, BulkFeed.count(hand, false));
        }
    }
}
