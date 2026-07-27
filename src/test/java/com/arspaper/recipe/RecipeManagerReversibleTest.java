package com.arspaper.recipe;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RecipeManager} の reversible / inventory 判定ロジックの純粋部分テスト。
 * Bukkit サーバー起動を要さない範囲(uniform ingredient 判定、2×2上限判定)を検証する。
 * materials.yml 実データ(echo_shard_1x/2x, magebloom_fiber 等)の形をそのまま再現している。
 */
class RecipeManagerReversibleTest {

    private static UnifiedRecipeLoader.WorkbenchRecipeData shaped(String id, String result,
            List<String> shape, Map<String, String> ingredients, String method, boolean reversible) {
        return new UnifiedRecipeLoader.WorkbenchRecipeData(
                id, "shaped", result, 1, shape, ingredients, method, reversible);
    }

    private static UnifiedRecipeLoader.WorkbenchRecipeData shapeless(String id, String result,
            Map<String, String> ingredients, String method, boolean reversible) {
        return new UnifiedRecipeLoader.WorkbenchRecipeData(
                id, "shapeless", result, 1, List.of(), ingredients, method, reversible);
    }

    @Test
    void uniformShapedIngredientResolvesSingleValueAndCount() {
        // echo_shard_1x: shape ii/ii, ingredient i=ECHO_SHARD (materials.yml:711-719)
        Map<String, String> ing = new HashMap<>();
        ing.put("i", "ECHO_SHARD");
        var data = shaped("echo_shard_1x", "custom:echo_shard_1x",
                List.of("ii", "ii"), ing, "inventory", true);

        Optional<String> uniform = RecipeManager.uniformIngredientValue(data);
        assertTrue(uniform.isPresent());
        assertEquals("ECHO_SHARD", uniform.get());
        assertEquals(4, RecipeManager.uniformIngredientCount(data));
    }

    @Test
    void uniformShapedIngredientWithCustomReferenceChain() {
        // echo_shard_2x: shape ii/ii, ingredient i=custom:echo_shard_1x (materials.yml:1181-1194)
        Map<String, String> ing = new HashMap<>();
        ing.put("i", "custom:echo_shard_1x");
        var data = shaped("echo_shard_2x", "custom:echo_shard_2x",
                List.of("ii", "ii"), ing, "inventory", true);

        Optional<String> uniform = RecipeManager.uniformIngredientValue(data);
        assertTrue(uniform.isPresent());
        assertEquals("custom:echo_shard_1x", uniform.get());
        assertEquals(4, RecipeManager.uniformIngredientCount(data));
    }

    @Test
    void nonUniformShapelessIngredientsAreNotReversible() {
        // magebloom_fiber: shapeless [WHEAT_SEEDS, custom:source_gem] (materials.yml:63-69) — not uniform
        Map<String, String> ing = new HashMap<>();
        ing.put("A", "WHEAT_SEEDS");
        ing.put("B", "custom:source_gem");
        var data = shapeless("magebloom_fiber", "custom:magebloom_fiber", ing, "inventory", false);

        assertFalse(RecipeManager.uniformIngredientValue(data).isPresent());
    }

    @Test
    void uniformShapedNineSlotIngredientCountsAllNonEmptySlots() {
        // source_gem_block: shape iii/iii/iii, ingredient i=custom:source_gem (materials.yml:45-54)
        Map<String, String> ing = new HashMap<>();
        ing.put("i", "custom:source_gem");
        var data = shaped("source_gem_block", "custom:source_gem_block",
                List.of("iii", "iii", "iii"), ing, "workbench", true);

        Optional<String> uniform = RecipeManager.uniformIngredientValue(data);
        assertTrue(uniform.isPresent());
        assertEquals("custom:source_gem", uniform.get());
        assertEquals(9, RecipeManager.uniformIngredientCount(data));
    }

    @Test
    void missingIngredientMappingForShapeSymbolIsNotUniform() {
        Map<String, String> ing = new HashMap<>();
        ing.put("i", "STONE");
        // shape references 'j' which has no ingredient entry
        var data = shaped("broken", "custom:broken", List.of("ij", "ij"), ing, "inventory", true);

        assertFalse(RecipeManager.uniformIngredientValue(data).isPresent());
    }

    @Test
    void inventoryShapeWithinTwoByTwoFits() {
        assertTrue(RecipeManager.fitsInventoryShape(List.of("ii", "ii")));
        assertTrue(RecipeManager.fitsInventoryShape(List.of("i")));
    }

    @Test
    void inventoryShapeExceedingTwoByTwoDoesNotFit() {
        assertFalse(RecipeManager.fitsInventoryShape(List.of("iii", "iii", "iii")));
        assertFalse(RecipeManager.fitsInventoryShape(List.of("i", "i", "i")));
    }

    // ------------------------------------------------------------------
    // requiresBareCustomIngredient — CustomIngredientCraftGuardListener が
    // 「盤面にカスタムアイテムが無くても per-slot 検証を強制すべきか」を判定するために使う。
    // custom: の素のバニラ代用exploit(source_gem等)を塞ぐための判定ロジック。
    // ------------------------------------------------------------------

    @Test
    void recipeWithBareCustomIngredientRequiresForcedGuard() {
        // ソースジェム装備相当: custom:source_gem を含むshapedレシピ
        Map<String, String> ing = new HashMap<>();
        ing.put("i", "custom:source_gem");
        ing.put("s", "STICK");
        var data = shaped("source_gem_pickaxe", "custom:source_gem_pickaxe",
                List.of("ii", " s"), ing, "workbench", false);

        assertTrue(RecipeManager.requiresBareCustomIngredient(data));
    }

    @Test
    void recipeWithOnlyPlainMaterialsDoesNotRequireForcedGuard() {
        // 圧縮ブロックのtier1相当: プレーンSTONEのみ、custom:は無い
        Map<String, String> ing = new HashMap<>();
        ing.put("i", "STONE");
        var data = shaped("stone_1x", "custom:stone_1x",
                List.of("iii", "iii", "iii"), ing, "workbench", true);

        assertFalse(RecipeManager.requiresBareCustomIngredient(data));
    }

    @Test
    void recipeWithOnlyListIngredientDoesNotRequireForcedGuard() {
        // list: はvanillaメンバの受理が設計上正当なので対象外
        Map<String, String> ing = new HashMap<>();
        ing.put("i", "list:some_wood_planks");
        var data = shaped("some_result", "custom:some_result",
                List.of("iii", "iii", "iii"), ing, "workbench", false);

        assertFalse(RecipeManager.requiresBareCustomIngredient(data));
    }

    @Test
    void recipeWithMixOfPlainAndCustomIngredientsRequiresForcedGuard() {
        // magebloom_fiber相当: shapeless [WHEAT_SEEDS, custom:source_gem]
        Map<String, String> ing = new HashMap<>();
        ing.put("A", "WHEAT_SEEDS");
        ing.put("B", "custom:source_gem");
        var data = shapeless("magebloom_fiber", "custom:magebloom_fiber", ing, "inventory", false);

        assertTrue(RecipeManager.requiresBareCustomIngredient(data));
    }

    // ------------------------------------------------------------------
    // listRequiresForcedGuard — 2026-07-24 レビューのM指摘「fork の短絡が TF 条件より弱い
    // (custom-only list 前提)」を 2026-07-26 に閉じたぶんの単体テスト。
    // list: は通常「vanilla メンバの受理が正当」なので強制ガードの対象外だが、custom メンバしか
    // 持たないリストは素のバニラでは絶対に満たせない = custom: と同じ扱いにしないと、
    // 盤面にカスタムアイテムが1つも無いときに per-slot 検証ごと素通りしてしまう。
    // ------------------------------------------------------------------

    @Test
    void customOnlyListRequiresForcedGuard() {
        assertTrue(RecipeManager.listRequiresForcedGuard(Set.of("source_gem"), Set.of()));
    }

    @Test
    void listWithAnyVanillaMemberDoesNotRequireForcedGuard() {
        // 混在リスト(custom + vanilla): vanilla メンバでのクラフトが設計上正当なので対象外のまま。
        assertFalse(RecipeManager.listRequiresForcedGuard(
                Set.of("source_gem"), Set.of(Material.DIAMOND)));
    }

    @Test
    void vanillaOnlyListDoesNotRequireForcedGuard() {
        assertFalse(RecipeManager.listRequiresForcedGuard(
                Set.of(), Set.of(Material.OAK_PLANKS, Material.BIRCH_PLANKS)));
    }

    @Test
    void emptyListDoesNotRequireForcedGuard() {
        // 未定義リストid / TF未ロード時は両方空になる。ここで true を返すと、全レシピが常に
        // per-slot 検証を強制されてしまう(=短絡の意味が消える)ので false でなければならない。
        assertFalse(RecipeManager.listRequiresForcedGuard(Set.of(), Set.of()));
    }
}
