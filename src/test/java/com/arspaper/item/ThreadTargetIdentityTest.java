package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * スレッド装着 GUI の対象同一性({@link ThreadTargetIdentity})の判定固定 —— 2026-07-31 F3 指摘5。
 *
 * <p>守りたい事故は「GUI 表示中に対象装備を動かせるため、スレッドが消費されて装着されない」
 * (プレイヤーは成功したと思ってスレッドを失う)。装着直前にこの比較が
 * <b>不一致を返せること</b>が消費を止める条件そのものなので、境界を明示的に固定する。
 * {@code ItemStack} を要する {@code of()} はサーバ無しでは評価できないため、比較だけを検証する。
 */
class ThreadTargetIdentityTest {

    private static final ThreadTargetIdentity SWORD =
            new ThreadTargetIdentity("NETHERITE_SWORD", 400101, "blade_of_dawn");

    @Test
    @DisplayName("同じ material + CMD + ID なら一致する(正常系で毎回中断しない)")
    void sameTripleMatches() {
        assertTrue(SWORD.matches(new ThreadTargetIdentity("NETHERITE_SWORD", 400101, "blade_of_dawn")),
                "同じ品なのに不一致。これが起きると装着が常に中断され機能が死ぬ。");
    }

    @Test
    @DisplayName("スロットが空になっていたら不一致(=対象を持ち出した)")
    void emptySlotDoesNotMatch() {
        assertFalse(SWORD.matches(ThreadTargetIdentity.NONE),
                "対象がスロットから抜けているのに一致扱い。ここで通すとスレッドだけ消える。");
        assertFalse(ThreadTargetIdentity.NONE.matches(SWORD),
                "開いたときの対象が解決できていない場合も一致にしてはいけない");
        assertFalse(ThreadTargetIdentity.NONE.isPresent(), "NONE が『存在する』と答えている");
    }

    @Test
    @DisplayName("material が同じでも CMD が違えば不一致(TFカタログは同一 material に相乗り)")
    void differentCustomModelDataDoesNotMatch() {
        assertFalse(SWORD.matches(new ThreadTargetIdentity("NETHERITE_SWORD", 400102, "blade_of_dawn")),
                "CMD 違いを同一扱いしている。TF カタログの剣は同じ NETHERITE_SWORD に"
                        + "CMD で 31 本相乗りしているので、CMD を見ないと別物へ書き込む。");
        assertFalse(SWORD.matches(new ThreadTargetIdentity("NETHERITE_SWORD", null, "blade_of_dawn")),
                "CMD 未設定(素のバニラ品)と CMD 付きを同一扱いしている");
    }

    @Test
    @DisplayName("カスタムアイテムIDが違えば不一致")
    void differentItemIdDoesNotMatch() {
        assertFalse(SWORD.matches(new ThreadTargetIdentity("NETHERITE_SWORD", 400101, "other_blade")),
                "ID 違いを同一扱いしている");
        assertFalse(SWORD.matches(new ThreadTargetIdentity("NETHERITE_SWORD", 400101, null)),
                "ID 無しの品と同一扱いしている");
    }

    @Test
    @DisplayName("material が違えば不一致")
    void differentMaterialDoesNotMatch() {
        assertFalse(SWORD.matches(new ThreadTargetIdentity("DIAMOND_SWORD", 400101, "blade_of_dawn")));
    }

    @Test
    @DisplayName("null 相手は不一致(比較呼び出しで落ちない)")
    void nullDoesNotMatch() {
        assertFalse(SWORD.matches(null));
    }

    @Test
    @DisplayName("素のバニラ品どうし(CMD/ID なし)は一致できる")
    void plainVanillaItemsCanMatch() {
        ThreadTargetIdentity plain = new ThreadTargetIdentity("NETHERITE_SWORD", null, null);
        assertTrue(plain.matches(new ThreadTargetIdentity("NETHERITE_SWORD", null, null)),
                "素のバニラ装備で常に不一致になると、枠を持つバニラ装備の装着が一切できない");
    }
}
