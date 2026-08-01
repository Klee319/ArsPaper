package com.arspaper.mana;

import com.arspaper.integration.TrinityForgeBridge;

import java.util.OptionalDouble;

/**
 * マナ初期値の唯一の読み出し口(2026-07-25 config editor T2)。
 *
 * <p>従来 {@code config.yml} の {@code mana.default-max} / {@code mana.default-regen-rate} /
 * {@code mana.regen-interval-ticks} / {@code mana.recovery.*} で設定していた初期値を、
 * TrinityForge の「プレイヤー基礎ステータス」({@code combat/base-stats.yml}) へ移設した。
 * ここから読む値は常に TrinityForge 側が最新の設定の権威であり、{@link TrinityForgeBridge#manaBaseStatRaw}
 * 経由でTF未ロード時にのみ下記フォールバック定数(移設前のArsPaper既定値)へ落ちる(fail-open)。
 *
 * <p>%系(on-hit / on-attack / idle-bonus)は TF 側で PERCENT stat として保存されるため、ここで返す値は
 * 既に分数(0.03 = 3%)である。旧 {@code PERCENT_DIVISOR} による /100 補正は不要になった点に注意。
 *
 * <p>2026-07-29(重複ステ間引き): {@code mana-onhit-flat} / {@code mana-onattack-flat} を廃止した。
 * 固定量の被弾/与ダメマナ回復は TF の {@code hit-mana-recovery} /
 * {@code damage-mana-recovery} に一本化され、{@code ArmorManaListener} が装備分と
 * 非装備分(パーク/役職/永続バフ/base-stats)の両方を拾って加算する。
 * ここに残る %系は「最大マナの何%」で意味が違うため存続する。
 *
 * <h2>⚠ 2026-08-01: 「0 と書いたのにフォールバック値が効く」バグの修正</h2>
 * TF の {@code BaseStatsConfig#load} は <b>0 を書いたキーをロード時に捨てる</b>
 * (「0 = 加算なし = 未記載」という base-stats.yml の規約)。そのため
 * {@code stats()} を引くだけの旧実装では「TFで 0 に設定した」と「TF未ロード」が
 * 区別できず、<b>出荷 base-stats.yml のとおり 0 を書いていても ArsPaper のフォールバック
 * 定数(被弾/与ダメで最大マナの3%回復、非詠唱5秒で1%回復)が黙って効き続けていた</b>。
 *
 * <p>そこでキーを2群に分けて解決する:
 * <ul>
 *   <li><b>量的キー</b>({@code mana-max-base} / {@code mana-regen-base} /
 *       {@code mana-regen-interval-ticks}): 0 は「未設定」とみなしフォールバックへ落ちる。
 *       0 をそのまま採用すると最大マナ0・回復間隔0tickでマナ機構自体が死ぬため、意図的にこの扱い。
 *       <b>設定で 0 を指定することはできない</b>(TF側が 0 を捨てるので原理的に不可能)。</li>
 *   <li><b>回復キー</b>({@code mana-onhit-percent} / {@code mana-onattack-percent} /
 *       {@code mana-idle-seconds} / {@code mana-idle-bonus-percent} / {@code mana-idle-bonus-flat}):
 *       TFがロードされていれば 0/未記載は <b>0(＝無効)</b> として扱う。フォールバック定数は
 *       TF未ロード時(ArsPaper単体運用)にのみ使う。</li>
 * </ul>
 */
public final class ManaBaseStats {

    // 移設前の ArsPaper config.yml 既定値(TF未ロード時のみ使用するフォールバック)。
    private static final int FALLBACK_DEFAULT_MAX = 100;
    private static final int FALLBACK_DEFAULT_REGEN_RATE = 5;
    private static final int FALLBACK_REGEN_INTERVAL_TICKS = 20;
    private static final double FALLBACK_ON_HIT_PERCENT = 0.03;
    private static final double FALLBACK_ON_ATTACK_PERCENT = 0.03;
    private static final int FALLBACK_IDLE_SECONDS = 5;
    private static final double FALLBACK_IDLE_BONUS_PERCENT = 0.01;
    private static final int FALLBACK_IDLE_BONUS_FLAT = 0;

    private ManaBaseStats() {
    }

    public static int defaultMax() {
        int v = round(quantity("mana-max-base", FALLBACK_DEFAULT_MAX));
        // 0以下は「未記載＝バニラのまま」とみなす(base-stats.yml のヘッダ規約と一致させる)。
        // 現状はTF側が0を捨てるためここへ0は届かないが、将来TFが0を保持するようになっても
        // 「最大マナ0で魔法が一切使えない」状態へ黙って落ちないようにする保険。
        return v > 0 ? v : FALLBACK_DEFAULT_MAX;
    }

    public static int defaultRegenRate() {
        return round(quantity("mana-regen-base", FALLBACK_DEFAULT_REGEN_RATE));
    }

    public static int regenIntervalTicks() {
        int v = round(quantity("mana-regen-interval-ticks", FALLBACK_REGEN_INTERVAL_TICKS));
        return v > 0 ? v : FALLBACK_REGEN_INTERVAL_TICKS;
    }

    /** 分数[0,1]で返す(例 0.03 = 3%)。TFロード時に未設定なら 0(無効)。 */
    public static double onHitPercent() {
        return recovery("mana-onhit-percent", FALLBACK_ON_HIT_PERCENT);
    }

    /** 分数[0,1]で返す(例 0.03 = 3%)。TFロード時に未設定なら 0(無効)。 */
    public static double onAttackPercent() {
        return recovery("mana-onattack-percent", FALLBACK_ON_ATTACK_PERCENT);
    }

    public static int idleSeconds() {
        return round(recovery("mana-idle-seconds", FALLBACK_IDLE_SECONDS));
    }

    /** 分数[0,1]で返す(例 0.01 = 1%)。TFロード時に未設定なら 0(無効)。 */
    public static double idleBonusPercent() {
        return recovery("mana-idle-bonus-percent", FALLBACK_IDLE_BONUS_PERCENT);
    }

    public static int idleBonusFlat() {
        return round(recovery("mana-idle-bonus-flat", FALLBACK_IDLE_BONUS_FLAT));
    }

    /** 量的キー: 0/未設定はフォールバックへ落とす(0にすると機構が死ぬため)。 */
    private static double quantity(String key, double fallback) {
        return resolve(TrinityForgeBridge.manaBaseStatRaw(key),
                TrinityForgeBridge.trinityForgeLoaded(), fallback, false);
    }

    /** 回復キー: TFロード時の 0/未設定は 0(無効)として扱う。 */
    private static double recovery(String key, double fallback) {
        return resolve(TrinityForgeBridge.manaBaseStatRaw(key),
                TrinityForgeBridge.trinityForgeLoaded(), fallback, true);
    }

    /**
     * TF から届いた値・TFのロード状態・フォールバックから実効値を決める純関数(テスト対象)。
     *
     * @param tfValue           TF の base-stats から取れた値。TF未ロード / 未記載 / <b>0を記載</b> のいずれでも empty
     * @param tfLoaded          TF 本体がロードされ base-stats を読める状態か
     * @param fallback          TF未ロード時に使う ArsPaper 単体の既定値
     * @param zeroWhenTfLoaded  true なら「TFロード済みで値が届かない」を 0 と解釈する(回復キー)。
     *                          false なら常に {@code fallback} へ落とす(量的キー)
     */
    static double resolve(OptionalDouble tfValue, boolean tfLoaded, double fallback, boolean zeroWhenTfLoaded) {
        if (tfValue.isPresent()) {
            return tfValue.getAsDouble();
        }
        if (zeroWhenTfLoaded && tfLoaded) {
            return 0.0;
        }
        return fallback;
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
