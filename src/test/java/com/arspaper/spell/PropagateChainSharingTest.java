package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>「投射 + 伝播」が炸裂を含む全フォームの上位互換になっていた問題（2026-08-19）の回帰ガード。</b>
 *
 * <p><b>真因（機構レベル）</b>: 伝播チェーンの除外集合が {@code resolveGroupsOnEntity} の
 * <b>ローカル変数</b>で、その回の対象と術者しか入っていなかった。炸裂のようにフォーム側が
 * 複数の対象へ独立に resolve すると、A のチェーンが「これから直撃させる B・C」を選び、
 * 実ダメージはバニラの無敵時間に吸われる ── つまり <b>伝播に払ったマナが丸ごと消えていた</b>。
 * さらにチェーン検索半径 8.0 が炸裂の最大半径より広く、炸裂は完全な下位互換だった。
 *
 * <p><b>直し方（ユーザー判断 2026-08-19: 案1 + 案3 + 上限 + 減衰）</b>
 * <ul>
 *   <li>案1: ヒット済み集合を<b>詠唱単位</b>で共有し、チェーンは未ヒットの相手だけを掴む。</li>
 *   <li>案3: 誰にも当たらなかった炸裂は<b>炸裂地点そのもの</b>を伝播の起点にできる
 *       （投射は外した弾から連鎖しない ＝ 炸裂 + 伝播だけの利点）。</li>
 *   <li>上限: 未ヒットを探し直す方式は密集地で候補が尽きるまで伸びるので
 *       {@code max-chains-per-cast} が必須。</li>
 *   <li>減衰: ホップごとに威力を落とす（{@code damage-falloff-per-hop} / {@code min-damage-rate}）。</li>
 * </ul>
 */
class PropagateChainSharingTest {

    private static final Path GLYPHS_YML = Path.of("src", "main", "resources", "glyphs.yml");
    private static final Path SPELL_CONTEXT = Path.of("src", "main", "java", "com", "arspaper",
            "spell", "SpellContext.java");
    private static final Path BURST_FORM = Path.of("src", "main", "java", "com", "arspaper",
            "spell", "form", "BurstForm.java");

    // ---------------------------------------------------------------- 算術（挙動で固定）

    @Test
    @DisplayName("上限に達したらチェーン数は 0 —— 密集地で候補が尽きるまで伸びるのを止める唯一の歯止め")
    void budgetStopsAtTheCastWideCap() {
        assertEquals(8, SpellPropagateMath.budget(8, 20, 0));
        assertEquals(4, SpellPropagateMath.budget(8, 20, 16), "残り枠 4 のときは希望 8 でも 4 まで");
        assertEquals(0, SpellPropagateMath.budget(8, 20, 20));
        assertEquals(0, SpellPropagateMath.budget(8, 20, 999), "使い過ぎても負値を返さない");
        assertEquals(0, SpellPropagateMath.budget(0, 20, 0), "伝播なしなら 0");
    }

    @Test
    @DisplayName("威力はホップごとに減衰し min-damage-rate で下げ止まる")
    void hopRateDecaysThenFloors() {
        assertEquals(0.85, SpellPropagateMath.hopRate(0, 0.15, 0.4), 1e-9);
        assertEquals(0.70, SpellPropagateMath.hopRate(1, 0.15, 0.4), 1e-9);
        assertEquals(0.55, SpellPropagateMath.hopRate(2, 0.15, 0.4), 1e-9);
        assertEquals(0.40, SpellPropagateMath.hopRate(3, 0.15, 0.4), 1e-9);
        assertEquals(0.40, SpellPropagateMath.hopRate(50, 0.15, 0.4), 1e-9,
                "下げ止まりが無いと遠方のチェーンが回復（負ダメージ）に化ける");
        assertTrue(SpellPropagateMath.hopRate(0, 0.15, 0.4) < 1.0,
                "1ホップ目から減衰していないと『減衰を入れた』ことにならない");
    }

    // ---------------------------------------------------------------- config（yml でしか表せない）

    @Test
    @DisplayName("propagate.params に 4 つの調整キーが揃っている")
    void propagateParamsAreConfigurable() throws IOException {
        String block = glyphBlock(Files.readString(GLYPHS_YML, StandardCharsets.UTF_8), "propagate");
        for (String key : new String[]{"chain-radius", "max-chains-per-cast",
                "damage-falloff-per-hop", "min-damage-rate"}) {
            assertTrue(block.contains(key + ":"),
                    "glyphs.yml の propagate.params に " + key + " が無い。"
                            + "Java 側のデフォルトへ落ちるので運用で調整できなくなる");
        }
    }

    // ---------------------------------------------------------------- 順序（型でも算術でも表せない不変条件）

    @Test
    @DisplayName("炸裂は【resolve より先に】対象全員をヒット済みへ登録する —— 逆順だと伝播が自分の直撃対象を掴む")
    void burstRegistersAllTargetsBeforeResolving() throws IOException {
        String src = Files.readString(BURST_FORM, StandardCharsets.UTF_8);
        int mark = src.indexOf("markCastHits(");
        int resolve = src.indexOf("resolveOnEntity(");
        assertTrue(mark >= 0, "BurstForm が markCastHits を呼んでいない。"
                + "1体目のチェーンが2体目・3体目を選び、無敵時間に吸われて伝播ぶんのマナが消える");
        assertTrue(resolve >= 0, "BurstForm が resolveOnEntity を呼んでいない");
        assertTrue(mark < resolve, "markCastHits が resolveOnEntity より後にある。"
                + "登録が後回しだと 1 体目のチェーンが『これから直撃させる相手』を選んでしまう");
    }

    @Test
    @DisplayName("誰にも当たらなかった炸裂は炸裂地点を伝播の起点にする（案3）")
    void burstWithoutDirectHitStillPropagates() throws IOException {
        String src = Files.readString(BURST_FORM, StandardCharsets.UTF_8);
        assertTrue(src.contains("resolvePropagateFromLocation("),
                "空中炸裂から伝播が発動しない。これが無いと『外しても芋づる』が成立せず、"
                        + "炸裂 + 伝播に投射 + 伝播より優れた点が一つも残らない");
    }

    @Test
    @DisplayName("チェーン候補は詠唱単位のヒット済み集合で絞る —— ローカル集合へ戻すと元の重複に戻る")
    void chainFiltersAgainstTheCastWideHitSet() throws IOException {
        String src = Files.readString(SPELL_CONTEXT, StandardCharsets.UTF_8);
        assertTrue(src.contains("castState.hitEntities.contains("),
                "チェーン候補が castState.hitEntities で絞られていない");
        assertTrue(src.contains("private final CastState castState;"),
                "詠唱単位の共有状態が無い。copy() 間で参照を共有していないと炸裂の各対象が別々の集合を持つ");
    }

    // ---------------------------------------------------------------- helper

    /** {@code glyphs.yml} から 1 グリフ分のブロックを切り出す（次の同インデントのキーまで）。 */
    private static String glyphBlock(String yml, String glyph) {
        int start = yml.indexOf("\n  " + glyph + ":");
        assertTrue(start >= 0, "glyphs.yml に " + glyph + " が無い");
        int next = yml.indexOf("\n  ", yml.indexOf('\n', start + 1));
        while (next >= 0 && yml.startsWith("\n    ", next)) {
            next = yml.indexOf("\n  ", next + 1);
        }
        return next < 0 ? yml.substring(start) : yml.substring(start, next);
    }
}
