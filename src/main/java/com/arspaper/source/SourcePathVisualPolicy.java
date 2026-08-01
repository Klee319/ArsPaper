package com.arspaper.source;

/**
 * ドミニオンワンドの経路可視化のうち、Bukkitに依存しない判断だけを切り出した純関数群。
 *
 * <p>ここを純関数にしてある理由は、パーティクル描画の負荷と「消える」事故が
 * 実サーバでしか再現しないため。特に:
 * <ul>
 *   <li><b>粒子数0は「表示しない」ではない</b> — {@code spawnParticle(count=0)} は
 *       offset をベクトル(向き/速度)として解釈する別機能に化ける。したがって
 *       {@link #dotCount} は常に1以上を返す。</li>
 *   <li>1経路あたりの粒子数は距離÷間隔で線形に増える。上限を設けないと
 *       長距離リンクを多数張ったときにメインスレッドが詰まる。</li>
 * </ul>
 */
public final class SourcePathVisualPolicy {

    /** 1経路あたりの粒子数の上限(距離256 ÷ 間隔0.1 = 2560 まで出させない)。 */
    public static final int MAX_DOTS_PER_PATH = 128;

    private SourcePathVisualPolicy() {
    }

    /**
     * 経路上に置く粒子の<b>実個数</b>を返す。
     *
     * <p>⚠ 2026-08-01 修正: 呼び出し側が {@code for (i = 0; i <= dots; i++)} と書いていたため、
     * 「上限128」と書いてあるのに実際には<b>129個</b>出ていた(オフバイワン)。
     * この関数の戻り値は「そのまま回す個数」であり、呼び出し側は
     * {@code for (i = 0; i < dotCount; i++)} と {@link #dotRatio} を使うこと。
     *
     * @param distance 経路の長さ(ブロック)
     * @param spacing  粒子の間隔(ブロック、正の値)
     * @return 1以上 {@link #MAX_DOTS_PER_PATH} 以下
     */
    public static int dotCount(double distance, double spacing) {
        if (!Double.isFinite(distance) || !Double.isFinite(spacing) || spacing <= 0.0 || distance <= 0.0) {
            return 1;
        }
        // 端点を両方描くので「区間数 + 1」が実個数。上限は実個数側に掛ける。
        long dots = (long) Math.floor(distance / spacing) + 1L;
        return (int) Math.max(1L, Math.min(MAX_DOTS_PER_PATH, dots));
    }

    /**
     * {@code index} 番目の粒子を始点(0.0)〜終点(1.0)のどこに置くかを返す。
     * {@code dotCount} が1のときは始点に置く(0除算にしない)。
     */
    public static double dotRatio(int index, int dotCount) {
        if (dotCount <= 1) {
            return 0.0;
        }
        int clamped = Math.max(0, Math.min(dotCount - 1, index));
        return (double) clamped / (double) (dotCount - 1);
    }

    /**
     * i番目の粒子を「流れ」の明色にするか。{@code phase} を描画のたびに増やすことで
     * 明色が送信元→送信先へ移動し、経路の向きが目視できる。
     */
    public static boolean isFlowDot(int index, int phase, int stride) {
        if (stride <= 1) {
            return true;
        }
        return Math.floorMod(index - phase, stride) == 0;
    }

    /** 端点どちらかがプレイヤーの視界内(半径 {@code viewDistance})に入っているか。 */
    public static boolean withinView(double distanceToFromSq, double distanceToToSq, int viewDistance) {
        double limitSq = (double) viewDistance * viewDistance;
        return distanceToFromSq <= limitSq || distanceToToSq <= limitSq;
    }
}
