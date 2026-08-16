package com.arspaper.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1アイテムに複数レシピ (recipe: と recipes:) を書けることの回帰テスト。
 *
 * <p>2026-08-13 まで全ローダーが {@code recipe:}(単数)しか読まなかったため、設定エディタの
 * 共通レシピUI(catalog.yml の正規形へ書き戻す)で2件目を足すと、正規形が {@code recipes:} に
 * 切り替わった瞬間に<b>1件目ごと Java から見えなくなっていた</b>。素材だけ扱いを変える理由は
 * 無い(儀式を使うアイテムは素材以外にもある)ので、読み取り側をカタログと同じ形に統合した。
 */
class UnifiedRecipeLoaderMultiRecipeTest {

    private static ConfigurationSection parse(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception invalid) {
            throw new AssertionError("テスト用 yaml が壊れている", invalid);
        }
        ConfigurationSection section = config.getConfigurationSection("entry");
        assertTrue(section != null, "テスト用 yaml に entry: が無い");
        return section;
    }

    @Test
    @DisplayName("recipes:(配列) だけのエントリでも全件読める(以前は0件になっていた)")
    void readsRecipesArray() {
        ConfigurationSection entry = parse(String.join("\n",
                "entry:",
                "  recipes:",
                "    - method: ritual",
                "      core-item: DIAMOND",
                "      source: 100",
                "    - method: workbench",
                "      type: shapeless",
                "      ingredients: [DIRT]"));

        List<ConfigurationSection> sections = UnifiedRecipeLoader.recipeSections(entry);

        assertEquals(2, sections.size(), "recipes: の配列が読めていない");
        assertEquals("ritual", sections.get(0).getString("method"));
        assertEquals(100, sections.get(0).getInt("source"));
        assertEquals("workbench", sections.get(1).getString("method"));
        assertEquals(List.of("DIRT"), sections.get(1).getStringList("ingredients"));
    }

    @Test
    @DisplayName("recipe:(単数) は従来どおり読める")
    void readsSingleRecipe() {
        ConfigurationSection entry = parse(String.join("\n",
                "entry:",
                "  recipe:",
                "    method: ritual",
                "    core-item: DIAMOND"));

        List<ConfigurationSection> sections = UnifiedRecipeLoader.recipeSections(entry);

        assertEquals(1, sections.size());
        assertEquals("DIAMOND", sections.get(0).getString("core-item"));
    }

    @Test
    @DisplayName("recipe: と recipes: が両方あれば両方読む(片方を黙って捨てない)")
    void readsBothForms() {
        ConfigurationSection entry = parse(String.join("\n",
                "entry:",
                "  recipe:",
                "    method: ritual",
                "    core-item: DIAMOND",
                "  recipes:",
                "    - method: ritual",
                "      core-item: EMERALD"));

        List<ConfigurationSection> sections = UnifiedRecipeLoader.recipeSections(entry);

        assertEquals(2, sections.size());
        assertEquals("DIAMOND", sections.get(0).getString("core-item"));
        assertEquals("EMERALD", sections.get(1).getString("core-item"));
    }

    @Test
    @DisplayName("レシピが1件も無いエントリは空(例外にしない)")
    void noRecipeIsEmpty() {
        assertEquals(0, UnifiedRecipeLoader.recipeSections(parse("entry:\n  display_name: foo")).size());
        assertEquals(0, UnifiedRecipeLoader.recipeSections(null).size());
    }

    @Test
    @DisplayName("2件目以降の登録キーは結果アイテムIDと別物になる(同じだと後勝ちで片方消える)")
    void secondRecipeGetsDistinctRegistrationKey() {
        // 作業台は new NamespacedKey(plugin, id)、儀式は Map のキーがどちらもこの文字列なので、
        // 1件目と同じキーを返すと2件目の登録で1件目が上書きされる(=UIでは2件見えるのに1件しか作れない)。
        assertEquals("mythril_ingot", UnifiedRecipeLoader.recipeKey("mythril_ingot", 0));
        assertNotEquals(UnifiedRecipeLoader.recipeKey("mythril_ingot", 0),
                UnifiedRecipeLoader.recipeKey("mythril_ingot", 1));
        assertNotEquals(UnifiedRecipeLoader.recipeKey("mythril_ingot", 1),
                UnifiedRecipeLoader.recipeKey("mythril_ingot", 2));
        assertEquals("mythril_ingot_r2", UnifiedRecipeLoader.recipeKey("mythril_ingot", 1));
    }
}
