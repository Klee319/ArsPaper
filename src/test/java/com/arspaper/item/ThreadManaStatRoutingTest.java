package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「スレッドの item-stats に書いたマナ系ステが無言で死ぬ」の回帰ガード (2026-08-03)。
 *
 * <p>要件「一部スレッドが名称と効果が一致していない(マナ増幅のスレッドなど) →
 * 名称に合ったステータスに重きを置く」を満たすには、まず
 * <b>{@code stats/item-stats.yml} のスレッド行に書いたマナ系が実際に効く</b>必要がある。
 * 変更前は {@code ArmorManaListener#collectThreadsInto} が
 * {@code resolveThreadStats} の戻り値をまるごと {@code totals.combatStats}
 * (= TF の addon 戦闘チャネル)へ流していたため、マナの消費側が誰も読まず
 * <b>エラーも警告も出ないまま何も起きなかった</b>(経路の詳細は
 * {@link ThreadManaStatRouting} の javadoc)。
 *
 * <p>仕分け本体は Bukkit を要らない純関数なので<b>本当に実行して</b>検証する。
 * 「その仕分けが実際に配線されていること」だけは、このフォークに Bukkit ランタイムが無い
 * ({@link ThreadHandheldWiringTest} の javadoc)ためソースの静的走査で固定する。
 */
class ThreadManaStatRoutingTest {

    private static Map<String, Double> stats(Object... pairs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return out;
    }

    @Test
    @DisplayName("マナ系5キーはマナカウンタへ移り、元のマップからは消える")
    void manaKeysAreRoutedAndRemoved() {
        Map<String, Double> input = stats(
                "mana_bonus", 40,
                "mana_regen", 3,
                "hit_mana_recovery", 5,
                "damage_mana_recovery", 7,
                "mana_cost_reduction_percent", 0.06);

        ThreadManaStatRouting.Deltas deltas = ThreadManaStatRouting.extract(input);

        assertEquals(40, deltas.manaBonus(), "最大マナ加算が拾われていない");
        assertEquals(3, deltas.manaRegen(), "マナ回復速度が拾われていない");
        assertEquals(5, deltas.hitManaRecovery(), "被弾時マナ回復が拾われていない");
        assertEquals(7, deltas.damageManaRecovery(), "与ダメージ時マナ回復が拾われていない");
        // item-stats の割合キーは分数(0.06 = 6%)。スレッドのカウンタは threads.yml と同じ整数%。
        assertEquals(6, deltas.costReductionPercent(),
                "コスト軽減の単位変換(分数→整数%)が壊れている。1/100 の効果に化ける。");

        assertTrue(input.isEmpty(),
                "マナ系キーが元のマップに残っている。残すと同じ値が addon 戦闘チャネルにも載り、"
                        + "TF 側が将来 addon から同キーを読み始めたときに二重取りになる: " + input);
    }

    @Test
    @DisplayName("マナ系以外のステは1つも触らない(戦闘チャネルへそのまま流す)")
    void nonManaKeysAreLeftUntouched() {
        Map<String, Double> input = stats(
                "mana_bonus", 25,
                "attack_damage", 4.5,
                "spell_power_percent", 0.12,
                "mining_fortune", 2,
                // 受け皿の無いマナ系2キーは意図的に素通り(単位が混ざるため。理由は routing の javadoc)。
                "mana_cost_reduction_flat", 3);

        ThreadManaStatRouting.Deltas deltas = ThreadManaStatRouting.extract(input);

        assertEquals(25, deltas.manaBonus());
        assertEquals(stats(
                        "attack_damage", 4.5,
                        "spell_power_percent", 0.12,
                        "mining_fortune", 2,
                        "mana_cost_reduction_flat", 3),
                input,
                "マナ系以外を巻き添えで消している(スレッドの厳選ステが丸ごと消える)");
    }

    @Test
    @DisplayName("空・null・対象キー無しは全0(fail-open。スレッドのマナ機能を止めない)")
    void missingInputFailsOpen() {
        ThreadManaStatRouting.Deltas fromNull = ThreadManaStatRouting.extract(null);
        assertEquals(0, fromNull.manaBonus());
        assertEquals(0, fromNull.costReductionPercent());

        assertEquals(0, ThreadManaStatRouting.extract(new LinkedHashMap<>()).manaRegen());

        Map<String, Double> unrelated = stats("attack_damage", 4.0);
        ThreadManaStatRouting.Deltas deltas = ThreadManaStatRouting.extract(unrelated);
        assertEquals(0, deltas.hitManaRecovery());
        assertEquals(1, unrelated.size(), "無関係なステを消している");
    }

    @Test
    @DisplayName("非有限値(NaN/Inf)は0扱いにしつつキーは取り除く")
    void nonFiniteValuesAreDroppedNotPropagated() {
        Map<String, Double> input = stats(
                "mana_bonus", Double.NaN,
                "mana_regen", Double.POSITIVE_INFINITY);

        ThreadManaStatRouting.Deltas deltas = ThreadManaStatRouting.extract(input);

        assertEquals(0, deltas.manaBonus(), "NaN が (int) キャストで巨大値/0以外に化けている");
        assertEquals(0, deltas.manaRegen(), "Inf が Integer.MAX_VALUE のマナ回復に化けている");
        assertTrue(input.isEmpty(), "非有限値のキーが戦闘チャネルへ流れ残っている");
    }

    @Test
    @DisplayName("四捨五入は端数を丸めるだけ(小数の厳選値が切り捨てで0にならない)")
    void fractionalRollsRoundInsteadOfTruncating() {
        // 厳選(random{min,max} + rollSeed)は小数を返す。切り捨てだと 0.6 → 0 で「効かない」に戻る。
        Map<String, Double> input = stats("mana_regen", 0.6, "mana_bonus", 12.4);
        ThreadManaStatRouting.Deltas deltas = ThreadManaStatRouting.extract(input);
        assertEquals(1, deltas.manaRegen());
        assertEquals(12, deltas.manaBonus());
    }

    @Test
    @DisplayName("ROUTED_KEYS は extract が実際に取り除くキーと一致している(外部からの参照用)")
    void routedKeySetMatchesTheImplementation() {
        Map<String, Double> input = new LinkedHashMap<>();
        for (String key : ThreadManaStatRouting.ROUTED_KEYS) {
            input.put(key, 1.0);
        }
        assertEquals(5, input.size(), "ROUTED_KEYS の件数が変わっている");
        ThreadManaStatRouting.extract(input);
        assertTrue(input.isEmpty(),
                "ROUTED_KEYS に載っているのに extract が取り除かないキーがある: " + input);
    }

    // --- 配線(ソース走査。Bukkit ランタイムが無いためリスナー本体は実行できない) ---

    @Test
    @DisplayName("collectThreadsInto が仕分けを通してからマナカウンタへ足している")
    void collectThreadsIntoRoutesManaStats() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "ArmorManaListener.java");
        assertTrue(Files.exists(path),
                "ソースが見つからない(パス変更時はこのテストも更新): " + path.toAbsolutePath());
        String source = Files.readString(path);

        assertTrue(source.contains("ThreadManaStatRouting.extract(threadStats)"),
                "装着スレッドの item-stats を仕分けせずに combatStats へ流している。"
                        + "この状態では item-stats.yml のスレッド行に書いたマナ系が無言で死ぬ"
                        + "(『マナ増幅のスレッドにマナを厳選させる』が飾りになる)。");

        for (String assignment : new String[] {
                "totals.threadMana += mana.manaBonus();",
                "totals.threadRegen += mana.manaRegen();",
                "totals.hitRecovery += mana.hitManaRecovery();",
                "totals.damageRecovery += mana.damageManaRecovery();",
                "totals.costReduction += mana.costReductionPercent();"}) {
            assertTrue(source.contains(assignment),
                    "仕分け結果の加算が欠けている: " + assignment);
        }

        // 仕分け → 残りを combatStats、の順序であること。逆だとマナ系が両方のチャネルへ二重に載る。
        int extract = source.indexOf("ThreadManaStatRouting.extract(threadStats)");
        int merge = source.indexOf("threadStats.forEach((key, value) -> totals.combatStats.merge(");
        assertTrue(merge > extract,
                "combatStats への合流が仕分けより前にある(マナ系が戦闘チャネルにも残る)");

        // 戻り値の可変性を仮定していないこと(TF 側が unmodifiable を返すと remove で落ちる)。
        assertTrue(source.contains("new LinkedHashMap<>(\n")
                        || source.contains("Map<String, Double> threadStats = new LinkedHashMap<>("),
                "resolveThreadStats の戻り値をそのまま破壊的に触っている");

        // 旧経路(戻り値を直接 forEach で combatStats へ流す形)が復活していないこと。
        assertFalse(source.contains("resolveThreadStats(thread.getBaseMaterial(), thread.getCustomModelData(),\n"
                        + "                                equipped.quality(), equipped.rollSeed())\n"
                        + "                        .forEach("),
                "仕分けを通さない旧経路が残っている");
    }
}
