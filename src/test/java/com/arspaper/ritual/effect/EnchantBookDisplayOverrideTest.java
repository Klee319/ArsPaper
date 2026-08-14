package com.arspaper.ritual.effect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-08-14: エンチャント本の儀式レシピを items.yml から functional-items.yml へ移し、
 * 表示名/lore を functional-items.yml から上書きできるようにした件の回帰テスト。
 *
 * <p>実サーバ報告は「回生とマナ関連のエンチャント本の儀式レシピが消えている」。
 * 実際には yml にもゲーム内にも在ったが、設定エディタの items.yml 用画面が
 * {@code ritual_effects:} しか描画しないため editor から一切見えなかった。
 *
 * <p>ここで固定するのは2点:
 * <ul>
 *   <li>移設後の functional-items.yml に8件が儀式レシピとして揃っていること
 *       (effect-type/effect-params はプログラムが結果を決めるキーなので必須)</li>
 *   <li>items.yml 側に enchant_book_* が残っていないこと
 *       (両方に在ると同じ登録キーで後勝ちし、片方の編集が無言で効かなくなる)</li>
 * </ul>
 *
 * <p>本フォークは Bukkit ランタイムを持たないため、生成される ItemStack の表示までは
 * ここでは踏めない。上書きの引き当てに使う ID 正規化だけ純関数として固定する。
 */
class EnchantBookDisplayOverrideTest {

    private static final List<String> EXPECTED_IDS = List.of(
        "enchant_book_mana_regen_1", "enchant_book_mana_regen_2", "enchant_book_mana_regen_3",
        "enchant_book_mana_boost_1", "enchant_book_mana_boost_2", "enchant_book_mana_boost_3",
        "enchant_book_share", "enchant_book_soulbound"
    );

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadResource(String name) throws Exception {
        try (InputStream in = EnchantBookDisplayOverrideTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " が resources に無い");
            return (Map<String, Object>) new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> itemsOf(Map<String, Object> root) {
        Object items = root.get("items");
        assertTrue(items instanceof Map, "items: セクションがマップでない");
        return (Map<String, Object>) items;
    }

    @Test
    @DisplayName("functional-items.yml にエンチャント本8件が儀式レシピとして揃っている")
    @SuppressWarnings("unchecked")
    void functionalItemsHoldsEnchantBooks() throws Exception {
        Map<String, Object> items = itemsOf(loadResource("functional-items.yml"));

        for (String id : EXPECTED_IDS) {
            Object raw = items.get(id);
            assertTrue(raw instanceof Map, id + " が functional-items.yml の items: に無い");
            Map<String, Object> entry = (Map<String, Object>) raw;

            Object recipeRaw = entry.get("recipe");
            assertTrue(recipeRaw instanceof Map, id + " に recipe: が無い");
            Map<String, Object> recipe = (Map<String, Object>) recipeRaw;

            assertEquals("ritual", recipe.get("method"), id + " の method が ritual でない");
            // effect-type/effect-params は EnchantBookRitualEffect が結果を決める値。
            // 落とすと「儀式は成立するのに何も出ない」形で無言死する。
            assertEquals("enchant_book", recipe.get("effect-type"), id + " の effect-type が enchant_book でない");
            Object paramsRaw = recipe.get("effect-params");
            assertTrue(paramsRaw instanceof Map, id + " に effect-params が無い");
            Map<String, Object> params = (Map<String, Object>) paramsRaw;
            assertNotNull(params.get("enchantment"), id + " の effect-params.enchantment が無い");
            assertNotNull(params.get("level"), id + " の effect-params.level が無い");
            assertEquals("BOOK", recipe.get("core-item"), id + " のコアアイテムが BOOK でない");
        }
    }

    @Test
    @DisplayName("functional-items.yml のエンチャント本は表示名と lore を宣言している")
    @SuppressWarnings("unchecked")
    void enchantBooksDeclareDisplay() throws Exception {
        Map<String, Object> items = itemsOf(loadResource("functional-items.yml"));

        for (String id : EXPECTED_IDS) {
            Map<String, Object> entry = (Map<String, Object>) items.get(id);
            Object displayName = entry.get("display-name");
            assertTrue(displayName instanceof String && !((String) displayName).isBlank(),
                id + " に display-name が無い(宣言しておかないと editor から表示を編集する足場が無い)");
            Object lore = entry.get("lore");
            assertTrue(lore instanceof List && !((List<?>) lore).isEmpty(), id + " に lore が無い");
        }
    }

    @Test
    @DisplayName("items.yml 側にエンチャント本は残っていない(二重定義は後勝ちで無言に化ける)")
    void itemsYmlNoLongerHoldsEnchantBooks() throws Exception {
        Map<String, Object> items = itemsOf(loadResource("items.yml"));
        for (String id : items.keySet()) {
            assertFalse(id.startsWith("enchant_book_"),
                "items.yml に " + id + " が残っている。functional-items.yml と同じ登録キーなので"
                    + "後に読まれた方が勝ち、editor で直した側が無言で効かなくなる");
        }
    }

    @Test
    @DisplayName("_r2 形式の登録キーは基底IDへ寄せる(2件目のレシピでも上書きを引ける)")
    void stripsRecipeIndexSuffix() {
        assertEquals("enchant_book_share",
            EnchantBookRitualEffect.stripRecipeIndexSuffix("enchant_book_share_r2"));
        assertEquals("enchant_book_share",
            EnchantBookRitualEffect.stripRecipeIndexSuffix("enchant_book_share_r10"));
        // 接尾辞が無いものはそのまま。
        assertEquals("enchant_book_share",
            EnchantBookRitualEffect.stripRecipeIndexSuffix("enchant_book_share"));
        // 数字以外が続くものは接尾辞ではない(_ring 等を誤って切らない)。
        assertEquals("source_ring", EnchantBookRitualEffect.stripRecipeIndexSuffix("source_ring"));
        assertEquals("foo_r", EnchantBookRitualEffect.stripRecipeIndexSuffix("foo_r"));
        assertEquals("foo_r2x", EnchantBookRitualEffect.stripRecipeIndexSuffix("foo_r2x"));
        assertEquals(null, EnchantBookRitualEffect.stripRecipeIndexSuffix(null));
    }
}
