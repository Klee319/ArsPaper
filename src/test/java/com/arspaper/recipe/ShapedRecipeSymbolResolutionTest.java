package com.arspaper.recipe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>shaped レシピの素材解決は「ingredients マップ」ではなく「shape に現れる文字」から引く。</b>
 *
 * <p>2026-08-16 の実サーバ報告:
 * <pre>
 *   [ArsPaper] Unknown custom ingredient: compressed_cooked_beef_3x in recipe core_meat
 *   [ArsPaper] Skipping shaped recipe core_meat: ingredient 'g'=custom:compressed_cooked_beef_3x not found
 * </pre>
 * ところが<b>ミートコアのレシピは圧縮ステーキを使っていない</b>。shape は {@code adb/cec/bda} で
 * a〜e しか登場せず、f〜i は旧版の名残として ingredients に残っていただけだった。
 * {@code registerShaped} が ingredients を丸ごと回していたため、
 * <b>どのスロットにも対応しない残骸キーのせいでレシピが丸ごと登録されず</b>、
 * しかもログには実際には使っていない素材の名前が原因として出続けていた
 * ── 追いかけると必ず迷子になる型の誤誘導。
 *
 * <p>さらに Bukkit の {@code ShapedRecipe#setIngredient} は shape に無い文字を渡すと例外を投げるので、
 * 残骸キーが「解決できてしまった」場合は旧実装だとそこで落ちていた(二重に壊れていた)。
 *
 * <p>不変条件:
 * <ol>
 *   <li>shape が使っていない ingredients のキーは解決対象に入らない(無視される)。</li>
 *   <li>shape が使っているのに ingredients に無い文字は {@code null} として必ず表に出る
 *       ── ここが typo の検出口なので、残骸を無視する代わりに検出力が落ちてはいけない。</li>
 *   <li>出荷 yml の shaped レシピに、shape が使わない残骸キーが残っていない。</li>
 * </ol>
 */
class ShapedRecipeSymbolResolutionTest {

    private static UnifiedRecipeLoader.WorkbenchRecipeData shaped(
            String id, List<String> shape, Map<String, String> ingredients) {
        return new UnifiedRecipeLoader.WorkbenchRecipeData(
                id, "shaped", "custom:" + id, 1, shape, ingredients, "workbench", false);
    }

    @Test
    @DisplayName("shape が使わない残骸キーは解決対象に入らない(ミートコアの実データ)")
    void unusedIngredientKeysAreIgnored() {
        Map<String, String> ing = new LinkedHashMap<>();
        ing.put("a", "custom:beef_3x");
        ing.put("b", "custom:porkchop_3x");
        ing.put("c", "custom:mutton_3x");
        ing.put("d", "custom:chicken_3x");
        ing.put("e", "custom:rabbit_3x");
        // 旧版の名残。shape の adb/cec/bda には一度も現れない。
        ing.put("f", "custom:piglin_brute_plate");
        ing.put("g", "custom:compressed_cooked_beef_3x"); // 実在しないid
        ing.put("h", "custom:ravager_hide");
        ing.put("i", "custom:compressed_cooked_beef_3x"); // 実在しないid

        var data = shaped("core_meat", List.of("adb", "cec", "bda"), ing);
        var resolved = RecipeManager.shapeSymbolIngredients(data);

        assertEquals(List.of('a', 'd', 'b', 'c', 'e'), new ArrayList<>(resolved.keySet()),
                "shape に現れる文字だけが、現れた順で対象になること");
        assertTrue(resolved.values().stream().noneMatch(v -> v != null && v.contains("compressed_cooked_beef")),
                "使っていない素材が解決対象に紛れ込んでいる。これがレシピを丸ごと落としていた真因");
    }

    @Test
    @DisplayName("shape が使うのに ingredients に無い文字は null として表に出る(typo の検出口)")
    void missingSymbolIsReportedAsNull() {
        Map<String, String> ing = new LinkedHashMap<>();
        ing.put("a", "COOKED_BEEF");
        // 'b' を打ち間違えて 'x' に書いてしまった状況
        ing.put("x", "LEATHER");

        var data = shaped("typo_recipe", List.of("ab"), ing);
        var resolved = RecipeManager.shapeSymbolIngredients(data);

        assertEquals(List.of('a', 'b'), new ArrayList<>(resolved.keySet()));
        assertNull(resolved.get('b'),
                "残骸を無視する代わりに typo を見逃すようになってはいけない");
    }

    @Test
    @DisplayName("空白スロットと同じ文字の重複は対象にしない")
    void blanksAndDuplicatesAreCollapsed() {
        Map<String, String> ing = new LinkedHashMap<>();
        ing.put("i", "IRON_INGOT");
        var data = shaped("blank_recipe", List.of("i i", "   ", " i "), ing);
        var resolved = RecipeManager.shapeSymbolIngredients(data);
        assertEquals(List.of('i'), new ArrayList<>(resolved.keySet()));
    }

    @Test
    @DisplayName("出荷 yml の shaped レシピに、shape が使わない残骸キーが残っていない")
    void shippedShapedRecipesHaveNoUnusedIngredientKeys() {
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (String resource : List.of("materials.yml", "functional-items.yml", "spellbooks.yml",
                "sourcejars.yml", "sourcelinks.yml", "threads.yml")) {
            Map<String, Object> root = loadOrEmpty(resource);
            checked += collectUnusedKeys(root, resource, offenders);
        }
        assertTrue(checked > 0,
                "出荷 yml から shaped レシピを1件も拾えていない。抽出が壊れているとこの検査は「全部OK」に化ける");
        assertEquals(List.of(), offenders,
                "shape が使わない ingredients のキーが残っている。どのスロットにも対応しないので"
                        + "レシピの一部ではなく、実在しないidを指していると原因調査を丸ごと誤誘導する: " + offenders);
    }

    // ---- 出荷 yml 走査 ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadOrEmpty(String resource) {
        try (InputStream in = ShapedRecipeSymbolResolutionTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                return new LinkedHashMap<>();
            }
            Object root = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return root instanceof Map ? (Map<String, Object>) root : new LinkedHashMap<>();
        } catch (Exception e) {
            throw new AssertionError("failed to read " + resource, e);
        }
    }

    /** 再帰的に recipe / recipes を探し、shaped のものだけ検査する。戻り値は検査した shaped レシピ数。 */
    @SuppressWarnings("unchecked")
    private static int collectUnusedKeys(Object node, String trail, List<String> offenders) {
        if (node instanceof List<?> list) {
            int n = 0;
            for (Object v : list) {
                n += collectUnusedKeys(v, trail, offenders);
            }
            return n;
        }
        if (!(node instanceof Map)) {
            return 0;
        }
        Map<String, Object> map = (Map<String, Object>) node;
        int checked = 0;
        checked += checkRecipe(map.get("recipe"), trail + ".recipe", offenders);
        if (map.get("recipes") instanceof List<?> recipes) {
            int i = 0;
            for (Object r : recipes) {
                checked += checkRecipe(r, trail + ".recipes[" + (i++) + "]", offenders);
            }
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if ("recipe".equals(e.getKey()) || "recipes".equals(e.getKey())) {
                continue;
            }
            checked += collectUnusedKeys(e.getValue(), trail + "." + e.getKey(), offenders);
        }
        return checked;
    }

    @SuppressWarnings("unchecked")
    private static int checkRecipe(Object node, String where, List<String> offenders) {
        if (!(node instanceof Map)) {
            return 0;
        }
        Map<String, Object> recipe = (Map<String, Object>) node;
        if (!"shaped".equalsIgnoreCase(String.valueOf(recipe.get("type")))) {
            return 0;
        }
        if (!(recipe.get("ingredients") instanceof Map<?, ?> ingredients)) {
            return 0; // shapeless 形式(リスト)で書かれているものはここでは見ない
        }
        StringBuilder shape = new StringBuilder();
        if (recipe.get("shape") instanceof List<?> rows) {
            for (Object row : rows) {
                shape.append(row);
            }
        }
        for (Object key : ingredients.keySet()) {
            String symbol = String.valueOf(key);
            if (shape.indexOf(symbol) < 0) {
                offenders.add(where + " の '" + symbol + "'=" + ingredients.get(key));
            }
        }
        return 1;
    }

    @Test
    @DisplayName("この検査の前提: 出荷 materials.yml を実際に読めている")
    void shippedResourceIsReadable() {
        assertNotNull(loadOrEmpty("materials.yml").get("materials"),
                "materials.yml が読めていない。読めないと上の出荷検査が無条件で通ってしまう");
    }
}
