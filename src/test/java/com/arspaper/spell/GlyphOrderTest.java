package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * グリフの並び替え規則を固定する
 * （2026-08-22 ユーザー報告「グリフレシピ、グリフ設定、グリフ解放で順番もそろえてほしい」）。
 *
 * <p>3画面が別々に並べていたのを {@link GlyphOrder} へ寄せた。採用した並びは
 * <b>「種類 → 登録順」で、ティアでは並べない</b> ── 登録順は手で意味づけされたグループ
 * （効果=攻撃系/移動系/…、増強=増幅⇔減衰 などの対）になっており、ティアで並べ替えると
 * それが崩れるため。
 *
 * <p>ここで検査するのは並べ替えの算数だけ。{@link SpellComponent} の生成にはプラグイン本体が
 * 要るので（このフォークのテスト基盤は MockBukkit を持たない）、
 * {@link GlyphOrder#superAfterBase} を文字列キーで直接叩く。
 */
class GlyphOrderTest {

    private static List<String> order(String... keys) {
        return GlyphOrder.superAfterBase(List.of(keys), key -> key);
    }

    @Test
    @DisplayName("超増強はベースの直後へ来る(登録順では末尾15個にまとまってしまう)")
    void superAugmentsFollowTheirBase() {
        assertEquals(
                List.of("amplify", "super_amplify", "dampen", "super_dampen", "aoe"),
                order("amplify", "dampen", "aoe", "super_amplify", "super_dampen"));
    }

    @Test
    @DisplayName("超増強以外は登録順のまま(ティアで並べ替えない)")
    void nonSuperKeepsRegistrationOrder() {
        // 実際の登録順。ティア順に並べると 収縮(T1) が 延伸(T2) の前へ跳ね、対が割れる。
        assertEquals(
                List.of("extend_time", "duration_down", "extend_reach", "shrink_reach"),
                order("extend_time", "duration_down", "extend_reach", "shrink_reach"));
    }

    @Test
    @DisplayName("ベースの居ない超増強も落とさない(末尾へ回す)")
    void orphanSuperAugmentIsStillListed() {
        assertEquals(
                List.of("amplify", "super_amplify", "super_ghost"),
                order("amplify", "super_amplify", "super_ghost"));
    }

    @Test
    @DisplayName("並べ替えで件数が変わらない(取りこぼし・二重表示をしない)")
    void nothingIsLostOrDuplicated() {
        List<String> input = List.of(
                "pierce", "split", "super_split", "super_pierce", "fortune", "super_fortune");
        List<String> sorted = order(input.toArray(new String[0]));

        assertEquals(input.size(), sorted.size(), "件数が変わっている");
        assertEquals(
                List.of("pierce", "super_pierce", "split", "super_split", "fortune", "super_fortune"),
                sorted);
    }
}
