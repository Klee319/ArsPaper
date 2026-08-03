package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 階梯の生成量倍率({@code items.<id>.yield-multiplier})を掛ける純関数(2026-08-03)。
 *
 * <p>守りたいのは3点: <b>倍率1.0で挙動が一切変わらない</b>こと、<b>int で溢れない</b>こと
 * (最高級燃料 {@code custom:source_engine} = 単価3000万をスニークで64個投入すると素で19.2億、
 * 倍率2.0を掛けると int を越える)、そして<b>正の生成量が0に化けない</b>こと
 * (0になると「上位階梯なのに何も生まれない」という気づきにくい死に方をする)。
 */
class SourceGenerationScalingTest {

    @Test
    @DisplayName("倍率1.0は素の値と完全に一致する(既存挙動の不変)")
    void multiplierOneIsBehaviourNeutral() {
        assertEquals(1, SourceGenerationScaling.scaleYield(1L, 1.0));
        assertEquals(50, SourceGenerationScaling.scaleYield(50L, 1.0));
        assertEquals(30_000_000, SourceGenerationScaling.scaleYield(30_000_000L, 1.0));
    }

    @Test
    @DisplayName("正の倍率は掛け算され、四捨五入される")
    void positiveMultipliersScale() {
        assertEquals(100, SourceGenerationScaling.scaleYield(50L, 2.0));
        assertEquals(200, SourceGenerationScaling.scaleYield(50L, 4.0));
        assertEquals(13, SourceGenerationScaling.scaleYield(5L, 2.5));   // 12.5 → 13
        assertEquals(8, SourceGenerationScaling.scaleYield(5L, 1.6));    // 8.0
    }

    @Test
    @DisplayName("生成量0以下は0(倍率をかけて生成をひねり出さない)")
    void nonPositiveAmountsStayZero() {
        assertEquals(0, SourceGenerationScaling.scaleYield(0L, 4.0));
        assertEquals(0, SourceGenerationScaling.scaleYield(-10L, 4.0));
    }

    @Test
    @DisplayName("倍率が0以下/非有限なら補正なし(生成が止まらない)")
    void brokenMultipliersFallBackToNoScaling() {
        assertEquals(50, SourceGenerationScaling.scaleYield(50L, 0.0));
        assertEquals(50, SourceGenerationScaling.scaleYield(50L, -2.0));
        assertEquals(50, SourceGenerationScaling.scaleYield(50L, Double.NaN));
        assertEquals(50, SourceGenerationScaling.scaleYield(50L, Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("弱体倍率でも正の生成量が0へ落ちない")
    void smallResultsAreFlooredAtOne() {
        assertEquals(1, SourceGenerationScaling.scaleYield(1L, 0.1));
        assertEquals(1, SourceGenerationScaling.scaleYield(2L, 0.2));   // 0.4 → 四捨五入0 → 1へ
    }

    @Test
    @DisplayName("int を越える生成量は Integer.MAX_VALUE で飽和する(負値に化けない)")
    void hugeYieldsSaturateInsteadOfOverflowing() {
        // source_engine(3000万) × 64個 = 19.2億。素でも int 上限に近い。
        assertEquals(1_920_000_000, SourceGenerationScaling.scaleYield(1_920_000_000L, 1.0));
        // そこへ階梯倍率2.0を掛けると 38.4億 = int 上限超え。
        assertEquals(Integer.MAX_VALUE, SourceGenerationScaling.scaleYield(1_920_000_000L, 2.0));
        assertEquals(Integer.MAX_VALUE, SourceGenerationScaling.scaleYield(Long.MAX_VALUE / 4, 4.0));
    }
}
