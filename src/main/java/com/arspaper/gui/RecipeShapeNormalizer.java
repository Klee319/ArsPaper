package com.arspaper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * レシピ画面へ渡す前に「shape(パターン行)と ingredients(記号→素材)の対応」を整える。
 *
 * <p><b>なぜ要るのか</b>: レシピ画面は shape が空でなければ<b>必ず</b>格子描画へ倒し、
 * 各マスを {@code ingredientMap.get(記号)} で引く。引けなかったマスは黙って空欄になるので、
 * <b>1文字も引けない shape が来ると素材が1つも描画されない</b> ── 例外もログも出ない。
 *
 * <p>実際に踏んだのがこれ(2026-08-21 実サーバ報告「コア系アイテムなど一部アイテムが、
 * レシピからクラフト素材をクリックして見た時に素材が表示されない」)。
 * {@code materials.yml} のジュエリー／ベジタブル／ウッドの各コアは {@code type: shapeless} なのに
 * 旧版の名残で {@code shape: [abc, def, ghi]} が残っており、一方 shapeless の
 * {@code ingredients:} は<b>配列</b>なのでローダーが {@code A..I}(大文字)のキーを振る。
 * 登録側は shapeless なので shape を一切見ず<b>クラフト自体は成立する</b>。壊れるのは画面だけで、
 * しかも「素材欄が空」という、レシピが無いのと見分けの付かない形で壊れる。
 *
 * <p>だから直し方は「この3件のデータを直す」ではなく<b>「引けない shape は捨てて shapeless 扱いにする」</b>。
 * 記号の食い違いは今後も同じ形で入り込むし、入ってもエラーにならないため。
 * データ側の名残も併せて落としてあるが、それは再発防止ではなく後続を迷わせないための掃除。
 *
 * <p>Bukkit へ依存しないただの写像なので、フォークに Bukkit ランタイムが無くても単体で試験できる。
 */
final class RecipeShapeNormalizer {

    private RecipeShapeNormalizer() {
    }

    /**
     * 素材を1つも指していない shape を捨てる。
     *
     * <p>「1マスでも引ければそのまま」なのが肝 ── 部分的に空欄なだけの shaped レシピ
     * （空白マスのある L 字型など）を shapeless へ倒すと、今度は形の情報が失われる。
     * 全滅している時だけ、確実に劣化している格子表示より shapeless 表示のほうがましだと判断する。
     *
     * @return そのまま使える shape、または捨てるべきときは空リスト
     */
    static List<String> usableShape(List<String> shape, Map<String, String> ingredientMap) {
        if (shape == null || shape.isEmpty() || ingredientMap == null || ingredientMap.isEmpty()) {
            return List.of();
        }
        for (String row : shape) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol != ' ' && ingredientMap.get(String.valueOf(symbol)) != null) {
                    return shape;
                }
            }
        }
        return List.of();
    }

    /**
     * shapeless 表示の並び順。記号キーの昇順＝config に書いた順
     * （配列形式のローダーが {@code A, B, C, ...} を順に振るため）。
     *
     * <p>素の {@code Map#values()} は HashMap の走査順なので、9個の素材が毎回でたらめな位置に並ぶ。
     * 「上から順に材料を読む」という当たり前の読み方ができなくなるだけで、誤りには見えないので
     * 誰も報告してこない類の劣化になる。
     */
    static List<String> orderedIngredients(Map<String, String> ingredientMap) {
        if (ingredientMap == null || ingredientMap.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new TreeMap<>(ingredientMap).values());
    }
}
