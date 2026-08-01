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
     * 経路上に置く粒子の個数を返す。
     *
     * @param distance 経路の長さ(ブロック)
     * @param spacing  粒子の間隔(ブロック、正の値)
     * @return 1以上 {@link #MAX_DOTS_PER_PATH} 以下
     */
    public static int dotCount(double distance, double spacing) {
        if (!Double.isFinite(distance) || !Double.isFinite(spacing) || spacing <= 0.0 || distance <= 0.0) {
            return 1;
        }
        int dots = (int) Math.floor(distance / spacing);
        return Math.max(1, Math.min(MAX_DOTS_PER_PATH, dots));
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
