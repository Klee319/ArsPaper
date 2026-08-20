package com.arspaper.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 統合版へ渡す補正レシピ表の中身を固定する。Bukkit サーバー起動を要さない純粋部分だけを見る
 * （アイテム解決は {@link BedrockRecipeExporter.ItemResolver} で差し替える）。
 *
 * <p>守るのは「<b>素材の identity が落ちないこと</b>」の一点。Geyser の既定変換が素材を
 * CustomModelData ごと捨ててしまうのが不具合の真因なので、この表から CMD が消えたら補正の
 * 意味が丸ごと無くなる ── しかも<b>症状は「統合版でクラフトできない」のままで、表を出して
 * いること自体は見えてしまう</b>ので、実サーバでは気づけない。
 */
class BedrockRecipeExporterTest {

    private static final Integer GEM_CMD = 100_011;
    private static final Integer GEM_BLOCK_CMD = 100_012;

    /** {@code custom:<id>} と素の Material を解ける差し替え解決器。 */
    private static BedrockRecipeExporter.ItemResolver resolver(
            Map<String, BedrockRecipeExporter.ItemRef> custom) {
        return new BedrockRecipeExporter.ItemResolver() {
            @Override
            public List<BedrockRecipeExporter.ItemRef> ingredient(String raw) {
                return single(raw).map(List::of).orElse(List.of());
            }

            @Override
            public Optional<BedrockRecipeExporter.ItemRef> result(String raw, int amount) {
                return single(raw).map(ref -> new BedrockRecipeExporter.ItemRef(
                        ref.material(), ref.customModelData(), amount));
            }

            private Optional<BedrockRecipeExporter.ItemRef> single(String raw) {
                if (raw == null) {
                    return Optional.empty();
                }
                if (raw.startsWith("custom:")) {
                    return Optional.ofNullable(custom.get(raw.substring("custom:".length())));
                }
                Material material = Material.getMaterial(raw);
                return material == null
                        ? Optional.empty()
                        : Optional.of(BedrockRecipeExporter.ItemRef.of(material, null));
            }
        };
    }

    private static UnifiedRecipeLoader.WorkbenchRecipeData compression(String id, String ingredient) {
        Map<String, String> ingredients = new HashMap<>();
        ingredients.put("i", ingredient);
        return new UnifiedRecipeLoader.WorkbenchRecipeData(
                id, "shaped", "custom:" + id, 1, List.of("iii", "iii", "iii"),
                ingredients, "workbench", true);
    }

    /**
     * <b>本題。</b> 素材の CustomModelData がそのまま表に出ること。
     * ここが null になると受け取り側はバニラ素材の descriptor しか作れず、
     * Geyser の既定変換と同じ＝何も直らない表になる。
     */
    @Test
    void keepsCustomModelDataOfIngredients() {
        var table = BedrockRecipeExporter.build(
                List.of(new BedrockRecipeExporter.Entry(
                        "arspaper:source_gem_block", compression("source_gem_block", "custom:source_gem"), false)),
                resolver(Map.of(
                        "source_gem", BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_SHARD, GEM_CMD),
                        "source_gem_block", BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_BLOCK, GEM_BLOCK_CMD))));

        assertEquals(1, table.recipes().size(), "圧縮レシピが1件出るはず: " + table.skipped());
        BedrockRecipeExporter.Recipe recipe = table.recipes().get(0);
        assertTrue(recipe.shaped());
        assertEquals(3, recipe.width());
        assertEquals(3, recipe.height());
        assertEquals(9, recipe.slots().size());
        for (BedrockRecipeExporter.Slot slot : recipe.slots()) {
            assertEquals(List.of(BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_SHARD, GEM_CMD)), slot.items());
        }
        assertEquals(new BedrockRecipeExporter.ItemRef(Material.AMETHYST_BLOCK, GEM_BLOCK_CMD, 1), recipe.result());
    }

    /**
     * 素材が全部バニラのレシピは<b>書き出さない</b>。Geyser の既定変換で正しく照合できる
     * （結果側は元から正しく変換される）ので、足すと同じレシピが二重に載るだけになる。
     */
    @Test
    void skipsRecipesWhoseIngredientsAreAllVanilla() {
        var table = BedrockRecipeExporter.build(
                List.of(new BedrockRecipeExporter.Entry(
                        "arspaper:plain", compression("plain", "AMETHYST_SHARD"), false)),
                resolver(Map.of("plain", BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_BLOCK, 1))));

        assertTrue(table.recipes().isEmpty(), "バニラ素材だけのレシピは出さない");
        assertTrue(table.skipped().isEmpty(), "解決失敗ではないので skipped にも入らない");
    }

    /**
     * <b>解凍レシピの取りこぼし防止。</b> 実サーバ報告では「解凍だけリザルトが出る」ので、
     * 逆レシピを落とすと直りが片側だけになる。ただし {@code registerReverseIfNeeded} は
     * 素材が不揃いなら黙ってスキップするので、<b>実際に登録されたときだけ</b>出す。
     */
    @Test
    void emitsTheDecompressRecipeOnlyWhenItIsActuallyRegistered() {
        var custom = Map.of(
                "source_gem", BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_SHARD, GEM_CMD),
                "source_gem_block", BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_BLOCK, GEM_BLOCK_CMD));
        var data = compression("source_gem_block", "custom:source_gem");

        var without = BedrockRecipeExporter.build(
                List.of(new BedrockRecipeExporter.Entry("arspaper:source_gem_block", data, false)),
                resolver(custom));
        assertEquals(1, without.recipes().size(), "逆レシピ未登録なら正レシピだけ");

        var with = BedrockRecipeExporter.build(
                List.of(new BedrockRecipeExporter.Entry("arspaper:source_gem_block", data, true)),
                resolver(custom));
        BedrockRecipeExporter.Recipe decompress = with.recipes().stream()
                .filter(r -> r.id().endsWith("_decompress"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("解凍レシピが出ていない: " + with.recipes()));
        assertEquals("arspaper:source_gem_block_decompress", decompress.id());
        assertFalse(decompress.shaped());
        assertEquals(List.of(BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_BLOCK, GEM_BLOCK_CMD)),
                decompress.slots().get(0).items(), "素材は完成品そのもの");
        assertEquals(new BedrockRecipeExporter.ItemRef(Material.AMETHYST_SHARD, GEM_CMD, 9), decompress.result(),
                "結果は元素材9個");
    }

    /**
     * 解決できない素材があるレシピは<b>丸ごと落として名前を残す</b>。半分だけ正しい表を出すと、
     * 受け取り側は誤った素材で照合するレシピを注入してしまい、「クラフトできない」より悪い
     * 「別のレシピが成立する」になる。
     */
    @Test
    void dropsAndReportsRecipesWithUnresolvableIngredients() {
        var table = BedrockRecipeExporter.build(
                List.of(new BedrockRecipeExporter.Entry(
                        "arspaper:broken", compression("broken", "custom:does_not_exist"), false)),
                resolver(Map.of("broken", BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_BLOCK, 1))));

        assertTrue(table.recipes().isEmpty());
        assertEquals(List.of("arspaper:broken"), table.skipped());
    }

    /**
     * shapeless の素材並びを symbol 順に固定する。{@code WorkbenchRecipeData#ingredients} は
     * {@code HashMap} なので、並べ替えないと<b>実行のたびに JSON が変わる</b>
     * （＝毎回の配備で全プレイヤーへ補正パケットを撃ち直すか否かの判断が付かなくなる）。
     */
    @Test
    void shapelessIngredientsAreOrderedBySymbol() {
        Map<String, String> ingredients = new HashMap<>();
        ingredients.put("C", "custom:source_gem");
        ingredients.put("A", "STICK");
        ingredients.put("B", "PAPER");
        var data = new UnifiedRecipeLoader.WorkbenchRecipeData(
                "mix", "shapeless", "custom:mix", 1, List.of(), ingredients, "workbench", false);

        var table = BedrockRecipeExporter.build(
                List.of(new BedrockRecipeExporter.Entry("arspaper:mix", data, false)),
                resolver(Map.of(
                        "source_gem", BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_SHARD, GEM_CMD),
                        "mix", BedrockRecipeExporter.ItemRef.of(Material.PAPER, 700))));

        List<Material> order = table.recipes().get(0).slots().stream()
                .map(slot -> slot.items().get(0).material())
                .toList();
        assertEquals(List.of(Material.STICK, Material.PAPER, Material.AMETHYST_SHARD), order);
    }

    /**
     * JSON の形を固定する。受け取り側は別プロセス・別リポジトリなので、
     * ここが変わると<b>気づかないまま読み込みが空になる</b>。
     */
    @Test
    void jsonKeepsEmptySlotsAsNullAndOmitsCmdForVanilla() {
        Map<String, String> ingredients = new HashMap<>();
        ingredients.put("i", "custom:source_gem");
        ingredients.put("v", "STICK");
        var data = new UnifiedRecipeLoader.WorkbenchRecipeData(
                "wand", "shaped", "custom:wand", 4, List.of("i ", " v"), ingredients, "workbench", false);

        JsonObject json = BedrockRecipeExporter.toJson(BedrockRecipeExporter.build(
                List.of(new BedrockRecipeExporter.Entry("arspaper:wand", data, false)),
                resolver(Map.of(
                        "source_gem", BedrockRecipeExporter.ItemRef.of(Material.AMETHYST_SHARD, GEM_CMD),
                        "wand", BedrockRecipeExporter.ItemRef.of(Material.STICK, 200)))));

        assertEquals(BedrockRecipeExporter.FORMAT_VERSION, json.get("version").getAsInt());
        assertEquals("ArsPaper", json.get("source").getAsString());
        JsonObject recipe = json.getAsJsonArray("recipes").get(0).getAsJsonObject();
        assertEquals("shaped", recipe.get("type").getAsString());
        assertEquals(2, recipe.get("width").getAsInt());
        assertEquals(2, recipe.get("height").getAsInt());

        JsonArray slots = recipe.getAsJsonArray("slots");
        assertEquals(4, slots.size());
        assertTrue(slots.get(1).isJsonNull(), "空欄は null（行優先の位置がずれる）");
        assertTrue(slots.get(2).isJsonNull());
        JsonObject vanilla = slots.get(3).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("STICK", vanilla.get("material").getAsString());
        assertFalse(vanilla.has("cmd"), "バニラ素材に cmd は書かない");

        JsonObject custom = slots.get(0).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals(GEM_CMD.intValue(), custom.get("cmd").getAsInt());
        assertEquals(4, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    /**
     * TrinityForge 側と形式バージョンが揃っていること。
     *
     * <p>クラスを共有していない（ArsPaper のビルドを {@code libs/TrinityForge.jar} の差し替えに
     * 縛らないため）ので、ここがずれると受け取り側が<b>片方のファイルだけ読まずに捨てる</b>。
     * 形式を変えるときは TrinityForge の {@code BedrockRecipeTable.FORMAT_VERSION} と
     * この値の<b>両方</b>を直すこと。
     */
    @Test
    void formatVersionIsPinnedToTheTrinityForgeSideValue() {
        assertEquals(1, BedrockRecipeExporter.FORMAT_VERSION,
                "TrinityForge の BedrockRecipeTable.FORMAT_VERSION と同じ値でなければならない");
        assertEquals("bedrock-recipes.json", BedrockRecipeExporter.FILE_NAME);
    }
}
