package com.arspaper.item;

/**
 * 「バニラが食事を始めない状況か」の判定だけを持つ純関数（W-258）。
 *
 * <p>Bukkit ランタイムを必要としないので、このフォークのテスト基盤（MockBukkit 無し）でも
 * そのまま検証できる。判定を {@link SourceBerryListener} の中へ書かないのは、
 * <b>境界を1tickでも取り違えると「1クリックで2個消える」か「永久に食べられない」の
 * どちらかになる</b>のに、実機で気づくのが遅いため。
 */
public final class SourceBerryConsumePolicy {

    /** バニラの満腹度の上限（{@code Player#getFoodLevel()} の最大値）。 */
    public static final int MAX_FOOD_LEVEL = 20;

    private SourceBerryConsumePolicy() {
    }

    /**
     * この満腹度のとき、こちら側で手動消費する必要があるか。
     *
     * <p>バニラが食事を始める条件は「満腹度 &lt; 20」または食品が {@code can_always_eat} を
     * 持つこと。ソースベリー（GLOW_BERRIES 素材）は後者を持たないので、
     * <b>満腹度が 20 に達している間だけ</b>バニラは何もしない。その区間だけ true を返す。
     *
     * @param foodLevel {@code Player#getFoodLevel()}。範囲外の値でも安全側（20以上なら手動）に倒す
     */
    public static boolean needsManualConsume(int foodLevel) {
        return foodLevel >= MAX_FOOD_LEVEL;
    }
}
