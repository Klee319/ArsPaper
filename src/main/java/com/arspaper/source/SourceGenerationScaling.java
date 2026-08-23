package com.arspaper.source;

/**
 * ソースリンクの<b>生成量</b>({@code items.<id>.yield-multiplier})を掛ける純粋関数(2026-08-03)。
 *
 * <p>要件は「階梯が上がると転送速度だけでなく<b>供給量</b>も上がる」。転送速度側は既に
 * {@code items.<id>.transfer-multiplier} → {@code Sourcelink#effectiveMaxPerTransfer} で効いていたが、
 * 上位ソースリンクへ燃料を1個焼べても得られるソースは無印と同じだったため、
 * 「階梯を上げても素材効率は変わらない(並べた台数だけが効く)」状態だった。
 *
 * <p>⚠ <b>なぜ {@code Sourcelink#addToBuffer} の中で掛けないのか</b> —— {@code addToBuffer} は
 * 生成経路だけでなく<b>返却経路</b>からも呼ばれる({@code SourcelinkTickTask#tick} が
 * {@code SourceYield#refundToBuffer} で「注ぎ切れずに戻す分」を入れ直す)。
 * ここで倍率を掛けると<b>隣接ジャーが満杯である限り毎周期ソースが増える無限増殖</b>になる。
 * したがって倍率は「本当に新しく生まれた量」を作る側 —— 燃料投入・受動生成・成長/撃破ボーナス
 * —— でだけ掛ける。
 */
public final class SourceGenerationScaling {

    private SourceGenerationScaling() {}

    /**
     * 生成量 {@code rawAmount} に {@code multiplier} を掛け、int の範囲へ収めて返す。
     *
     * <p>{@code rawAmount} を {@code long} で受けるのは、燃料の単価が最大 3000万
     * ({@code custom:source_engine})で64個同時投入が 19.2億 —— そこへ倍率を掛けると
     * int を越えるため。2026-08-24 に一括投入が<b>通常の右クリック</b>側へ移った
     * ({@code Sourcelink#feedCount})ので、この桁は例外ケースではなく既定の経路になった。上限に当たった分は {@code addToBuffer} 側の {@code buffer-cap} クランプで
     * 警告付きに捨てられる(この関数はオーバーフローで負値に化けるのを防ぐだけ)。
     *
     * @param rawAmount 倍率を掛ける前の生成量。0以下は 0(生成なし)。
     * @param multiplier 非有限値・0以下は「補正なし」として扱う(設定ミスで生成が止まらないように)。
     * @return {@code [1, Integer.MAX_VALUE]} に収めた生成量。{@code rawAmount} が正なら 0 は返さない
     *         (倍率1未満の弱体設定でも「無言で生成が死ぬ」ことを防ぐ)。
     */
    public static int scaleYield(long rawAmount, double multiplier) {
        if (rawAmount <= 0L) {
            return 0;
        }
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            return (int) Math.min(rawAmount, Integer.MAX_VALUE);
        }
        long scaled = Math.round(rawAmount * multiplier);
        if (scaled < 1L) {
            return 1;
        }
        return (int) Math.min(scaled, Integer.MAX_VALUE);
    }
}
