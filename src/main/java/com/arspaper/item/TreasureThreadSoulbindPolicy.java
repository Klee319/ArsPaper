package com.arspaper.item;

import java.util.UUID;

/**
 * <b>ダンジョン(構造物のルートチェスト)産のスレッドだけを魂縛する</b>判定 —— 2026-08-25 (W-259)。
 *
 * <h2>何を解こうとしているのか</h2>
 * ユーザーの本質的な要求はこう述べられた:
 * <blockquote>「どうやって初心者に上級者が集めたスレッドが無尽蔵に供給され
 * 収集コンテンツをつぶされることを防ぐかが本質」</blockquote>
 *
 * <p>最初はスレッドに<b>レベル制限</b>を掛ける案だったが、ユーザー自身が
 * 「そもそも他の人にスレッド付きの武器とかが渡された場合にどうやって対処しよう」と
 * 抜け道を指摘し、案は撤回された。確定した方針は
 * <b>「ダンジョンドロップ品のみ魂縛し、それ以外はしない」</b>。
 *
 * <p>魂縛は「レベルで制限する」より素直に効く: 譲渡そのものを止めるので、
 * 装備に挿してから渡す抜け道も同時に塞がる(挿す時点で所有者判定が走るため)。
 * 一方で儀式で作れるスレッドは<b>今までどおり自由に譲渡できる</b> ── 収集コンテンツを
 * 守りたいのは「拾うしか入手経路が無い」枠だけで、作れる枠まで縛ると交易が死ぬ。
 *
 * <h2>なぜ TrinityForge 側の魂縛機構を使わないのか</h2>
 * TF には {@code bind-type: SOULBOUND} と {@code PickupQualityListener#stampOwnerIfEligible}
 * があるが、あれは<b>PDC に {@code bind_type} と {@code roll_seed} がある品にしか発火しない</b>。
 * 対象10種は TF の catalog で {@code external-source: arspaper} と宣言されていて
 * <b>実体を Ars が作る</b>ため、その2つの PDC を持たない ── つまり TF 側の機構は
 * 何も起きないまま素通りする(ログも出ない)。だから所有者の刻印と装着ゲートは
 * <b>フォーク側に置くしかない</b>。
 *
 * <h2>対象の決め方は「id の一覧」ではなく CMD の帯</h2>
 * 許可リスト方式は<b>リスト自体が誤ると検査ごと無効化される</b>(実在しない id を
 * 書いて壊れを見逃した前例がある)。ここでは
 * {@link #TREASURE_CMD_MIN}..{@link #TREASURE_CMD_MAX} の帯で判定するので、
 * トレジャースレッドを増やす = 帯の中に CMD を採る、というだけで自動的に対象へ入る。
 */
public final class TreasureThreadSoulbindPolicy {

    /** トレジャースレッド(構造物のルートチェスト専属)の CustomModelData 帯・下限(含む)。 */
    public static final int TREASURE_CMD_MIN = 300070;

    /** 同・上限(含まない)。2026-08-23 に新設した10種 300070-300079 がこの帯。 */
    public static final int TREASURE_CMD_MAX = 300080;

    private TreasureThreadSoulbindPolicy() {
    }

    /**
     * このスレッドが魂縛の対象か。
     *
     * <p>⚠ 儀式で作れるスレッドは対象外。ユーザー確定要件が
     * 「ダンジョンドロップ品のみ魂縛し、それ以外はしない」なので、
     * ここを広げる = 要件を超えた変更になる。
     */
    public static boolean isSoulbound(ThreadType type) {
        return type != null && isSoulboundCmd(type.getCustomModelData());
    }

    /**
     * CustomModelData だけで見る版。
     *
     * <p>⚠ 判定の実体を<b>こちら</b>に置いているのは、{@link ThreadType} が
     * {@code PotionEffectType} の静的初期化を踏むため<b>サーバ無しではクラスロードできない</b>から。
     * {@code ThreadType} を触る形でしか書かないと、テストが
     * {@code ExceptionInInitializerError} で中断し ── <b>赤ではあるが何も検査していない</b>状態になる。
     */
    public static boolean isSoulboundCmd(int customModelData) {
        return customModelData >= TREASURE_CMD_MIN && customModelData < TREASURE_CMD_MAX;
    }

    /**
     * {@code actor} がこのスレッドを使ってよいか。
     *
     * <p>所有者が未刻印({@code owner == null})なら誰でも使える ── 刻印は「最初に拾った人」で
     * 決まるので、まだ誰も拾っていない個体(チェストの中、地面に落ちている間)は
     * 縛らないのが正しい。刻印済みなら本人だけ。
     */
    public static boolean mayUse(UUID owner, UUID actor) {
        if (owner == null) {
            return true;
        }
        return owner.equals(actor);
    }
}
