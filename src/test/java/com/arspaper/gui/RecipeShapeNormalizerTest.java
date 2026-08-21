package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * レシピ画面へ渡す shape / 素材の並びを固定する
 * (2026-08-21 実サーバ報告「コア系アイテムなど一部アイテムがレシピからクラフト素材を
 * クリックして見た時に素材が表示されない」)。
 *
 * <p><b>この壊れ方の質が悪い理由</b>: レシピ画面は shape が空でなければ必ず格子描画へ倒し、
 * 引けなかったマスを<b>黙って空欄</b>にする。記号が全滅した shape が 1 本混ざるだけで
 * 「素材欄が空のレシピ」が出来上がり、例外もログも出ないので
 * <b>レシピが存在しないのと見分けが付かない</b>。
 */
class RecipeShapeNormalizerTest {

    /** 実際に壊れていたジュエリーコアの形。shape は小文字、配列形式の素材キーは大文字。 */
    private static Map<String, String> coreJewelryIngredients() {
        Map<String, String> ing = new LinkedHashMap<>();
        String[] tokens = {
                "custom:copper_block_3x", "custom:iron_block_3x", "custom:gold_block_3x",
                "custom:lapis_block_3x", "custom:lapis_block_3x", "custom:redstone_block_3x",
                "custom:quartz_block_3x", "custom:diamond_block_3x", "custom:emerald_block_3x",
        };
        for (int i = 0; i < tokens.length; i++) {
            ing.put(String.valueOf((char) ('A' + i)), tokens[i]);
        }
        return ing;
    }

    @Test
    @DisplayName("記号が1つも引けない shape は捨てる(コア系3種が踏んだ形そのもの)")
    void aShapeThatMatchesNoIngredientIsDropped() {
        List<String> shape = List.of("abc", "def", "ghi");

        assertTrue(RecipeShapeNormalizer.usableShape(shape, coreJewelryIngredients()).isEmpty(),
                "小文字 shape × 大文字キーでは1マスも引けない。"
                        + "この shape を残すと格子9マスが全部空欄になり、素材が1つも表示されない");
    }

    @Test
    @DisplayName("1マスでも引ける shape はそのまま残す(空白マスのある shaped を壊さない)")
    void aPartiallyMatchingShapeIsKept() {
        // グランドコアと同じ「一部のマスだけ埋まる」形。b は引けるので shaped のまま。
        List<String> shape = List.of("b b", "   ", " b ");
        Map<String, String> ing = Map.of("b", "custom:netherrack_4x");

        assertSame(shape, RecipeShapeNormalizer.usableShape(shape, ing),
                "引ける記号が1つでもあるなら shaped のまま扱う。"
                        + "ここを「全マス引けたら」にすると、空白入りのレシピが軒並み shapeless へ倒れて形が消える");
    }

    @Test
    @DisplayName("素材が空なら shape も捨てる(格子だけ描いて中身ゼロにしない)")
    void anEmptyIngredientMapDropsTheShape() {
        assertTrue(RecipeShapeNormalizer.usableShape(List.of("aaa"), Map.of()).isEmpty());
        assertTrue(RecipeShapeNormalizer.usableShape(List.of("aaa"), null).isEmpty());
        assertTrue(RecipeShapeNormalizer.usableShape(null, Map.of("a", "STICK")).isEmpty());
        assertTrue(RecipeShapeNormalizer.usableShape(List.of(), Map.of("a", "STICK")).isEmpty());
    }

    @Test
    @DisplayName("shapeless の並びは記号キー昇順 = config に書いた順")
    void shapelessIngredientsFollowConfigOrder() {
        // わざと Map の走査順が config 順と違う入れ方をする。
        Map<String, String> scrambled = new LinkedHashMap<>();
        scrambled.put("C", "third");
        scrambled.put("A", "first");
        scrambled.put("B", "second");

        assertEquals(List.of("first", "second", "third"),
                RecipeShapeNormalizer.orderedIngredients(scrambled),
                "HashMap の走査順そのままだと9個の素材が毎回でたらめな位置に並ぶ。"
                        + "誤りには見えないので報告されないまま「読めない画面」になる");
    }

    @Test
    @DisplayName("並べ替えても素材は1つも落とさない(同じ素材が2回出る形を含む)")
    void orderingKeepsEveryIngredientIncludingDuplicates() {
        List<String> ordered = RecipeShapeNormalizer.orderedIngredients(coreJewelryIngredients());

        assertEquals(9, ordered.size(), "ジュエリーコアは9素材。重複(ラピス×2)を潰してはいけない");
        assertEquals("custom:copper_block_3x", ordered.get(0));
        assertEquals("custom:emerald_block_3x", ordered.get(8));
        assertEquals(2, ordered.stream().filter("custom:lapis_block_3x"::equals).count());
    }

    @Test
    @DisplayName("素材が空なら並びも空(呼び出し側でnull分岐を増やさない)")
    void orderingHandlesEmptyInput() {
        assertTrue(RecipeShapeNormalizer.orderedIngredients(null).isEmpty());
        assertTrue(RecipeShapeNormalizer.orderedIngredients(Map.of()).isEmpty());
    }
}
