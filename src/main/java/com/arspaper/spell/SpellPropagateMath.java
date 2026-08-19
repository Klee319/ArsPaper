package com.arspaper.spell;

/**
 * 伝播（propagate）チェーンの純粋な算術。Bukkit に触れないのでそのままテストできる。
 *
 * <p>{@link SpellContext#schedulePropagateChain} から切り出してある。式を書き換えると
 * {@code PropagateChainMathTest} が落ちる ── 「サーバで確かめる」以外に検証手段が無い状態を避けるため。
 */
public final class SpellPropagateMath {

    private SpellPropagateMath() {
    }

    /**
     * この起点から実際に張ってよいチェーン数を返す。
     *
     * <p><b>上限が要る理由</b>: 伝播は「この詠唱でまだ当てていない敵」を探し直す方式なので、
     * 密集地（TT・スポナー前）では候補が尽きるまで外へ伸びる。起点ごとに近傍検索を掛けるため、
     * 上限が無いと 対象数 × 検索回数 が跳ね上がってサーバが持っていかれる。
     *
     * @param requested   増強から決まる希望チェーン数（{@code targets-per-stack} × 個数）
     * @param maxPerCast  詠唱1回あたりの総チェーン数上限（{@code max-chains-per-cast}）
     * @param used        この詠唱で既に消費したチェーン数
     * @return 0 以上。上限に達していれば 0
     */
    public static int budget(int requested, int maxPerCast, int used) {
        if (requested <= 0) return 0;
        int remaining = maxPerCast - used;
        if (remaining <= 0) return 0;
        return Math.min(requested, remaining);
    }

    /**
     * {@code hopIndex} 番目（0 始まり = 起点から一番近い相手）に掛ける威力倍率。
     *
     * <p>1 ホップごとに {@code falloffPerHop} ずつ引き、{@code minRate} で下げ止まる。
     * この倍率は<b>最終ダメージ</b>に掛ける ── TF の守備力は引き算で効くので、素の威力側に掛けると
     * 0.4 倍のつもりが {@code min-component-damage} まで落ちて、表示上の減衰率と実ダメージが桁で食い違う。
     *
     * @param hopIndex     0 始まりのホップ番号
     * @param falloffPerHop 1 ホップあたりの減衰量（0.15 なら 85% / 70% / 55% ...）
     * @param minRate      下げ止まりの倍率
     */
    public static double hopRate(int hopIndex, double falloffPerHop, double minRate) {
        double rate = 1.0 - falloffPerHop * (hopIndex + 1);
        return Math.max(minRate, rate);
    }
}
