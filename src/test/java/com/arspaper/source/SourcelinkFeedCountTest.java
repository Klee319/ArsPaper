package com.arspaper.source;

import com.arspaper.source.sourcelink.Sourcelink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ソースリンクへ1回の右クリックで焼べる個数の規約（2026-08-24 要望）。
 *
 * <p>要望は「ソースリンクにアイテムをくべる時にソースベリーと同様に一括でくべられるようにしてほしい」。
 * ソースジャーへのソースベリー投入は<b>無条件で一括</b>なのに、ソースリンクは逆に
 * <b>スニークしたときだけ一括</b>で、同じ「くべる」操作の意味が食い違っていた。
 * 通常＝一括／スニーク＝1個へ入れ替える。
 *
 * <p>1個だけ焼べる手段を残すのが要点。上位ソースリンクの燃料は1個あたりの価値が桁違い
 * （{@code custom:source_engine} = 3,000万）で、逃げ道が無いと握ったまま右クリックした瞬間に
 * スタック全部が消える。
 */
class SourcelinkFeedCountTest {

    @Test
    @DisplayName("通常の右クリックは手持ちスタックを全部焼べる")
    void normalClickFeedsWholeStack() {
        assertEquals(64, Sourcelink.feedCount(64, false));
        assertEquals(7, Sourcelink.feedCount(7, false));
        assertEquals(1, Sourcelink.feedCount(1, false));
    }

    @Test
    @DisplayName("スニーク中は1個だけ焼べる(高価な燃料の逃げ道)")
    void sneakFeedsExactlyOne() {
        assertEquals(1, Sourcelink.feedCount(64, true));
        assertEquals(1, Sourcelink.feedCount(1, true));
    }

    @Test
    @DisplayName("手ぶら/負値は0個。消費も加算も起こさせない")
    void emptyHandFeedsNothing() {
        assertEquals(0, Sourcelink.feedCount(0, false));
        assertEquals(0, Sourcelink.feedCount(0, true));
        assertEquals(0, Sourcelink.feedCount(-3, false));
    }

    @Test
    @DisplayName("返す個数は手持ちを絶対に超えない(超えると複製になる)")
    void neverExceedsHandAmount() {
        for (int hand = 1; hand <= 64; hand++) {
            assertEquals(hand, Sourcelink.feedCount(hand, false));
            assertEquals(1, Sourcelink.feedCount(hand, true));
        }
    }
}
