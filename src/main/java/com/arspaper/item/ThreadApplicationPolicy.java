package com.arspaper.item;

import org.bukkit.Material;

/**
 * スレッド(糸)の効果を「装備がどのスロットにあるとき適用するか」の線引き。
 *
 * <p><b>2026-07-31 (F2「武器・触媒のスレッド枠が機能しない」)</b>: それまでスレッドは
 * {@code ArmorManaListener} の防具4部位ループの中だけで収集されていたため、
 * item-stats の {@code thread-slots} を持つ 126 件のうち<b>非防具 78 件(触媒/剣/斧/槍/鎌/弓/
 * クロスボウ/トライデント/メイス)は lore に「スレッド枠 N枠」と出るだけの飾り</b>だった。
 * 収集をメインハンド/オフハンドへ広げたが、<b>広げたのは数値だけ</b>である。
 *
 * <h2>手持ちへ広げたもの / 広げなかったもの(オーケストレータ決定)</h2>
 * <table>
 *   <caption>スロット区分ごとの適用範囲</caption>
 *   <tr><th>効果</th><th>着用防具</th><th>メイン/オフハンド</th></tr>
 *   <tr><td>数値ステ(マナ最大/回復・被弾/与ダメ回復・コスト減・厳選ステ・thread-sets のセット効果)</td>
 *       <td>適用</td><td><b>適用</b></td></tr>
 *   <tr><td>常時ポーション効果({@code ThreadType#hasPotionEffect})</td><td>適用</td><td><b>非適用</b></td></tr>
 *   <tr><td>飛行(滑空)({@code ThreadType#isFlightThread})</td><td>適用</td><td><b>非適用</b></td></tr>
 *   <tr><td>バックパック({@code ThreadType#isBackpackThread})</td><td>適用</td>
 *       <td><b>装着そのものを拒否</b></td></tr>
 * </table>
 *
 * <p>常時効果を広げない理由は「事故に見えるから」である ── 剣を握っただけで滑空が付いたり、
 * ホットバーをスクロールするたびにポーション効果が点滅したりするのは、機能ではなく不具合として
 * 受け取られる({@code ArmorManaListener} の再計算は {@code PlayerItemHeldEvent} でも走る)。
 *
 * <p>バックパックだけは「効かない」ではなく<b>装着を拒否</b>する。{@code BackpackGui} の
 * 収納データは装着先アイテムの PDC に入り、取り出し口である {@code /ars backpack} は
 * <b>着用中の防具しか走査しない</b>。武器に装着できてしまうと、しまった中身へ二度と
 * 辿り着けない(=データ喪失)ため、入口で止めるのが唯一安全な形。
 *
 * <p>Bukkit ランタイムを必要としない純粋な判定だけを置く(このフォークのテスト基盤は
 * MockBukkit を持たないため)。{@link Material} は素の enum なのでサーバ無しでも評価できる。
 */
public final class ThreadApplicationPolicy {

    /**
     * {@code threads.yml} で {@code stackable:} を書かなかったときの既定
     * (2026-08-18 ユーザー確定要件「同一のスレッドを重複で入れられるようにしてほしい」)。
     *
     * <p><b>旧既定は {@code false}(=1装備に1本まで)で、これが {@code thread-sets.yml} の
     * 上位ティアを到達不能にする原因だった</b>: キャリアは着用防具4部位 + メインハンドの5個
     * (オフハンドは {@code offhand-stats-apply: true} の品が出荷 item-stats.yml に0件)なので、
     * 1装備1本なら同種は最大5本 ── 6段のしきい値は永久に発動しない
     * ({@code role_luck} / {@code role_effeciency} が実際にその状態だった)。
     */
    public static final boolean DEFAULT_STACKABLE = true;
    // ⚠ 2026-08-25 (W-254): この既定は true のままだが、DEFAULT_MAX_STACK が 1 になったので
    //   実効は「1装備につき同種1本」。true/false のどちらでも結果は同じになるため、
    //   上限の意味は max の側だけで読むこと(true を false へ変えても挙動は変わらない)。

    /**
     * {@code max:} を書かなかったときの1装備あたりの上限。
     *
     * <p><b>2026-08-25 (W-254): 2 → 1。</b>ユーザー確定要件
     * 「同じスレッドは同じ部位に1つまでしか付けられないように修正する。これにより回避率や
     * ダメージ軽減等の一部100%に達成するとバランスの壊れるステータスを防ぐ」。
     *
     * <p><b>なぜ上限を絞るのが効くのか</b>: 回避率・ダメージ軽減率・会心軽減率(防具強度)・
     * 各耐性は<b>割合</b>なので、同じ種を枠へ集中させると合計が上限
     * ({@code combat/damage.yml} の {@code max-mitigation-rate} 0.9 /
     * {@code max-dodge-chance} 0.9 / {@code max-crit-reduction} 1.0)へ張り付く。
     * 張り付いた後は装備を更新しても一切効かなくなる ── 上限に触れる編成が1つあるだけで
     * 「その先の育成が無意味」という壊れ方をする。1装備1本にすると同種の総数は
     * キャリア数(着用防具4部位 + メインハンド = 5)で頭打ちになる。
     *
     * <p>⚠ <b>この値を2以上へ戻すと、{@code thread-sets.yml} の到達可能な最大しきい値も動く</b>
     * (1本なら 1 x 5 = 5、2本なら 10)。しきい値の側を先に確認すること
     * ({@code ThreadSetThresholdReachabilityTest})。
     *
     * <p>※かつては「セット効果の最上位ティア(6)を 2 x 5 = 10 で到達可能にするため 2」と
     * 説明していた。2026-08-25 に上限を 1 へ絞ったので、6段だった
     * {@code role_luck} / {@code role_effeciency} は 5 段へ畳んである。
     */
    public static final int DEFAULT_MAX_STACK = 1;

    /**
     * 同じ装備へ同じスレッドをもう1本挿せるか(純関数)。
     *
     * <p>{@code ThreadGui} の装着経路が唯一の呼び出し元。{@code stackable: false} を
     * 明示した種だけが「1本まで」で、既定({@link #DEFAULT_STACKABLE})は重複可。
     *
     * @param stackable    {@code ThreadConfig#isStackable}(未記載は {@link #DEFAULT_STACKABLE})
     * @param maxStack     {@code ThreadConfig#getMaxStack}(未記載は {@link #DEFAULT_MAX_STACK})
     * @param currentCount その装備に既に挿さっている同種の本数
     */
    public static boolean canSocketAnother(boolean stackable, int maxStack, int currentCount) {
        if (!stackable) {
            return currentCount <= 0;
        }
        return currentCount < maxStack;
    }

    /** スレッドを保持している装備が置かれているスロットの区分。 */
    public enum SlotOrigin {
        /** 着用中の防具4部位。 */
        WORN_ARMOR,
        /** メインハンド(武器・触媒・ツール)。 */
        MAIN_HAND,
        /** オフハンド(item-stats の {@code offhand-stats-apply: true} の品だけがここに来る)。 */
        OFF_HAND
    }

    private ThreadApplicationPolicy() {
    }

    /**
     * 数値ステ(マナ系・厳選ステ・thread-sets のセット効果)を適用するか。
     * 全スロットで適用する ── これが F2 で「武器・触媒でも機能させる」と決めた本体。
     */
    public static boolean appliesNumericStats(SlotOrigin origin) {
        return origin != null;
    }

    /**
     * 常時効果(ポーション/飛行)を適用するか。着用中の防具だけ。
     * 手持ちで発動させると持ち替えのたびに点滅・付け外しが起きて事故に見えるため。
     */
    public static boolean appliesAmbientEffects(SlotOrigin origin) {
        return origin == SlotOrigin.WORN_ARMOR;
    }

    /**
     * <b>スタックした装備へスレッドを装着させてはいけない</b>（2026-07-31 F6 指摘1・HIGH）。
     *
     * <p><b>複製とデータ喪失の両方が player-reachable だった</b>: Bukkit の {@code ItemMeta} は
     * <b>スタック単位</b>なので、{@code ThreadGui#saveThreadSlots} の {@code editMeta} は
     * スタック内の <b>N 個すべて</b>へ {@code THREAD_SLOTS} / {@code THREAD_SLOT_ROLLS} / lore を書く。
     * 一方スレッドの消費はちょうど 1 個。したがって
     * <ul>
     *   <li><b>装着</b>: 5個スタックの杖へ1本装着 → スタックを分割すると
     *       <b>スレッド1本で5本の強化済み杖</b>になる（複製）。</li>
     *   <li><b>取り外し</b>: 返ってくるスレッドは1本だけなのに、
     *       5本すべてからスレッドが消える（データ喪失）。</li>
     * </ul>
     *
     * <p>F2 で装着 GUI を「決してスタックしない防具」から手持ち装備へ広げたことで到達可能になった。
     * {@code thread-slots} を持つ 127 件のうち、{@code BLAZE_ROD} 触媒 11 件と {@code ENDER_EYE#85} は
     * <b>最大スタック 64</b> である。しかも {@code CraftQualityListener} は
     * プロトタイプ1個に {@code rollSeed} を1回だけ刻んで {@code setResult(stamped.clone())} するため、
     * <b>シフトクラフトすると PDC がバイト単位で同一な N 個スタック</b>ができる
     * （{@code GiveItemCommand} も同様）。「スタック可能な品はロールを持たない」という前提は成り立たない。
     *
     * <p>対処は「1個だけ持ってから装着させる」の一点。
     * 入口（{@code /ars thread} / 防具のスニーク+右クリック）と
     * 装着直前（{@code ThreadGui#refreshTargetFromSlot}）の両方で通す
     * ── GUI を開いたあとにスタックを作り直せるため、入口だけでは塞げない。
     *
     * @param amount 対象スタックの個数
     * @return 装着を拒否すべきなら true
     */
    public static boolean isStackTooLargeToSocket(int amount) {
        return amount > 1;
    }

    /**
     * {@code type} を「防具ではない装備」へ装着してよいか。
     *
     * <p>バックパックだけ {@code false}。収納データの取り出し口({@code /ars backpack})が
     * 着用中の防具しか見ないため、武器に入れると中身へ辿り着けなくなる。
     * ポーション/飛行スレッドは {@code true} ── 常時効果は出ないが厳選ステとセット効果は
     * 効くので死に枠にはならない(GUI 側でその旨を明示する)。
     */
    public static boolean canSocketOutsideArmor(ThreadType type) {
        return isSocketableOutsideArmor(type != null && type.isBackpackThread());
    }

    /**
     * {@link #canSocketOutsideArmor(ThreadType)} の純粋な中身。
     * {@link ThreadType} は静的初期化で {@code PotionEffectType} レジストリを触るため
     * サーバ無しでロードできない ── そのため判定本体を素の boolean で切り出してある
     * (テストはこちらを直接叩く)。
     */
    public static boolean isSocketableOutsideArmor(boolean backpackThread) {
        return !backpackThread;
    }

    /**
     * {@code type} が「防具でしか意味を持たない常時効果」を持つか(GUI の注記判定用)。
     */
    public static boolean isAmbientOnlyEffect(ThreadType type) {
        return type != null
                && hasAmbientOnlyEffect(type.hasPotionEffect(), type.isFlightThread(), type.isBackpackThread());
    }

    /**
     * {@link #isAmbientOnlyEffect(ThreadType)} の純粋な中身
     * ({@link #isSocketableOutsideArmor(boolean)} と同じ理由で分けてある)。
     */
    public static boolean hasAmbientOnlyEffect(boolean potionEffect, boolean flightThread,
                                               boolean backpackThread) {
        return potionEffect || flightThread || backpackThread;
    }

    /**
     * {@code material} が着用スロット(頭/胴/脚/足)へ入る材質か。
     *
     * <p>TrinityForge の {@code EquipmentSlotResolver} が正本。TF が未ロード/未リンクの環境
     * (このフォークのテスト実行時は {@code libs/TrinityForge.jar} が {@code compileOnly} なので
     * クラスが解決できず {@code NoClassDefFoundError} になる)では材質名の接尾辞で判定する。
     *
     * <p>{@code Material#isAir()} は使わない ── ブロック型の遅延解決で
     * {@code org.bukkit.Registry} を触るためサーバ無しでは落ちる。空気材質はどの接尾辞にも
     * 当たらないので、判定としても要らない。
     */
    public static boolean isArmorSlotMaterial(Material material) {
        if (material == null) {
            return false;
        }
        try {
            return switch (com.trinityforge.stats.EquipmentSlotResolver.resolve(material)) {
                case HEAD, CHEST, LEGS, FEET -> true;
                default -> false;
            };
        } catch (Throwable tfUnavailable) {
            return isArmorSlotMaterialName(material.name());
        }
    }

    /** {@link #isArmorSlotMaterial} の TF 非依存フォールバック(材質名だけで判定)。 */
    public static boolean isArmorSlotMaterialName(String materialName) {
        if (materialName == null) {
            return false;
        }
        return materialName.endsWith("_HELMET") || materialName.equals("TURTLE_HELMET")
                || materialName.endsWith("_CHESTPLATE") || materialName.equals("ELYTRA")
                || materialName.endsWith("_LEGGINGS") || materialName.endsWith("_BOOTS");
    }

    // isToolMaterial/isToolMaterialName (2026-08-02 依頼#44の「斧を除く純粋ツールだけ」判定)は
    // 2026-08-04 に削除済み。ThreadGuiOpenListener の見上げジェスチャーはユーザー確定要件により
    // 「スレッド枠を持つ装備全般」(斧・武器・杖・触媒を含む)へ対象を広げたため、
    // 素材カテゴリで絞り込む必要自体が無くなった(effectiveThreadSlots(item, player) > 0 だけで判定する)。
    // ※かつて「戦闘武器を兼ねる品はコマンド入口のまま」と説明していたが、この線引きはユーザーの
    // 確定要件により撤回された(旧説明のまま復元しないこと)。
}
