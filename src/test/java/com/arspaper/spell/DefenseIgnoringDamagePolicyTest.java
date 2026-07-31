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
}
