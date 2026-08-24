package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 割合排出（{@code transfer.sourcelink.drain-ratio}）の判断を固定する。
 *
 * <p>直している実バグは「<b>生成量に対して転送が遅すぎる</b>」。定額排出だけだと
 * 1点あたりの所要時間が燃料の価値と無関係に固定され、階梯を上げても
 * 生成量倍率と転送倍率が同率で伸びるので比率が一切改善しなかった。
 */
class SourceDrainPolicyTest {

    /** 既定の周期(100tick = 5秒)。所要時間を秒で語るために使う。 */
    private static final int INTERVAL_TICKS = 100;
    /** 既定の定額(無印ソースリンクの基準値)。 */
    private static final int FLAT = 50;

    /** {@code buffer} を空にするまでの秒数。 */
    private static long secondsToDrain(long buffer, int flat, double ratio) {
        int cycles = 0;
        while (buffer > 0 && cycles < 100_000_000) {
            int visible = (int) Math.min(buffer, (long) Integer.MAX_VALUE);
            long drain = Math.min(buffer, SourceDrainPolicy.allowance(flat, visible, ratio));
            buffer -= drain;
            cycles++;
        }
        assertEquals(0L, buffer, "吐き切れずに残った(周期数の上限に当たった)");
        return (long) cycles * INTERVAL_TICKS / 20L;
    }

    @Test
    @DisplayName("割合0なら従来どおりの完全定額（挙動不変の逃げ道が残っている）")
    void ratioZeroKeepsFlatBehaviour() {
        assertEquals(FLAT, SourceDrainPolicy.allowance(FLAT, 30_000_000, 0.0));
        assertEquals(FLAT, SourceDrainPolicy.allowance(FLAT, 30_000_000, -1.0));
        assertEquals(FLAT, SourceDrainPolicy.allowance(FLAT, 30_000_000, Double.NaN));
    }

    /** 出荷の既定値。 */
    private static final double RATIO = 0.25;

    @Test
    @DisplayName("溜まっている量が多いときは割合が勝ち、少ないときは定額が勝つ")
    void takesWhicheverIsLarger() {
        assertEquals(7_500_000, SourceDrainPolicy.allowance(FLAT, 30_000_000, RATIO));
        // 100 の25%は25で定額50に負ける ＝ 尻尾が1点ずつにならない
        assertEquals(FLAT, SourceDrainPolicy.allowance(FLAT, 100, RATIO));
    }

    @Test
    @DisplayName("階梯の倍率が乗った定額より割合が小さければ、階梯側の速さが損なわれない")
    void tierRateIsNeverSlowedDownByTheRatio() {
        // 最上段(x512)の定額 = 50 x 512 = 25,600。バッファ10万の25%は2.5万なので定額が勝つ。
        int tierFlat = FLAT * 512;
        assertEquals(tierFlat, SourceDrainPolicy.allowance(tierFlat, 100_000, RATIO));
    }

    @Test
    @DisplayName("切り上げるので、割合が極小でも「1点も動かない」で止まらない")
    void neverStallsAtZero() {
        assertEquals(1, SourceDrainPolicy.allowance(1, 10, 0.0000001));
        assertTrue(SourceDrainPolicy.allowance(1, 3, 0.01) >= 1);
    }

    @Test
    @DisplayName("int 上限のバッファに倍率を掛けても溢れて負値にならない")
    void neverOverflows() {
        int allowance = SourceDrainPolicy.allowance(Integer.MAX_VALUE, Integer.MAX_VALUE, 1.0);
        assertTrue(allowance > 0, "溢れて負値になってはいけない: " + allowance);
        assertEquals(Integer.MAX_VALUE, allowance);
    }

    @Test
    @DisplayName("修正前の定額のみだと高価値燃料の回収に現実的でない時間がかかる（回帰の証拠）")
    void flatOnlyIsUnusableForHighValueFuel() {
        // ⚠ この行が「直す前の状態」。割合排出を消すとこの時間へ戻る。
        // 圧縮薪(3x3x3 = 2,187)1個 = 2,187 ÷ 50 = 44周期 = 220秒(約3分40秒)。
        assertEquals(220L, secondsToDrain(2_187L, FLAT, 0.0), "圧縮薪1個で約3分40秒");
        // ソース機関(3,000万)1個 = 600,000周期 = 3,000,000秒(約35日)。
        assertEquals(3_000_000L, secondsToDrain(30_000_000L, FLAT, 0.0),
                "ソース機関1個で約35日かかっていた");
    }

    @Test
    @DisplayName("既定の割合0.25の所要時間を表として固定する（バランスの現物）")
    void ratioBoundsDrainTimeRegardlessOfAmount() {
        // 小口は定額が勝つので<修正前と同じ>。ここが変わると溶岩バケツの体感が変わってしまう。
        assertEquals(10L, secondsToDrain(100L, FLAT, RATIO), "溶岩バケツ(100)は10秒のまま");
        assertEquals(65L, secondsToDrain(2_187L, FLAT, RATIO), "圧縮薪1個(2,187)");
        assertEquals(75L, secondsToDrain(4_500L, FLAT, RATIO), "ソースの欠片1個(4,500)");
        assertEquals(135L, secondsToDrain(140_000L, FLAT, RATIO), "圧縮薪1スタック(140,000)");
        assertEquals(230L, secondsToDrain(30_000_000L, FLAT, RATIO), "ソース機関1個(3,000万)");

        // 量が13,700倍でも所要時間は対数的にしか伸びない、が割合排出の要点。
        long small = secondsToDrain(2_187L, FLAT, RATIO);
        long huge = secondsToDrain(30_000_000L, FLAT, RATIO);
        assertTrue(huge < small * 5L,
                "量が13,700倍でも所要時間は5倍未満に収まること: " + small + "秒 → " + huge + "秒");
    }
}
