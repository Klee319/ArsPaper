package com.arspaper.spell;

/**
 * 防御無視ダメージ（日輪/月輪の {@code setHealth} による直接HP減少）のワンショット防止（2026-07-31 F4）。
 *
 * <p><b>なぜ必要か（レビュー指摘 HIGH の真因）</b>: 防御無視ダメージは
 * {@code target.setHealth(max(0, getHealth() - damage))} で適用されるので、
 * <b>{@code EntityDamageEvent} が発火しない</b>。つまり
 * <ul>
 *   <li>TF の守備力・耐性・被ダメージ軽減・回避・{@code min-component-damage}</li>
 *   <li>不死のトーテム・吸収ハート・盾・WorldGuard 等あらゆるダメージフック</li>
 *   <li>TF の {@code PvpDamagePolicy}（対人抑制）</li>
 * </ul>
 * が<b>1つも通らない</b>。ここに攻撃力のような伸びる値が入ると、装備・トーテムに関係なく初弾で
 * 確定死亡する即死ボタンになる（実際に杖の attack-power 10584 が乗って 1発 10594 になっていた）。
 *
 * <p><b>なぜ「最大体力に対する割合」なのか</b>: TF の {@code PvpDamagePolicy} と同じ理由。
 * グリフ基礎値や増幅の上限を絶対値で決めると、体力側のインフレ／攻撃側のカーブ変更のたびに
 * 調整し直しになり、<b>調整漏れがそのまま即死に戻る</b>。「1発で最大体力の何%まで」は
 * スケールフリーなので、どちら側が伸びても「最低◯発かかる」が構造的に保証される。
 *
 * <p><b>なぜ1発の上限と1詠唱の合計上限を別に持つのか</b>: 日輪/月輪は分裂(split)で
 * 1斉射あたりの発射数が増え、さらに召喚が持続する間ずっと撃ち続ける。1発の上限だけだと
 * 「上限いっぱいの弾を連射して即死」に戻るため、<b>1詠唱（=1召喚）あたりの累計</b>にも
 * 上限を置く。既定は
 * <ul>
 *   <li>1発 = 最大体力の <b>25%</b>（＝最低4発かかる。回復/離脱/召喚体の破壊が間に合う余地を作る）</li>
 *   <li>1詠唱の累計 = 最大体力の <b>100%</b>（＝1詠唱で仕留めること自体は許すが、
 *       防御無視だけで2回殺すことはできない）</li>
 * </ul>
 * 既定値の根拠: 防御無視は「性格として硬い相手にも通る」ことに価値があるので機能自体は殺さず、
 * 「何発かかるか」だけを固定する。100% を下回らせると「防御無視では絶対に倒せない」になり
 * グリフの用途が消えるため下限は 100%、上限（1発）は 4発 = 4秒（既定 fire-interval 20tick）で
 * プレイヤーが反応できる時間を確保する値として 25% にした。
 *
 * <p>Bukkit 非依存の純粋関数だけを置く（このフォークのテスト基盤は Bukkit ランタイムを持たない）。
 */
public final class DefenseIgnoringDamagePolicy {

    /** 1発あたりの上限（対象の最大体力に対する割合）。0 以下で「1発の上限なし」。 */
    public static final double DEFAULT_MAX_PERCENT_PER_HIT = 0.25;

    /** 1詠唱（=1召喚）あたりの累計上限（対象の最大体力に対する割合）。0 以下で「累計上限なし」。 */
    public static final double DEFAULT_MAX_PERCENT_PER_CAST = 1.0;

    private DefenseIgnoringDamagePolicy() {
    }

    /**
     * 実際にHPから引いてよい防御無視ダメージ量。
     *
     * <p>{@code victimMaxHealth <= 0}（属性が読めないテストダブル等）のときは割合上限を掛けられないので
     * {@code rawDamage} をそのまま返す（{@code PvpDamagePolicy#maxHealthOf} と同じ安全側の流儀）。
     *
     * @param rawDamage            グリフ基礎＋増幅から算出した1発分のダメージ
     * @param victimMaxHealth      対象の最大体力（{@code <= 0} なら上限を掛けない）
     * @param alreadyDealtThisCast この詠唱でこの対象へ既に与えた防御無視ダメージの累計
     * @param maxPercentPerHit     1発の上限（最大体力比。0以下で上限なし）
     * @param maxPercentPerCast    1詠唱の累計上限（最大体力比。0以下で上限なし）
     * @return 適用してよい量（0以上。累計上限に達していれば 0）
     */
    public static double cappedDamage(double rawDamage, double victimMaxHealth,
                                      double alreadyDealtThisCast,
                                      double maxPercentPerHit, double maxPercentPerCast) {
        if (!Double.isFinite(rawDamage) || rawDamage <= 0.0) {
            return 0.0;
        }
        if (!Double.isFinite(victimMaxHealth) || victimMaxHealth <= 0.0) {
            return rawDamage;
        }
        double allowed = rawDamage;
        if (Double.isFinite(maxPercentPerHit) && maxPercentPerHit > 0.0) {
            allowed = Math.min(allowed, victimMaxHealth * maxPercentPerHit);
        }
        if (Double.isFinite(maxPercentPerCast) && maxPercentPerCast > 0.0) {
            double budget = victimMaxHealth * maxPercentPerCast
                    - Math.max(0.0, Double.isFinite(alreadyDealtThisCast) ? alreadyDealtThisCast : 0.0);
            allowed = Math.min(allowed, budget);
        }
        return Math.max(0.0, allowed);
    }
}
