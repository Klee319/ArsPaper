package com.arspaper.mana;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ManaBaseStats#resolve} の解決規約を固定するテスト。
 *
 * <p>背景(2026-08-01 実バグ): TF の {@code BaseStatsConfig#load} は
 * <b>0 を書いたキーをロード時に捨てる</b>(0 = 加算なし = 未記載 という規約)。
 * 旧実装は「値が取れなかった」を無条件で ArsPaper のフォールバック定数へ落としていたため、
 * 出荷 {@code combat/base-stats.yml} に {@code mana-onhit-percent: 0} と書いてあるのに
 * 実際には被弾のたびに最大マナの3%が回復していた(与ダメも同3%、非詠唱5秒で1%)。
 */
class ManaBaseStatsResolveTest {

    // ---- 回復キー(zeroWhenTfLoaded = true) ----

    @Test
    void recoveryKeyIsZeroWhenTrinityForgeIsLoadedButValueIsAbsentOrZero() {
        // TFロード済み + 値なし(= yml に 0 と書いた、または行ごと無い) → 0。
        // これがバグの本体: 以前はここで 0.03(3%) が返っていた。
        assertEquals(0.0, ManaBaseStats.resolve(OptionalDouble.empty(), true, 0.03, true));
        assertEquals(0.0, ManaBaseStats.resolve(OptionalDouble.empty(), true, 5.0, true));
    }

    @Test
    void recoveryKeyFallsBackOnlyWhenTrinityForgeIsAbsent() {
        // ArsPaper 単体運用(TF未ロード)では従来どおりフォールバック定数で動く(fail-open)。
        assertEquals(0.03, ManaBaseStats.resolve(OptionalDouble.empty(), false, 0.03, true));
    }

    @Test
    void recoveryKeyUsesConfiguredNonZeroValue() {
        // TF側で 3(=3%) と書けば PercentStatNormalize が 0.03 へ正規化して届く。
        assertEquals(0.03, ManaBaseStats.resolve(OptionalDouble.of(0.03), true, 0.99, true));
    }

    // ---- 量的キー(zeroWhenTfLoaded = false) ----

    @Test
    void quantityKeyFallsBackWhenValueIsAbsentEvenIfTrinityForgeIsLoaded() {
        // 最大マナ/回復量/回復間隔を 0 にすると機構ごと死ぬので、ここだけは 0 を採らない。
        assertEquals(100.0, ManaBaseStats.resolve(OptionalDouble.empty(), true, 100.0, false));
        assertEquals(20.0, ManaBaseStats.resolve(OptionalDouble.empty(), true, 20.0, false));
    }

    @Test
    void quantityKeyUsesConfiguredValueVerbatim() {
        // mana-max-base は「Ars既定の100に加算」ではなく「100を置換」する(仕様)。
        assertEquals(250.0, ManaBaseStats.resolve(OptionalDouble.of(250.0), true, 100.0, false));
    }

    @Test
    void configuredValueWinsRegardlessOfTrinityForgeLoadedFlag() {
        // 値が届いている時点で TF はロードされているが、フラグに依存しないことを固定する。
        assertEquals(7.0, ManaBaseStats.resolve(OptionalDouble.of(7.0), false, 1.0, true));
        assertEquals(7.0, ManaBaseStats.resolve(OptionalDouble.of(7.0), false, 1.0, false));
    }
}
