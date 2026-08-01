package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 経路パーティクルの粒子数・向き表示・可視範囲の判断を固定する。
 */
class SourcePathVisualPolicyTest {

    @Test
    @DisplayName("粒子数は決して0にならない（0は非表示ではなく別機能に化ける）")
    void dotCountIsNeverZero() {
        // count=0 を渡すと spawnParticle は offset をベクトル(向き/速度)として解釈する
        // 別機能になる。距離0・間隔が極端でも1個は出す。
        assertEquals(1, SourcePathVisualPolicy.dotCount(0.0, 0.5));
        assertEquals(1, SourcePathVisualPolicy.dotCount(0.3, 0.5));
        assertEquals(1, SourcePathVisualPolicy.dotCount(Double.NaN, 0.5));
        assertEquals(1, SourcePathVisualPolicy.dotCount(10.0, 0.0));
        assertEquals(1, SourcePathVisualPolicy.dotCount(10.0, -1.0));
    }

    @Test
    @DisplayName("粒子数は距離÷間隔で、上限でハードキャップされる")
    void dotCountFollowsSpacingUntilCapped() {
        assertEquals(60, SourcePathVisualPolicy.dotCount(30.0, 0.5));
        assertEquals(20, SourcePathVisualPolicy.dotCount(10.0, 0.5));
        // 距離256 ÷ 間隔0.1 = 2560 だが、メインスレッドを守るため上限で止める
        assertEquals(SourcePathVisualPolicy.MAX_DOTS_PER_PATH,
                SourcePathVisualPolicy.dotCount(256.0, 0.1));
    }

    @Test
    @DisplayName("明色は stride 間隔で並び、phase を進めると送信先へ流れる")
    void flowDotsAdvanceWithPhase() {
        assertTrue(SourcePathVisualPolicy.isFlowDot(0, 0, 4));
        assertFalse(SourcePathVisualPolicy.isFlowDot(1, 0, 4));
        assertTrue(SourcePathVisualPolicy.isFlowDot(4, 0, 4));
        // phase=1 で明色が1つ先へずれる
        assertFalse(SourcePathVisualPolicy.isFlowDot(0, 1, 4));
        assertTrue(SourcePathVisualPolicy.isFlowDot(1, 1, 4));
        // 負の剰余で穴が空かないこと(floorMod)
        assertTrue(SourcePathVisualPolicy.isFlowDot(0, 4, 4));
        assertTrue(SourcePathVisualPolicy.isFlowDot(0, 8, 4));
    }

    @Test
    @DisplayName("stride が1以下なら全粒子が明色（0除算/全消灯にしない）")
    void strideOfOneLightsEveryDot() {
        assertTrue(SourcePathVisualPolicy.isFlowDot(3, 0, 1));
        assertTrue(SourcePathVisualPolicy.isFlowDot(3, 0, 0));
    }

    @Test
    @DisplayName("端点のどちらかが視界内なら描画対象")
    void withinViewAcceptsEitherEndpoint() {
        // view-distance=48 → 48^2 = 2304
        assertTrue(SourcePathVisualPolicy.withinView(100.0, 100000.0, 48));
        assertTrue(SourcePathVisualPolicy.withinView(100000.0, 100.0, 48));
        assertTrue(SourcePathVisualPolicy.withinView(2304.0, 100000.0, 48));
        assertFalse(SourcePathVisualPolicy.withinView(2305.0, 2305.0, 48));
    }
}
