package com.arspaper.mana;

import com.arspaper.mana.ManaBaseStats.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ManaBaseStats#resolve} の解決規約を固定するテスト。
 *
 * <p>背景(2026-08-01 実バグ): TF の {@code BaseStatsConfig#load} は
 * <b>0 を書いたキーをロード時に捨てる</b>(0 = 加算なし = 未記載 という規約)。
 * 旧実装は「値が取れなかった」を無条件で ArsPaper のフォールバック定数へ落としていたため、
 * {@code combat/base-stats.yml} に {@code mana-onhit-percent: 0} と書いてあっても
 * 実際には被弾のたびに最大マナの3%が回復していた。
 *
 * <p>round2 の指摘: その修正(「TFロード済み ＝ 未記載は0」)は、
 * <b>キーが1行も無い</b>場合と <b>yml のパースに失敗した</b>場合まで 0 に潰していた。
 * どちらも「0 と設定した」ではないので、フォールバックへ落ちるのが正しい。
 * 状態は {@link Source} の4値で受け取る。
 *
 * <p>⚠ このテストは <b>base-stats.yml の実際の値に依存しない</b>。
 * 作業ツリーの yml は他セッションが編集中で、HEAD と食い違うことがあるため
 * (実際 round1 は「出荷ymlのマナ回復系は0」という作業ツリー限定の前提に乗っていた)。
 * HEAD の値でも壊れないことは {@link #headShippedValuesResolveVerbatim()} で別途固定する。
 */
class ManaBaseStatsResolveTest {

    // ---- 回復キー(zeroWhenDeclared = true) ----

    @Test
    @DisplayName("回復キー: yml にその行があって値が届かない = 0 と書いた → 0(無効)")
    void recoveryKeyIsZeroOnlyWhenTheKeyIsActuallyDeclared() {
        // これがバグの本体: 以前はここで 0.03(3%) が返っていた。
        assertEquals(0.0, ManaBaseStats.resolve(OptionalDouble.empty(), Source.KEY_DECLARED, 0.03, true));
        assertEquals(0.0, ManaBaseStats.resolve(OptionalDouble.empty(), Source.KEY_DECLARED, 5.0, true));
    }

    @Test
    @DisplayName("回復キー: 行が無い/ymlを読めない/TF未ロード は 0 に潰さずフォールバックへ落ちる")
    void recoveryKeyFallsBackWhenTheZeroCannotBeAttributedToConfiguration() {
        // round2 の指摘そのもの。設定ミスやバージョン差でマナ回復が無言で全停止しないこと。
        assertEquals(0.03, ManaBaseStats.resolve(OptionalDouble.empty(), Source.KEY_ABSENT, 0.03, true));
        assertEquals(0.03, ManaBaseStats.resolve(OptionalDouble.empty(), Source.UNREADABLE, 0.03, true));
        assertEquals(0.03,
                ManaBaseStats.resolve(OptionalDouble.empty(), Source.TRINITYFORGE_ABSENT, 0.03, true));
    }

    @Test
    @DisplayName("回復キー: 値が書かれていればそれを使う")
    void recoveryKeyUsesConfiguredNonZeroValue() {
        // TF側で 3(=3%) と書けば PercentStatNormalize が 0.03 へ正規化して届く。
        assertEquals(0.03, ManaBaseStats.resolve(OptionalDouble.of(0.03), Source.KEY_DECLARED, 0.99, true));
    }

    // ---- 量的キー(zeroWhenDeclared = false) ----

    @ParameterizedTest
    @EnumSource(Source.class)
    @DisplayName("量的キー: どの状態でも 0 は採らない(最大マナ0/間隔0tick/idle待ち0秒で機構が壊れる)")
    void quantityKeyNeverCollapsesToZero(Source source) {
        assertEquals(100.0, ManaBaseStats.resolve(OptionalDouble.empty(), source, 100.0, false));
        assertEquals(20.0, ManaBaseStats.resolve(OptionalDouble.empty(), source, 20.0, false));
        assertEquals(5.0, ManaBaseStats.resolve(OptionalDouble.empty(), source, 5.0, false));
    }

    @Test
    @DisplayName("量的キー: 書かれた値はそのまま使う(加算ではなく置換)")
    void quantityKeyUsesConfiguredValueVerbatim() {
        assertEquals(250.0, ManaBaseStats.resolve(OptionalDouble.of(250.0), Source.KEY_DECLARED, 100.0, false));
    }

    @ParameterizedTest
    @EnumSource(Source.class)
    @DisplayName("値が届いている時点で状態に関わらずその値が勝つ")
    void configuredValueWinsRegardlessOfSource(Source source) {
        assertEquals(7.0, ManaBaseStats.resolve(OptionalDouble.of(7.0), source, 1.0, true));
        assertEquals(7.0, ManaBaseStats.resolve(OptionalDouble.of(7.0), source, 1.0, false));
    }

    // ---- mana-idle-seconds は閾値なので量的キー ----

    @Test
    @DisplayName("mana-idle-seconds を 0 にしても『常時idle扱い』へ転ばない")
    void idleSecondsIsAThresholdAndNeverBecomesZero() {
        // 0 を「無効」と読むと待ち時間ゼロ = 常時 idle となり、回復が切れるどころか強化される。
        // このキーだけは回復キー群ではなく量的キー群(zeroWhenDeclared=false)で解決する。
        assertEquals(5.0, ManaBaseStats.resolve(OptionalDouble.empty(), Source.KEY_DECLARED, 5.0, false));
        // 参考: もし回復キー扱いのままなら 0 になってしまう(これが round2 で指摘された反転)
        assertEquals(0.0, ManaBaseStats.resolve(OptionalDouble.empty(), Source.KEY_DECLARED, 5.0, true));
    }

    // ---- HEAD の出荷値でも壊れないこと ----

    /**
     * HEAD({@code git show HEAD:TrinityForge/src/main/resources/combat/base-stats.yml})の値。
     * 作業ツリーの yml は他セッションが 0 へ書き換えている最中なので、そちらは参照しない。
     */
    @Test
    @DisplayName("HEAD の出荷値(0.03 / 0.03 / 5 / 0.01 / 0)はそのまま採用され、挙動が変わらない")
    void headShippedValuesResolveVerbatim() {
        // 全キーが行として存在し、0 以外の値は TF から届く = 値がそのまま勝つ。
        assertEquals(0.03,
                ManaBaseStats.resolve(OptionalDouble.of(0.03), Source.KEY_DECLARED, 0.03, true));  // mana-onhit-percent
        assertEquals(0.03,
                ManaBaseStats.resolve(OptionalDouble.of(0.03), Source.KEY_DECLARED, 0.03, true));  // mana-onattack-percent
        assertEquals(5.0,
                ManaBaseStats.resolve(OptionalDouble.of(5.0), Source.KEY_DECLARED, 5.0, false));   // mana-idle-seconds
        assertEquals(0.01,
                ManaBaseStats.resolve(OptionalDouble.of(0.01), Source.KEY_DECLARED, 0.01, true));  // mana-idle-bonus-percent
        // mana-idle-bonus-flat: 0 は TF がロード時に捨てるので届かない。行はあるので 0(無効)。
        assertEquals(0.0,
                ManaBaseStats.resolve(OptionalDouble.empty(), Source.KEY_DECLARED, 0.0, true));
        // 2026-08-16: 量的キーだった mana-max-base / mana-regen-base / mana-regen-interval-ticks は
        // ArsPaper config.yml の mana.default-max / default-regen-rate / regen-interval-ticks へ移設され、
        // ここを通らなくなった(ManaBaseValuesConfigTest が新しい読み口を固定する)。
        // このクラスに残る量的キーは mana-idle-seconds だけで、上の1件で固定済み。
    }

    @Test
    @DisplayName("作業ツリー側の値(回復系すべて0)でも、0 は 0 として通る")
    void workingTreeZeroesResolveToZero() {
        // 他セッションが 0 へ書き換えた場合。行はあるので「0と設定した」= 0(無効)。
        assertEquals(0.0, ManaBaseStats.resolve(OptionalDouble.empty(), Source.KEY_DECLARED, 0.03, true));
        // ただし mana-idle-seconds は閾値なので 0 にはならずフォールバック 5 秒が効く。
        assertEquals(5.0, ManaBaseStats.resolve(OptionalDouble.empty(), Source.KEY_DECLARED, 5.0, false));
    }
}
