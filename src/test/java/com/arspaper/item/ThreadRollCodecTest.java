package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * スレッド厳選の保存形式（{@link ThreadRoll}）の往復と、壊れた入力の扱いを固定する。
 *
 * <p>この文字列は<b>防具の PDC にスロット数ぶん配列で入る</b>ので、形式が変わると装着済みの
 * 厳選が全員ぶん読めなくなる。壊れた入力で例外を投げないこと（fail-open）も同じ理由で契約。
 */
class ThreadRollCodecTest {

    private static Map<String, Double> map(Object... pairs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return out;
    }

    @Test
    @DisplayName("主ステ+サブステ+レア度が往復でそのまま戻る")
    void roundTrip() {
        ThreadRoll roll = new ThreadRoll("epic",
                map("crit-damage", 0.0725),
                map("attack-power", 180.0, "dodge-chance", 0.0091));

        Optional<ThreadRoll> decoded = ThreadRoll.decode(roll.encode());

        assertTrue(decoded.isPresent());
        assertEquals("epic", decoded.get().rarityId());
        assertEquals(map("crit-damage", 0.0725), decoded.get().mainStat());
        assertEquals(map("attack-power", 180.0, "dodge-chance", 0.0091), decoded.get().subStats());
    }

    @Test
    @DisplayName("サブステ0本（外れ個体）も往復する")
    void roundTripWithoutSubs() {
        ThreadRoll roll = new ThreadRoll("common", map("penetration", 0.021), Map.of());

        Optional<ThreadRoll> decoded = ThreadRoll.decode(roll.encode());

        assertTrue(decoded.isPresent());
        assertTrue(decoded.get().subStats().isEmpty());
        assertEquals(map("penetration", 0.021), decoded.get().mainStat());
    }

    @Test
    @DisplayName("allStats は主ステとサブステを合わせて返す")
    void allStatsMerges() {
        ThreadRoll roll = new ThreadRoll("rare", map("crit-chance", 0.03), map("crit-damage", 0.02));

        assertEquals(map("crit-chance", 0.03, "crit-damage", 0.02), roll.allStats());
    }

    @Test
    @DisplayName("壊れた入力は例外を投げず「厳選なし」になる")
    void malformedIsEmpty() {
        assertTrue(ThreadRoll.decode(null).isEmpty());
        assertTrue(ThreadRoll.decode("").isEmpty());
        assertTrue(ThreadRoll.decode("epic").isEmpty(), "区切りが足りない");
        assertTrue(ThreadRoll.decode("|crit-chance=0.03|").isEmpty(), "レア度が空");
        assertTrue(ThreadRoll.statsOf("完全なゴミ").isEmpty());
        assertTrue(ThreadRoll.statsOf(null).isEmpty());
    }

    @Test
    @DisplayName("数値でないトークンだけを捨て、同じ文字列の他のステは活かす")
    void partialMalformedKeepsRest() {
        Map<String, Double> stats = ThreadRoll.statsOf("rare|crit-chance=0.03|attack-power=NaNではない文字;dodge-chance=0.01");

        assertEquals(map("crit-chance", 0.03, "dodge-chance", 0.01), stats);
    }

    @Test
    @DisplayName("値0のステは保存しない（no-op を PDC に残さない）")
    void zeroValuesAreDropped() {
        ThreadRoll roll = new ThreadRoll("common", map("crit-chance", 0.02), map("dodge-chance", 0.0));

        Optional<ThreadRoll> decoded = ThreadRoll.decode(roll.encode());

        assertTrue(decoded.isPresent());
        assertTrue(decoded.get().subStats().isEmpty());
    }
}
