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
    @DisplayName("粒子数は「距離÷間隔 + 1」(端点を両方描く)で、上限でハードキャップされる")
    void dotCountFollowsSpacingUntilCapped() {
        // 2026-08-01: 戻り値は「実際に回す個数」。呼び出し側が i <= dots で回していたため
        // 「上限128」と書いてあるのに129個出ていた(オフバイワン)。個数側に上限を掛ける。
        assertEquals(61, SourcePathVisualPolicy.dotCount(30.0, 0.5));
        assertEquals(21, SourcePathVisualPolicy.dotCount(10.0, 0.5));
        assertEquals(31, SourcePathVisualPolicy.dotCount(30.0, 1.0));
        // 距離256 ÷ 間隔0.1 + 1 = 2561 だが、メインスレッドを守るため上限で止める
        assertEquals(SourcePathVisualPolicy.MAX_DOTS_PER_PATH,
                SourcePathVisualPolicy.dotCount(256.0, 0.1));
        // 上限ちょうどを跨いでも128を超えない(129個出ていたのが実バグ)
        assertEquals(SourcePathVisualPolicy.MAX_DOTS_PER_PATH,
                SourcePathVisualPolicy.dotCount(127.0, 1.0));
        assertEquals(SourcePathVisualPolicy.MAX_DOTS_PER_PATH,
                SourcePathVisualPolicy.dotCount(1000.0, 1.0));
    }

    @Test
    @DisplayName("粒子の位置は始点0.0〜終点1.0を dotCount 個で割る(0除算しない)")
    void dotRatioSpansBothEndpoints() {
        assertEquals(0.0, SourcePathVisualPolicy.dotRatio(0, 5), 1e-9);
        assertEquals(0.25, SourcePathVisualPolicy.dotRatio(1, 5), 1e-9);
        assertEquals(1.0, SourcePathVisualPolicy.dotRatio(4, 5), 1e-9);
        // 1個しか出ない経路(距離0など)は始点へ置く。dots-1 = 0 で割らないこと。
        assertEquals(0.0, SourcePathVisualPolicy.dotRatio(0, 1), 1e-9);
        assertEquals(0.0, SourcePathVisualPolicy.dotRatio(3, 1), 1e-9);
        // 範囲外の index でも [0,1] を出ない
        assertEquals(1.0, SourcePathVisualPolicy.dotRatio(99, 5), 1e-9);
        assertEquals(0.0, SourcePathVisualPolicy.dotRatio(-3, 5), 1e-9);
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
