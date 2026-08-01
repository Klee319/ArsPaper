package com.arspaper.source.sourcelink;

/**
 * ソースリンクが1周期に供給へ回す量を、<b>バッファ由来</b>と<b>受動生成</b>に分けて表す値
 * (2026-08-01)。
 *
 * <h2>なぜ分ける必要があるのか</h2>
 * 「隣接ジャーが満杯で注ぎ切れなかった残量をバッファへ戻す」処理は、
 * <b>戻してよいのがバッファから引いた分だけ</b>である。ここを区別しないと、
 * バイタリック({@link VitalicSourcelink}) のようにバッファ由来でない受動生成
 * (1周期あたり {@code SOURCE_PER_TICK})を持つ実装で、
 * <b>ジャーが無い/満杯のまま放置するだけでバッファが無限に増える</b>。
 * 既定(100tick周期・受動5)で1台あたり日 86,400 ポイントが無から湧き、
 * 「ジャーを外して放置 → 後から上位ジャーを付けて一気に回収」という
 * <b>保管容量の制約を消す経路</b>が開いてしまう。
 *
 * <p>元設計では、隣接ジャーへ注げなかった受動生成分は<b>その場で捨てられていた</b>
 * (ジャーの容量が実質的な上限として働いていた)。この record と
 * {@link #refundToBuffer} はその上限を規約として復元するためのもので、
 * <b>5実装すべてがこの1つの規約に従う</b>(4実装は受動生成を持たないので
 * {@code passive = 0}、バイタリックだけが {@code passive > 0})。
 *
 * @param fromBuffer この周期にバッファから引いた量(注ぎ切れなければ戻してよい)
 * @param passive    バッファ由来でない受動生成量(注ぎ切れなければ<b>捨てる</b>)
 */
public record SourceYield(int fromBuffer, int passive) {

    /** 何も生成しなかった周期。 */
    public static final SourceYield NONE = new SourceYield(0, 0);

    public SourceYield {
        fromBuffer = Math.max(0, fromBuffer);
        passive = Math.max(0, passive);
    }

    /** バッファから引いた分だけの生成(受動生成を持たない4実装用)。 */
    public static SourceYield ofBuffer(int amount) {
        return new SourceYield(amount, 0);
    }

    /** バッファ由来 + 受動生成(バイタリック用)。 */
    public static SourceYield of(int fromBuffer, int passive) {
        return new SourceYield(fromBuffer, passive);
    }

    /** 供給に回す総量。int を飽和させる(オーバーフローで負値にしない)。 */
    public int total() {
        long sum = (long) fromBuffer + (long) passive;
        return (int) Math.min(sum, (long) Integer.MAX_VALUE);
    }

    /**
     * 注ぎ切れなかった残量 {@code leftover} のうち、<b>バッファへ戻してよい量</b>を返す純関数。
     *
     * <p>規約は1つだけ: <b>戻せるのはこの周期にバッファから引いた分({@link #fromBuffer})が上限</b>。
     * したがって受動生成分は、注げなければ必ず捨てられ、バッファは受動生成では決して増えない
     * ({@code buffer後 = buffer前 - fromBuffer + refund ≦ buffer前})。
     *
     * <p>残量の割り当ては<b>受動生成を先に供給した</b>とみなす(＝残ったのはバッファ分から数える)。
     * こうすると「一部だけ注げた」周期でプレイヤーが焼べた分(バッファ)が最大限保全され、
     * 捨てられるのは無から湧いた受動生成分だけになる。
     *
     * @param yield    その周期の生成内訳
     * @param leftover {@link Sourcelink#supplyAdjacent} が返した注ぎ切れなかった量
     * @return バッファへ戻す量(0以上・{@code fromBuffer} 以下)
     */
    public static int refundToBuffer(SourceYield yield, int leftover) {
        if (yield == null || leftover <= 0) {
            return 0;
        }
        int capped = Math.min(leftover, yield.total());
        return Math.min(capped, yield.fromBuffer());
    }
}
