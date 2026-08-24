package com.arspaper.source;

/**
 * ソースリンクのバッファから1周期に排出してよい量を決める純関数(2026-08-24)。
 *
 * <p>Bukkit に依存しない形で切り出してある。理由は
 * {@link SourcePathVisualPolicy} と同じで、<b>実サーバでしか気づけない失敗</b>が
 * ここに集まっているため:
 * <ul>
 *   <li><b>定額だけだと「1点あたり何秒」が燃料の価値に関係なく固定される。</b>
 *       既定(50 ÷ 100tick)なら 0.1 秒/点。階梯の {@code transfer-multiplier} と
 *       {@code yield-multiplier} は同じ倍率で伸びるので、
 *       <b>上位ソースリンクにしてもこの比率は永久に改善しない</b>。
 *       結果、圧縮薪1個(2,187)で約3分40秒、ソース機関1個(3,000万)で約35日という
 *       「生成量に見合わない転送速度」になっていた。</li>
 *   <li><b>割合だけだと尻尾が終わらない。</b> 残り9点に10%を掛けると1点未満になる。
 *       切り上げても最後は1点ずつになるので、定額を下限として併用する。</li>
 *   <li><b>int の掛け算で溢れさせない。</b> バッファは int 上限(約21億)まで載るので、
 *       {@code buffer * ratio} は必ず double/long を経由する。</li>
 * </ul>
 */
public final class SourceDrainPolicy {

    private SourceDrainPolicy() {
    }

    /**
     * 1周期に排出してよい量を返す。<b>定額と割合の大きい方</b>。
     *
     * @param flat   定額分({@code max-per-transfer} × 階梯倍率 × コア倍率)。1未満は1として扱う
     * @param buffer 現在のバッファ量
     * @param ratio  {@code drain-ratio}(0.0〜1.0)。0以下なら割合分を使わない
     * @return 1以上 {@link Integer#MAX_VALUE} 以下。呼び出し側で {@code min(buffer, ...)} すること
     */
    public static int allowance(int flat, int buffer, double ratio) {
        long floor = Math.max(1L, (long) flat);
        if (buffer <= 0 || !Double.isFinite(ratio) || ratio <= 0.0) {
            return (int) Math.min((long) Integer.MAX_VALUE, floor);
        }
        // 切り上げるので、割合が極小でも「1点も動かない」で止まることはない。
        long proportional = (long) Math.ceil((double) buffer * Math.min(1.0, ratio));
        return (int) Math.min((long) Integer.MAX_VALUE, Math.max(floor, proportional));
    }
}
