package com.arspaper.source;

import com.arspaper.source.sourcelink.SourceYield;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「注ぎ切れなかった分をバッファへ戻す」規約を、<b>5実装すべてぶん</b>固定する。
 *
 * <p>実バグ(2026-08-01): 隣接ジャーが満杯のとき残量を<b>全額</b>バッファへ戻す実装にしたため、
 * バイタリックだけが持つ<b>受動生成</b>({@code SOURCE_PER_TICK = 5}、バッファ由来ではない)まで
 * 毎周期バッファへ積み上がるようになっていた。既定100tick周期なら1台あたり日86,400ポイントが
 * 無から湧き、{@code buffer-cap} の既定が int上限なので実質無制限。
 * 「ジャーを外して放置 → 後から上位ジャーを付けて一気に回収」で<b>保管容量の制約が消える</b>。
 *
 * <p>復元する規約はただ1つ: <b>戻せるのはその周期にバッファから引いた分だけ</b>。
 * 受動生成分は注げなければ捨てる(＝ジャーの容量が上限として働く元設計)。
 *
 * <p>このフォークは Bukkit ランタイム / MockBukkit / Mockito を持たない
 * ({@code SourceAutoConsumeTest} の javadoc 参照)ので、{@code Block} を要する
 * {@code generateSource} 自体は動かせない。ここでは各実装が返す<b>内訳の形</b>を列挙して
 * 純関数 {@link SourceYield#refundToBuffer} を検証し、
 * 「その形で実際に実装されていること」は {@link SourcelinkYieldWiringTest} が担保する。
 */
class SourceYieldRefundTest {

    /** バイタリックの受動生成(VitalicSourcelink.SOURCE_PER_TICK)。 */
    private static final int VITALIC_PASSIVE = 5;

    // ---- 5実装ぶんの規約 ----

    @ParameterizedTest(name = "{0}: バッファ{1} + 受動{2} を全く注げないとき、戻るのは{3}")
    @CsvSource({
            // 実装名, fromBuffer, passive, 期待する返却量
            "Alchemical, 120,  0, 120",
            "Botanical,   80,  0,  80",
            "Mycelial,    50,  0,  50",
            "Volcanic,   200,  0, 200",
            // バイタリックだけ受動生成を持つ。ジャーが無ければ受動分5は捨てる。
            "Vitalic,     45,  5,  45",
            // 燃料も討伐ボーナスも無いバイタリックは、受動5だけが生成される → 全部捨てる
            "Vitalic,      0,  5,   0"
    })
    @DisplayName("ジャーへ1も注げなかった周期: 戻るのはバッファ由来の分だけ(受動生成は捨てる)")
    void refundIsLimitedToTheBufferDerivedPart(String impl, int fromBuffer, int passive, int expected) {
        SourceYield yield = SourceYield.of(fromBuffer, passive);
        int leftover = yield.total(); // 1も注げなかった
        assertEquals(expected, SourceYield.refundToBuffer(yield, leftover), impl);
    }

    @ParameterizedTest(name = "{0}: バッファ{1} + 受動{2}")
    @CsvSource({
            "Alchemical, 120,  0",
            "Botanical,   80,  0",
            "Mycelial,    50,  0",
            "Volcanic,   200,  0",
            "Vitalic,     45,  5",
            "Vitalic,      0,  5"
    })
    @DisplayName("どの実装でも、1周期を通してバッファが増えることは絶対にない")
    void bufferNeverGrowsForAnyImplementation(String impl, int fromBuffer, int passive) {
        SourceYield yield = SourceYield.of(fromBuffer, passive);
        int before = 1_000; // 周期開始時のバッファ(drainBuffer 前)
        for (int leftover = 0; leftover <= yield.total(); leftover++) {
            int after = before - fromBuffer + SourceYield.refundToBuffer(yield, leftover);
            assertTrue(after <= before,
                    impl + ": leftover=" + leftover + " でバッファが " + before + " → " + after + " と増えた");
            assertTrue(after >= before - fromBuffer,
                    impl + ": leftover=" + leftover + " で引いた以上に減っている");
        }
    }

    // ---- 端の挙動 ----

    @Test
    @DisplayName("全部注げた周期は何も戻さない")
    void nothingIsRefundedWhenEverythingWasDelivered() {
        assertEquals(0, SourceYield.refundToBuffer(SourceYield.of(45, 5), 0));
        assertEquals(0, SourceYield.refundToBuffer(SourceYield.ofBuffer(50), 0));
    }

    @Test
    @DisplayName("一部だけ注げた周期は、まず受動生成が使われたとみなす(焼べた分を最大限保全)")
    void partialDeliveryConsumesThePassivePartFirst() {
        SourceYield yield = SourceYield.of(45, 5); // 合計50
        // 47注げた → 残3。受動5が先に注がれた扱いなので、残3はバッファ由来 → 3戻す。
        assertEquals(3, SourceYield.refundToBuffer(yield, 3));
        // 5しか残らなかった → 5戻す(バッファ45の範囲内)
        assertEquals(5, SourceYield.refundToBuffer(yield, 5));
        // 全く注げなかった(残50) → バッファ由来の45だけ戻す
        assertEquals(45, SourceYield.refundToBuffer(yield, 50));
    }

    @Test
    @DisplayName("不正な leftover(負値/総量超過/null)でバッファを増やさない")
    void malformedLeftoverNeverInflatesTheBuffer() {
        SourceYield yield = SourceYield.of(45, VITALIC_PASSIVE);
        assertEquals(0, SourceYield.refundToBuffer(yield, 0));
        assertEquals(0, SourceYield.refundToBuffer(yield, -100));
        // 総量を超える leftover が来ても総量以上は戻さない(=バッファ由来分が上限)
        assertEquals(45, SourceYield.refundToBuffer(yield, Integer.MAX_VALUE));
        assertEquals(0, SourceYield.refundToBuffer(null, 10));
    }

    @Test
    @DisplayName("負の内訳は0へ丸め、総量は int を飽和させる")
    void yieldNormalisesItsComponents() {
        assertEquals(0, SourceYield.of(-5, -5).total());
        assertEquals(SourceYield.NONE, SourceYield.of(-5, -5));
        assertEquals(Integer.MAX_VALUE,
                SourceYield.of(Integer.MAX_VALUE, Integer.MAX_VALUE).total());
        // 飽和しても「戻せるのはバッファ分まで」は崩れない
        assertEquals(Integer.MAX_VALUE, SourceYield.refundToBuffer(
                SourceYield.of(Integer.MAX_VALUE, VITALIC_PASSIVE), Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("受動生成だけの周期(ジャー不在のバイタリック)は永久に何も溜まらない")
    void passiveOnlyCyclesNeverAccumulate() {
        SourceYield yield = SourceYield.of(0, VITALIC_PASSIVE);
        int buffer = 0;
        for (int cycle = 0; cycle < 10_000; cycle++) {
            buffer = buffer - 0 + SourceYield.refundToBuffer(yield, yield.total());
        }
        assertEquals(0, buffer,
                "ジャーを外して放置しても受動生成は蓄積しない(修正前は1万周期で50,000溜まっていた)");
    }
}
