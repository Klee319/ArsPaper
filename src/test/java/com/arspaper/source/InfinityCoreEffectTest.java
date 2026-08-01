package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code infinity_source_core} の半径判定・倍率適用・クランプを固定する
 * (2026-08-01 確定仕様 柱6)。
 *
 * <p>{@link InfinityCoreEffect} は Bukkit 型に依存しない純粋関数なので、
 * MockBukkit/Mockito 無しでも直接テストできる ―― この点がこのフォークのテスト制約
 * (Mockito/MockBukkit 無し)への対応そのものになっている。
 */
class InfinityCoreEffectTest {

    // ---- withinRadius: マルチブロックのパターン判定はせず、半径だけで成立させる ----

    @Test
    @DisplayName("原点(コアそのものの位置)は半径0でもfalse(0=無効化)")
    void radiusZeroDisablesBoost() {
        assertFalse(InfinityCoreEffect.withinRadius(0, 0, 0, 0));
    }

    @Test
    @DisplayName("半径ちょうどの距離は内側として扱う(境界は含む)")
    void boundaryDistanceIsInside() {
        assertTrue(InfinityCoreEffect.withinRadius(5, 0, 0, 5));
        assertTrue(InfinityCoreEffect.withinRadius(3, 4, 0, 5)); // 3-4-5
    }

    @Test
    @DisplayName("半径を1でも超えると外側")
    void justOutsideRadiusIsOutside() {
        assertFalse(InfinityCoreEffect.withinRadius(5.0001, 0, 0, 5));
    }

    @Test
    @DisplayName("マルチブロックのパターンは見ない ―― 3軸合成の距離だけで判定する")
    void onlyEuclideanDistanceMatters() {
        // 同じ直線距離(5)でも軸の内訳が違う3パターンすべて内側になる
        assertTrue(InfinityCoreEffect.withinRadius(5, 0, 0, 5));
        assertTrue(InfinityCoreEffect.withinRadius(0, 5, 0, 5));
        assertTrue(InfinityCoreEffect.withinRadius(0, 0, 5, 5));
    }

    @Test
    @DisplayName("負の相対座標も距離の2乗で吸収される")
    void negativeOffsetsAreSymmetric() {
        assertTrue(InfinityCoreEffect.withinRadius(-3, -4, 0, 5));
        assertFalse(InfinityCoreEffect.withinRadius(-6, 0, 0, 5));
    }

    // ---- scaleCap: buffer-cap / max-per-transfer への倍率適用 ----

    @Test
    @DisplayName("既定倍率2.0を既定max-per-transfer(50)へ適用すると単純に2倍")
    void defaultTransferMultiplierDoublesBase() {
        assertEquals(100, InfinityCoreEffect.scaleCap(50, 2.0));
    }

    @Test
    @DisplayName("buffer-cap が既に Integer.MAX_VALUE のとき倍率を掛けてもオーバーフローしない")
    void scaleCapNeverOverflowsAtIntMax() {
        // 実バグの再発防止: (long) を経由せず int のまま掛けると負値に化ける値を敢えて使う。
        assertEquals(Integer.MAX_VALUE,
                InfinityCoreEffect.scaleCap(Integer.MAX_VALUE, 2.0));
        assertEquals(Integer.MAX_VALUE,
                InfinityCoreEffect.scaleCap(Integer.MAX_VALUE / 2 + 1000, 2.0));
    }

    @Test
    @DisplayName("倍率0以下・非有限は補正なし(base素通し)として扱う")
    void nonPositiveOrNonFiniteMultiplierIsNoOp() {
        assertEquals(50, InfinityCoreEffect.scaleCap(50, 0.0));
        assertEquals(50, InfinityCoreEffect.scaleCap(50, -1.0));
        assertEquals(50, InfinityCoreEffect.scaleCap(50, Double.NaN));
        assertEquals(50, InfinityCoreEffect.scaleCap(50, Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("倍率1.0未満(ナーフ方向)も安全に丸め込める")
    void fractionalMultiplierRoundsSafely() {
        assertEquals(25, InfinityCoreEffect.scaleCap(50, 0.5));
        assertEquals(1, InfinityCoreEffect.scaleCap(1, 0.5));
    }

    @Test
    @DisplayName("scaleCap の戻り値はさらに SourceTransferConfig.clampBuffer(既存の上限クランプ)を通せる")
    void scaledCapFeedsIntoExistingOverflowSafeClamp() {
        // Sourcelink#addToBuffer と同じ経路: scaleCap で作った cap を clampBuffer の cap 引数に渡す。
        int base = SourceTransferConfig.DEFAULT_SOURCELINK_BUFFER_CAP; // Integer.MAX_VALUE
        int boostedCap = InfinityCoreEffect.scaleCap(base, 2.0);
        assertEquals(Integer.MAX_VALUE, boostedCap);

        // 3000万を焼べ続けても既存のオーバーフロー安全クランプで上限に張り付くだけで負値に化けない。
        int next = SourceTransferConfig.clampBuffer(Integer.MAX_VALUE - 10, 30_000_000, boostedCap);
        assertEquals(Integer.MAX_VALUE, next);
        assertTrue(next > 0, "オーバーフローして負値になってはいけない");
    }
}
