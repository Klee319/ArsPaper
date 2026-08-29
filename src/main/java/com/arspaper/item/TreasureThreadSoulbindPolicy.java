package com.arspaper.item;

import java.util.UUID;

/**
 * スレッド魂縛の判定 —— 2026-08-25 (W-259)、2026-08-29 に catalog {@code bind-type} へ移した。
 *
 * <h2>何を解こうとしているのか</h2>
 * ユーザーの本質的な要求はこう述べられた:
 * <blockquote>「どうやって初心者に上級者が集めたスレッドが無尽蔵に供給され
 * 収集コンテンツをつぶされることを防ぐかが本質」</blockquote>
 *
 * <p>魂縛は「レベルで制限する」より素直に効く: 譲渡そのものを止めるので、
 * 装備に挿してから渡す抜け道も同時に塞がる(挿す時点で所有者判定が走るため)。
 *
 * <h2>対象の決め方は catalog の {@code bind-type: SOULBOUND}</h2>
 * 2026-08-29 の確定要件は「ハードコードではなく設定エディタのバインド種別」。
 * {@link com.arspaper.integration.TrinityForgeBridge#catalogAutoStampsOwner} が
 * {@code items/catalog.yml} の {@code thread_<id>} を読む。作業台/儀式で作れる ID は
 * TRADEABLE（入手経路がドロップでも縛らない）。レシピの無い ID は SOULBOUND で
 * 入手時に所有者が付く。
 *
 * <p>TrinityForge 未ロード時だけ、後方互換として {@link #isSoulboundCmd} の
 * トレジャー帯(300070-300079)へ落ちる。本番は catalog が正。
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
     * <p>正は TrinityForge catalog の {@code bind-type}。TF が居ない構成だけ
     * {@link #isSoulboundCmd} のトレジャー帯へフォールバックする。
     */
    public static boolean isSoulbound(ThreadType type) {
        if (type == null) {
            return false;
        }
        Boolean fromCatalog = com.arspaper.integration.TrinityForgeBridge
                .catalogAutoStampsOwner("thread_" + type.getId());
        if (fromCatalog != null) {
            return fromCatalog;
        }
        return isSoulboundCmd(type.getCustomModelData());
    }

    /**
     * CustomModelData だけで見る版。TrinityForge 未ロード時のフォールバック専用。
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
