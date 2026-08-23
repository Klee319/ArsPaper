package com.arspaper.block.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ソースベリーの一括変換(2026-08-23 / W-189「連打が必要」)を固定する。
 *
 * <p>ここで縛るのは2点だけだが、どちらも<b>壊れてもエラーが出ない</b>種類の不具合:
 *
 * <ol>
 *   <li><b>スタックを一度に流し込む</b> —— 1個ずつに戻ると、容量 50,000,000 のジャーは
 *       500,000 回の右クリックになる(=事実上使えない)。</li>
 *   <li><b>端数は必ず切り捨てる</b> —— 残り容量が 1 個ぶん(100)に満たないのに 1 個消費すると、
 *       入り切らなかったソースが黙って消える。プレイヤーからは「ベリーが1個減っただけ」に見え、
 *       ログにも何も出ない。</li>
 * </ol>
 *
 * <p>⚠ 2026-08-24 以降、「何個入れたいか」を決めるのは
 * {@code com.arspaper.source.BulkFeed}（スニーク = 手持ち全部 / 通常 = 1個）で、
 * ここが縛るのは<b>容器側の頭打ち</b>だけ。1件目のテストの「手持ち全部」は
 * 「渡された個数をそのまま通す」の意味であって、右クリック1回の挙動ではない。
 */
class SourceJarBulkBerryConversionTest {

    @Test
    @DisplayName("残り容量が足りていれば手持ちを全部使う(1個ずつに戻っていない)")
    void theWholeHeldStackIsConsumedWhenThereIsRoom() {
        // 64 個 x 100 = 6,400 に対して残り容量 20,000 は十分。
        assertEquals(64, SourceJar.convertibleBerries(64, 20000),
                "手持ち全部を流し込めていない。1個ずつに戻ると上位ジャーは連打で埋まらない");
    }

    @Test
    @DisplayName("残り容量が足りなければ「あふれる一段階前」で止まる")
    void itStopsOneStepBeforeOverflowing() {
        // 残り 450 は 4 個(400)ぶんまで。5 個目(500)は 50 だけ入って 50 が消える。
        assertEquals(4, SourceJar.convertibleBerries(64, 450),
                "端数を切り上げている。入り切らないぶんのソースが無言で消える");
    }

    @Test
    @DisplayName("残り容量がベリー1個ぶんに満たないと0個(=消費せずに知らせる)")
    void nothingIsConsumedWhenEvenOneBerryWouldNotFit() {
        assertEquals(0, SourceJar.convertibleBerries(64, SourceJar.SOURCE_PER_BERRY - 1),
                "1個ぶんに満たない残量で消費してはいけない");
        assertEquals(0, SourceJar.convertibleBerries(64, 0));
    }

    @Test
    @DisplayName("ちょうど割り切れる残量は使い切って満タンになる")
    void anExactMultipleFillsTheJarCompletely() {
        assertEquals(3, SourceJar.convertibleBerries(64, SourceJar.SOURCE_PER_BERRY * 3),
                "割り切れる残量まで切り捨ててしまうと、満タンに到達できないジャーができる");
    }

    @Test
    @DisplayName("空スタック/負の残量でも0を返す(例外にしない)")
    void degenerateInputsYieldZero() {
        assertEquals(0, SourceJar.convertibleBerries(0, 20000));
        assertEquals(0, SourceJar.convertibleBerries(64, -1));
    }
}
