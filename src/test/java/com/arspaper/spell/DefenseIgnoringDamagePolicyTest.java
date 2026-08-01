package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防御無視ダメージ（日輪/月輪の直接HP減少）のワンショット防止（2026-07-31 F4 指摘1(b)）の回帰テスト。
 *
 * <p>この経路は {@code EntityDamageEvent} を出さないので守備力・トーテム・PvP抑制が効かない。
 * したがって「1発 / 1詠唱累計で最大体力の何%まで削れるか」だけが唯一の安全弁であり、その算術を固定する。
 */
class DefenseIgnoringDamagePolicyTest {

    private static final double PER_HIT = DefenseIgnoringDamagePolicy.DEFAULT_MAX_PERCENT_PER_HIT;
    private static final double PER_CAST = DefenseIgnoringDamagePolicy.DEFAULT_MAX_PERCENT_PER_CAST;

    @Test
    @DisplayName("既定値は 1発25% / 1詠唱累計100%")
    void defaultsAreQuarterPerHitAndFullPerCast() {
        assertEquals(0.25, PER_HIT, 0.0);
        assertEquals(1.0, PER_CAST, 0.0);
    }

    @Test
    @DisplayName("上限未満のダメージはそのまま通る(機能を殺さない)")
    void smallDamagePassesThrough() {
        assertEquals(4.0, DefenseIgnoringDamagePolicy.cappedDamage(4.0, 20.0, 0.0, PER_HIT, PER_CAST), 1e-9);
    }

    @Test
    @DisplayName("1発の上限: 最大体力20なら1発5.0まで(10594でも即死しない)")
    void perHitCapIsRatioOfMaxHealth() {
        assertEquals(5.0, DefenseIgnoringDamagePolicy.cappedDamage(10594.0, 20.0, 0.0, PER_HIT, PER_CAST), 1e-9,
                "レビュー指摘HIGHそのもの: 杖の攻撃力が乗った1発10594が最大体力33のプレイヤーを確定死亡させていた");
        // 最低何発かかるかは体力側がインフレしても変わらない(スケールフリー)ことの確認。
        assertEquals(100.0, DefenseIgnoringDamagePolicy.cappedDamage(10594.0, 400.0, 0.0, PER_HIT, PER_CAST), 1e-9);
    }

    @Test
    @DisplayName("1詠唱の累計上限: 既に最大体力分削っていたら以降は0(連射で二度殺せない)")
    void perCastBudgetIsExhaustible() {
        double maxHealth = 20.0;
        assertEquals(0.0,
                DefenseIgnoringDamagePolicy.cappedDamage(5.0, maxHealth, 20.0, PER_HIT, PER_CAST), 1e-9);
        // 残り3.0しかないときは3.0だけ通す(1発上限5.0より小さい側が勝つ)。
        assertEquals(3.0,
                DefenseIgnoringDamagePolicy.cappedDamage(5.0, maxHealth, 17.0, PER_HIT, PER_CAST), 1e-9);
    }

    @Test
    @DisplayName("split連射でも1詠唱の合計は最大体力を超えない")
    void volleyTotalNeverExceedsMaxHealthBudget() {
        double maxHealth = 33.0;   // プレイヤー最大体力の想定上限
        double dealt = 0.0;
        for (int shot = 0; shot < 50; shot++) {
            double applied = DefenseIgnoringDamagePolicy.cappedDamage(
                    10594.0, maxHealth, dealt, PER_HIT, PER_CAST);
            dealt += applied;
        }
        assertEquals(maxHealth, dealt, 1e-9,
                "1詠唱の累計は最大体力の100%で止まる(既定)。split=4の斉射を何度撃っても超えない");
        assertTrue(dealt / (maxHealth * PER_HIT) >= 4.0 - 1e-9,
                "最低4発かかる = 回復/離脱/召喚体破壊の余地が構造的に残る");
    }

    @Test
    @DisplayName("最大体力が読めない(0以下)なら割合上限は掛けない(安全側フォールバック)")
    void unknownMaxHealthFallsOpen() {
        assertEquals(9.0, DefenseIgnoringDamagePolicy.cappedDamage(9.0, 0.0, 0.0, PER_HIT, PER_CAST), 1e-9);
        assertEquals(9.0, DefenseIgnoringDamagePolicy.cappedDamage(9.0, -1.0, 0.0, PER_HIT, PER_CAST), 1e-9);
    }

    @Test
    @DisplayName("上限を0以下にすると当該上限は無効(運用者が外せる逃げ道)")
    void zeroPercentDisablesThatCap() {
        assertEquals(10594.0, DefenseIgnoringDamagePolicy.cappedDamage(10594.0, 20.0, 0.0, 0.0, 0.0), 1e-9);
        // 1発上限だけ無効化しても累計上限は生きる。
        assertEquals(20.0, DefenseIgnoringDamagePolicy.cappedDamage(10594.0, 20.0, 0.0, 0.0, PER_CAST), 1e-9);
    }

    @Test
    @DisplayName("非有限/非正のダメージは0(回復側へ倒さない)")
    void nonPositiveDamageIsZero() {
        assertEquals(0.0, DefenseIgnoringDamagePolicy.cappedDamage(0.0, 20.0, 0.0, PER_HIT, PER_CAST), 0.0);
        assertEquals(0.0, DefenseIgnoringDamagePolicy.cappedDamage(-5.0, 20.0, 0.0, PER_HIT, PER_CAST), 0.0);
        assertEquals(0.0, DefenseIgnoringDamagePolicy.cappedDamage(Double.NaN, 20.0, 0.0, PER_HIT, PER_CAST), 0.0);
    }

    // ---- 斉射の判断（2026-07-31 F5 指摘3） ----

    @Test
    @DisplayName("撃てる対象が1体でもいれば発射する")
    void firesWhileAnyTargetHasBudgetLeft() {
        assertEquals(DefenseIgnoringDamagePolicy.VolleyOutcome.FIRE,
                DefenseIgnoringDamagePolicy.volleyOutcome(3, 2));
        assertEquals(DefenseIgnoringDamagePolicy.VolleyOutcome.FIRE,
                DefenseIgnoringDamagePolicy.volleyOutcome(1, 1));
    }

    @Test
    @DisplayName("索敵範囲に対象がいないだけなら召喚は維持する(設置技として成立させる)")
    void keepsSummonAliveWhenNobodyIsInRange() {
        assertEquals(DefenseIgnoringDamagePolicy.VolleyOutcome.IDLE,
                DefenseIgnoringDamagePolicy.volleyOutcome(0, 0));
    }

    @Test
    @DisplayName("範囲内の対象が全員累計上限に達したら召喚を終了する(残り時間を無言で捨てない)")
    void endsSummonWhenEveryTargetInRangeIsExhausted() {
        assertEquals(DefenseIgnoringDamagePolicy.VolleyOutcome.END_SUMMON,
                DefenseIgnoringDamagePolicy.volleyOutcome(1, 0));
        assertEquals(DefenseIgnoringDamagePolicy.VolleyOutcome.END_SUMMON,
                DefenseIgnoringDamagePolicy.volleyOutcome(5, 0));
    }

    @Test
    @DisplayName("回復する対象は召喚に対して無敵にならない(F5 指摘3 の失敗シナリオ)")
    void regeneratingTargetCannotMakeTheSummonPermanentlyUseless() {
        // 出荷値: 生10.0 / プレイヤー最大体力33 → 1発上限 8.25、1詠唱累計上限 33.0。
        double raw = 10.0;
        double maxHealth = 33.0;
        double dealt = 0.0;
        int shots = 0;
        while (true) {
            double applied = DefenseIgnoringDamagePolicy.cappedDamage(
                    raw, maxHealth, dealt, PER_HIT, PER_CAST);
            if (applied <= 0.0) {
                break;
            }
            dealt += applied;
            shots++;
            assertTrue(shots < 100, "上限が効かず無限に撃てている");
        }
        assertEquals(maxHealth, dealt, 1e-9, "1詠唱では最大体力ぶんまでしか削れない");

        // ここで対象が回復して生き残った状態 = 旧実装なら最近接スロットを占有し続け、
        // 召喚の残り時間ぜんぶが不発だった。現在は「全員上限」として召喚を終了する判断になる。
        assertEquals(DefenseIgnoringDamagePolicy.VolleyOutcome.END_SUMMON,
                DefenseIgnoringDamagePolicy.volleyOutcome(1, 0),
                "上限を使い切った対象しかいないなら召喚を終了する(=詠唱し直せば再度削れる)");

        // 詠唱し直し(累計リセット)で必ずダメージが通る = 恒久的な無敵は成立しない。
        assertTrue(DefenseIgnoringDamagePolicy.cappedDamage(raw, maxHealth, 0.0, PER_HIT, PER_CAST) > 0.0,
                "詠唱をまたげば累計はリセットされるので、対象が恒久的に無敵になることはない");
    }
}
