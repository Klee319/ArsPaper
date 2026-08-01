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
 * <h2>出荷値で 1発上限が実際に効く範囲（2026-07-31 F5 指摘4 の訂正）</h2>
 * <p>初版の commit メッセージ／javadoc は「PvE の手触りは変わらない／高HPモブには実質不作用」と
 * 書いていたが、これは<b>最大体力 40 以上の対象に限って正しい</b>。出荷 {@code glyphs.yml} の
 * 生ダメージは {@code base-damage 4.0 + amplify-damage-bonus 1.0 × 増幅段} なので増幅6段で 10.0、
 * 1発上限は {@code 最大体力 × 0.25} である。両者が交差するのは最大体力 40 なので:
 * <ul>
 *   <li><b>最大体力 40 以上</b>（TF モブ 150+ / EliteMobs のボス等）: {@code 10.0 ≤ 40×0.25} なので
 *       1発上限は<b>不作用</b>（ここだけが「PvE 不変」）。</li>
 *   <li><b>最大体力 40 未満</b>: 1発が最大体力の 25% へ丸められる。実測例 —
 *       プレイヤー（TF の上限 33）→ {@code 8.25}（10.0 から約 -17%）、
 *       バニラ動物・村人・{@code SummonDecoy}/{@code SummonVex}/{@code SummonWolves} のような
 *       低HP召喚体（20）→ {@code 5.0}（半減）。</li>
 * </ul>
 * つまり<b>低HP対象には確かに弱くなっている</b>。これは意図した挙動（ワンショット防止の副作用として
 * 「最低4発」を全帯で保証する）だが、「PvE 不変」という記述は不正確だったので数値で明示しておく。
 * 1詠唱の累計上限（100%）は最大体力に比例するので、どの帯でも「1詠唱で最大体力ぶんまで」で一定。
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

    /** 斉射1回に対する判断（{@link #volleyOutcome} の戻り値）。 */
    public enum VolleyOutcome {
        /** 撃てる対象がいる。通常の斉射。 */
        FIRE,
        /** 索敵範囲に対象が1体もいない。召喚は維持する（敵が入ってくるのを待つ）。 */
        IDLE,
        /** 索敵範囲の対象が全員この詠唱の累計上限に到達している。召喚を終了する。 */
        END_SUMMON
    }

    /**
     * 斉射1回で何をすべきか（純関数）。
     *
     * <p><b>なぜ必要か（2026-07-31 F5 指摘3）</b>: 旧実装は「距離順に {@code limit(発射数)} で切ってから
     * 撃つ」順序だったため、<b>累計上限を使い切った対象が発射スロットを占有し続けた</b>。
     * 分裂拡張なし（発射数=1、既定）だと最近接の1体しかスロットが無いので、
     * 「最近接の敵が累計上限に達したがまだ生きている」状態で
     * <b>範囲内に有効な敵がいても召喚が残り時間ぜんぶ不発</b>になる。
     * 出荷値だと最長 27 秒（{@code base-duration 180 + 延長6段×60 = 540tick}）が丸ごと無駄になり、
     * PvP では「前衛が回復ポーションを飲んで立っているだけで Tier3 グリフを丸ごと吸収できる」抜け穴、
     * PvE でも再生持ちボスに対して同じ形で不発になっていた。
     *
     * <p>対処は2段構え:
     * <ol>
     *   <li>累計上限を使い切った対象は<b>選定から外す</b>（＝スロットを占有しない）ので、
     *       他の有効な敵へダメージが通り続ける。</li>
     *   <li>それでも「範囲内の対象が全員上限に到達」した場合は、残り時間を無言で消費するのではなく
     *       <b>召喚を終了してプレイヤーへ1回だけ知らせる</b>（詠唱し直せば累計はリセットされるので、
     *       回復する対象が恒久的に無敵になることはない）。</li>
     * </ol>
     * 「範囲に誰もいない」({@code IDLE}) と「全員上限」({@code END_SUMMON}) を区別するのが要点 —
     * 前者で終了させると「敵が来る前に置いた設置技」が成立しなくなる。
     *
     * @param candidatesInRange 索敵範囲内の有効対象数（累計上限で絞る<b>前</b>）
     * @param shootableInRange  そのうち、この詠唱でまだ削れる余地がある対象数
     */
    public static VolleyOutcome volleyOutcome(int candidatesInRange, int shootableInRange) {
        if (candidatesInRange <= 0) {
            return VolleyOutcome.IDLE;
        }
        return shootableInRange <= 0 ? VolleyOutcome.END_SUMMON : VolleyOutcome.FIRE;
    }
}
