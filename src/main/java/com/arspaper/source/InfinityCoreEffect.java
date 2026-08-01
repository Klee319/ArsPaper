package com.arspaper.source;

/**
 * 設置された {@code infinity_source_core} が周囲のソースリンクへ乗せる補正の純粋関数群。
 *
 * <p>Bukkit 型に一切依存しない(座標は呼び出し側で {@code double} に分解して渡す)ため、
 * MockBukkit/Mockito 無しで直接ユニットテストできる。半径判定・実際の位置追跡・
 * イベント配線は {@link InfinityCoreTracker} と {@code Sourcelink} 側が担う。
 */
public final class InfinityCoreEffect {

    private InfinityCoreEffect() {}

    /**
     * 中心(コア)からの相対座標 {@code (dx, dy, dz)} が半径 {@code radius} 以内かどうか。
     * マルチブロックのパターン判定はせず、球状の距離判定だけで成立させる
     * (2026-08-01 確定仕様 柱6)。
     *
     * @param radius 0以下は「常にfalse」(補正を無効化する設定値として扱う)。
     */
    public static boolean withinRadius(double dx, double dy, double dz, int radius) {
        if (radius <= 0) {
            return false;
        }
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        double radiusSquared = (double) radius * (double) radius;
        return distanceSquared <= radiusSquared;
    }

    /**
     * {@code base} へ {@code multiplier} を掛けた結果を、int オーバーフローせずに求める。
     *
     * <p>{@code base} は既に {@code Integer.MAX_VALUE} 近辺(例: buffer-cap の既定値)であり得るため、
     * {@code long} で計算してから {@code [1, Integer.MAX_VALUE]} にクランプする。
     * {@code multiplier} が 0 以下・非有限(NaN/Infinity)の場合は補正なし({@code base} そのまま)を返す。
     *
     * <p>⚠ この関数はオーバーフローしない値を作るだけで、「buffer-cap を超えて注げない」保証は
     * 呼び出し側が {@link SourceTransferConfig#clampBuffer} のような既存の上限クランプへ
     * この戻り値を <b>cap として渡すこと</b>で初めて成立する(K-16 で塞いだ穴の再発防止)。
     */
    public static int scaleCap(int base, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            return base;
        }
        long scaled = Math.round(base * multiplier);
        if (scaled < 1L) {
            return base;
        }
        return (int) Math.min(scaled, Integer.MAX_VALUE);
    }
}
