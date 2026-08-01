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
 * 区別できず、<b>base-stats.yml に 0 を書いても ArsPaper のフォールバック
 * 定数(被弾/与ダメで最大マナの3%回復、非詠唱5秒で1%回復)が黙って効き続けていた</b>。
 *
 * <h2>⚠ 2026-08-01 round2: 「値が届かない」を4通りに分ける</h2>
 * 初版は「TFロード済み ＝ 未記載は0」としたが、これは
 * <b>キーが yml に1行も無い場合</b>(TFのバージョン差・行の削除)と
 * <b>base-stats.yml のパースに失敗した場合</b>まで 0 に潰していた。
 * どちらも「0 と設定した」ではないので、フォールバックへ落ちるのが正しい
 * (0に潰すと、設定ミス1つでマナ回復機構が無言で全停止する)。
 * 状態は {@link Source} の4値で受け取る:
 * <ul>
 *   <li>{@link Source#TRINITYFORGE_ABSENT} — TF未ロード(ArsPaper単体運用) → フォールバック</li>
 *   <li>{@link Source#UNREADABLE} — base-stats.yml を読めない/パースできない → フォールバック</li>
 *   <li>{@link Source#KEY_ABSENT} — その行が yml に無い → フォールバック</li>
 *   <li>{@link Source#KEY_DECLARED} — その行が yml にある(値0を含む) → 下記の2群で分岐</li>
 * </ul>
 *
 * <p>{@code KEY_DECLARED} の扱いはキーの性質で2群に分かれる:
 * <ul>
 *   <li><b>量的キー</b>({@code mana-max-base} / {@code mana-regen-base} /
 *       {@code mana-regen-interval-ticks} / {@code mana-idle-seconds}):
 *       0 と書いてもフォールバックへ落とす。0 にすると
 *       「最大マナ0」「回復間隔0tick」「idle判定の待ち時間0秒」で機構が壊れる/意味が反転するため。
 *       <b>設定で 0 を指定することはできない</b>(TF側が 0 を捨てるので原理的に不可能)。</li>
 *   <li><b>回復キー</b>({@code mana-onhit-percent} / {@code mana-onattack-percent} /
 *       {@code mana-idle-bonus-percent} / {@code mana-idle-bonus-flat}):
 *       0 と書いてあれば <b>0(＝無効)</b>。これが「回復量を0にして切る」唯一の手段。</li>
 * </ul>
 *
 * <p>⚠ {@code mana-idle-seconds} が量的キー側にいる理由(2026-08-01 round2): これは回復量ではなく
 * <b>閾値</b>(最後の詠唱から何秒でidleとみなすか)なので、0 を「無効」と読むと
 * <b>待ち時間ゼロ＝常時idle扱い</b>になり、回復が切れるどころか<b>強化される</b>方向へ転ぶ。
 * idle回復を止めたいときは {@code mana-idle-bonus-percent} / {@code mana-idle-bonus-flat} を 0 にする。
 */
public final class ManaBaseStats {

    /** TF側 {@code combat/base-stats.yml} からその1キーがどう見えているか。 */
    public enum Source {
        /** TrinityForge がロードされていない(ArsPaper単体運用)。 */
        TRINITYFORGE_ABSENT,
        /** TFはロード済みだが base-stats.yml を読めない/パースできない。 */
        UNREADABLE,
        /** base-stats.yml は読めたが、そのキーの行が存在しない。 */
        KEY_ABSENT,
        /** base-stats.yml にそのキーが書かれている(値0を含む)。 */
        KEY_DECLARED
    }

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

    /** 分数[0,1]で返す(例 0.03 = 3%)。ymlに 0 と書いてあれば 0(無効)。 */
    public static double onHitPercent() {
        return recovery("mana-onhit-percent", FALLBACK_ON_HIT_PERCENT);
    }

    /** 分数[0,1]で返す(例 0.03 = 3%)。ymlに 0 と書いてあれば 0(無効)。 */
    public static double onAttackPercent() {
        return recovery("mana-onattack-percent", FALLBACK_ON_ATTACK_PERCENT);
    }

    /**
     * 最後の詠唱から何秒でidle扱いにするかの<b>閾値</b>。
     * 0 は「無効」ではなく「常時idle」なので、量的キーとして扱いフォールバックへ落とす。
     */
    public static int idleSeconds() {
        int v = round(quantity("mana-idle-seconds", FALLBACK_IDLE_SECONDS));
        return v > 0 ? v : FALLBACK_IDLE_SECONDS;
    }

    /** 分数[0,1]で返す(例 0.01 = 1%)。ymlに 0 と書いてあれば 0(無効)。 */
    public static double idleBonusPercent() {
        return recovery("mana-idle-bonus-percent", FALLBACK_IDLE_BONUS_PERCENT);
    }

    public static int idleBonusFlat() {
        return round(recovery("mana-idle-bonus-flat", FALLBACK_IDLE_BONUS_FLAT));
    }

    /** 量的キー: 0/未設定はフォールバックへ落とす(0にすると機構が壊れる/意味が反転するため)。 */
    private static double quantity(String key, double fallback) {
        return resolve(TrinityForgeBridge.manaBaseStatRaw(key),
                TrinityForgeBridge.manaBaseStatSource(key), fallback, false);
    }

    /** 回復キー: ymlに 0 と書いてあれば 0(無効)として扱う。 */
    private static double recovery(String key, double fallback) {
        return resolve(TrinityForgeBridge.manaBaseStatRaw(key),
                TrinityForgeBridge.manaBaseStatSource(key), fallback, true);
    }

    /**
     * TF から届いた値・TF側での見え方・フォールバックから実効値を決める純関数(テスト対象)。
     *
     * @param tfValue          TF の base-stats から取れた値。TF未ロード / 未記載 / <b>0を記載</b> のいずれでも empty
     * @param source           そのキーが base-stats.yml でどう見えているか
     * @param fallback         値を採用できないときに使う ArsPaper 単体の既定値
     * @param zeroWhenDeclared true なら「ymlに行があるのに値が届かない = 0と書いた」を 0 と解釈する(回復キー)。
     *                         false なら常に {@code fallback} へ落とす(量的キー)
     */
    static double resolve(OptionalDouble tfValue, Source source, double fallback, boolean zeroWhenDeclared) {
        if (tfValue.isPresent()) {
            return tfValue.getAsDouble();
        }
        if (zeroWhenDeclared && source == Source.KEY_DECLARED) {
            return 0.0;
        }
        // TRINITYFORGE_ABSENT / UNREADABLE / KEY_ABSENT、および量的キーは全てフォールバック。
        return fallback;
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
