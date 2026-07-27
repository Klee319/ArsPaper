package com.arspaper.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CustomIngredientCraftGuardListener} の shape 正規化(外接矩形の切り出しと鏡像)の単体テスト。
 *
 * <p>2026-07-24 レビューのM指摘「fork の新ガードに単体テストが皆無」に対する分。ガード本体は
 * {@code PrepareItemCraftEvent}/{@code CrafterCraftEvent} を受け取るためこのフォークのテスト環境
 * (MockBukkit 無し・paper-api をクラスパスに置いただけ)では直接叩けないが、over-match と代用素材の
 * 判定精度を実質決めているのは shape 正規化なので、そこを純関数として検証する。
 *
 * <p>ここが壊れたときの症状は「レシピ帳には出るのにクラフトできない」または逆に
 * 「別tierのレシピが素通りする」で、どちらも config 側の書き方を疑ってしまい原因に辿り着きにくい。
 */
class CustomIngredientCraftGuardShapeTest {

    @Test
    void boundingBoxTrimsSurroundingBlankRowsAndColumns() {
        // 3x3 の左上2x2にだけ材料がある形 → 2x2 に切り詰める(置く位置をずらしても同じレシピ)。
        assertEquals(List.of("ii", "ii"),
                CustomIngredientCraftGuardListener.boundingBox(List.of("ii ", "ii ", "   ")));
        assertEquals(List.of("ii", "ii"),
                CustomIngredientCraftGuardListener.boundingBox(List.of("   ", " ii", " ii")));
    }

    @Test
    void boundingBoxKeepsInteriorBlanks() {
        // 内側の空白は形の一部。ここを詰めると「ドーナツ型」と「べた塗り」が同一視されてしまう。
        assertEquals(List.of("iii", "i i", "iii"),
                CustomIngredientCraftGuardListener.boundingBox(List.of("iii", "i i", "iii")));
    }

    @Test
    void boundingBoxPadsShortRowsToTheBoxWidth() {
        // yml の shape は行ごとに長さが違うことがある(末尾スペースを省いて書ける)。
        assertEquals(List.of("ii", "i "),
                CustomIngredientCraftGuardListener.boundingBox(List.of("ii", "i")));
    }

    @Test
    void boundingBoxOfEmptyShapeIsEmpty() {
        assertEquals(List.of(), CustomIngredientCraftGuardListener.boundingBox(List.of("   ", "   ")));
        assertEquals(List.of(), CustomIngredientCraftGuardListener.boundingBox(List.of()));
    }

    @Test
    void mirrorReversesEachRowIndependently() {
        assertEquals(List.of("ii ", " si"),
                CustomIngredientCraftGuardListener.mirror(List.of(" ii", "is ")));
    }

    @Test
    void mirrorTwiceIsIdentity() {
        List<String> shape = List.of(" ii", "is ", "s  ");
        assertEquals(shape, CustomIngredientCraftGuardListener.mirror(
                CustomIngredientCraftGuardListener.mirror(shape)));
    }
}
