package com.arspaper.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SourceAutoConsume} の消費計画ロジックのテスト。
 *
 * <p>このフォークのテスト基盤は Bukkit ランタイム/MockBukkit/Mockito を持たない
 * (see {@code RecipeManagerReversibleTest} — 純粋ロジックのみをテストするスタイル)ため、
 * {@code Player}/{@code Inventory} を要する {@link SourceAutoConsume#tryConvert} 全体の
 * 統合テストは行わず、Bukkit非依存に切り出した {@link SourceAutoConsume#computeConsumptionPlan}
 * (実際の消費数決定ロジック)を対象にする。{@code player == null} ガードのみ、実引数無しで
 * {@code tryConvert} を直接呼んで検証する。
 */
class SourceAutoConsumeTest {

    private static final int SOURCE_BERRY_MANA = 25;

    @Test
    void deficitFullyCoveredByBerries_returnsPlanConsumingExactBerriesNeeded() {
        // config: {source_berry: 25}. プレイヤーが1スロットに5個のsource_berryを保有、不足マナ60。
        // 60 / 25 = 2.4 -> ceil = 3個消費すれば60マナ以上を賄える(all-or-nothing、超過分は破棄)。
        var berries = new SourceAutoConsume.MatchedStack(0, SOURCE_BERRY_MANA, 5);
        List<SourceAutoConsume.ConsumeEntry> plan =
            SourceAutoConsume.computeConsumptionPlan(List.of(berries), 60);

        assertTrue(plan != null, "60マナは5個(125マナ分)のベリーで賄えるため計画がnullであってはならない");
        assertEquals(1, plan.size());
        assertEquals(0, plan.get(0).slotIndex());
        assertEquals(3, plan.get(0).consumeCount(), "3個(75マナ) >= 60マナ不足を満たす最小消費数");
    }

    @Test
    void deficitExactlyDivisible_consumesExactCount() {
        // 不足50マナ、25マナ/個 -> ちょうど2個。
        var berries = new SourceAutoConsume.MatchedStack(2, SOURCE_BERRY_MANA, 10);
        List<SourceAutoConsume.ConsumeEntry> plan =
            SourceAutoConsume.computeConsumptionPlan(List.of(berries), 50);

        assertEquals(1, plan.size());
        assertEquals(2, plan.get(0).consumeCount());
    }

    @Test
    void deficitNotCovered_tooFewBerries_returnsNullAndConsumesNothing() {
        // 保有2個(50マナ分)では不足マナ60を賄えない -> 消費計画自体がnull(=一切消費しない)。
        var berries = new SourceAutoConsume.MatchedStack(0, SOURCE_BERRY_MANA, 2);
        List<SourceAutoConsume.ConsumeEntry> plan =
            SourceAutoConsume.computeConsumptionPlan(List.of(berries), 60);

        assertNull(plan, "合計変換可能マナ(50) < 不足マナ(60)のときは消費計画を作らない(all-or-nothing)");
    }

    @Test
    void deficitNotCovered_noMatchingItems_returnsNull() {
        List<SourceAutoConsume.ConsumeEntry> plan =
            SourceAutoConsume.computeConsumptionPlan(List.of(), 25);

        assertNull(plan, "対象アイテムが1つも無ければ消費計画は作られない");
    }

    @Test
    void multipleStacks_consumesFromEarlierStackFirstThenSpillsToNext() {
        // スロット0に1個(25マナ)、スロット5に3個(75マナ)。不足40マナ -> スロット0を1個使い切り、
        // 残り15マナ分をスロット5からceil(15/25)=1個で賄う。
        var stack0 = new SourceAutoConsume.MatchedStack(0, SOURCE_BERRY_MANA, 1);
        var stack5 = new SourceAutoConsume.MatchedStack(5, SOURCE_BERRY_MANA, 3);
        List<SourceAutoConsume.ConsumeEntry> plan =
            SourceAutoConsume.computeConsumptionPlan(List.of(stack0, stack5), 40);

        assertEquals(2, plan.size());
        assertEquals(0, plan.get(0).slotIndex());
        assertEquals(1, plan.get(0).consumeCount());
        assertEquals(5, plan.get(1).slotIndex());
        assertEquals(1, plan.get(1).consumeCount());
    }

    @Test
    void tryConvert_nullPlayer_returnsZeroWithoutSideEffects() {
        // player==nullガード(要件①): Playerが特定できない経路は常に0(未変換)を返す。
        int converted = SourceAutoConsume.tryConvert(null, 25, java.util.Map.of("source_berry", 25), 10);
        assertEquals(0, converted);
    }

    @Test
    void tryConvert_nonPositiveDeficit_returnsZero() {
        // deficitMana<=0ガード: 不足が無ければ変換不要で常に0。
        int converted = SourceAutoConsume.tryConvert(null, 0, java.util.Map.of("source_berry", 25), 10);
        assertEquals(0, converted);
    }

    // ---- 2026-08-14 追加: 自動消費のクールタイム(秒) ----
    // ノード説明「100マナ/10CT」は当初からあったのにCT判定が一度も実装されておらず、
    // マナ不足のたびに無制限で変換できていた。判定は純粋関数へ切り出してここで固定する。

    @Test
    void isOnCooldown_neverConverted_isNotOnCooldown() {
        // 一度も変換していない(lastMillis==null)なら、CTがいくつでも即使える。
        assertFalse(SourceAutoConsume.isOnCooldown(null, 1_000_000L, 10));
    }

    @Test
    void isOnCooldown_zeroOrNegativeCooldown_isNeverOnCooldown() {
        // CT=0(および負値)は従来挙動(CT無し)。config で 0 を書いた運用を壊さない。
        assertFalse(SourceAutoConsume.isOnCooldown(1_000_000L, 1_000_000L, 0));
        assertFalse(SourceAutoConsume.isOnCooldown(1_000_000L, 1_000_000L, -5));
    }

    @Test
    void isOnCooldown_withinCooldown_blocksAndBoundaryIsInclusiveOfElapsed() {
        long last = 1_000_000L;
        assertTrue(SourceAutoConsume.isOnCooldown(last, last, 10), "同一時刻はCT中");
        assertTrue(SourceAutoConsume.isOnCooldown(last, last + 9_999L, 10), "9.999秒はまだCT中");
        assertFalse(SourceAutoConsume.isOnCooldown(last, last + 10_000L, 10), "ちょうど10秒で解ける");
        assertFalse(SourceAutoConsume.isOnCooldown(last, last + 60_000L, 10));
    }

    @Test
    void isOnCooldown_clockWentBackwards_staysOnCooldown() {
        // now < last(時刻巻き戻し)で経過時間が負になる。ここを「経過が大きい」と誤読すると
        // CTが無制限に素通りするので、CT中へ倒す方が安全。
        assertTrue(SourceAutoConsume.isOnCooldown(1_000_000L, 900_000L, 10));
    }
}
