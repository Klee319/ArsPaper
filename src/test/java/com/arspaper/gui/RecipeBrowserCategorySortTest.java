package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「分類順」ソートの並びと、最低品質ステータス表示の配線（N4）。
 */
class RecipeBrowserCategorySortTest {

    private static RecipeEntry entry(String name, RecipeCategory category) {
        RecipeEntry e = new RecipeEntry();
        e.id = name;
        e.displayName = name;
        e.sortCategory = category;
        return e;
    }

    @Test
    @DisplayName("分類順は 防具→素材→武器→ツール→その他 で、同分類内は名前順")
    void categorySortFollowsDeclarationOrder() {
        List<RecipeEntry> source = List.of(
                entry("zz その他", RecipeCategory.OTHER),
                entry("bb ツール", RecipeCategory.TOOL),
                entry("aa 武器", RecipeCategory.WEAPON),
                entry("cc 武器", RecipeCategory.WEAPON),
                entry("dd 素材", RecipeCategory.MATERIAL),
                entry("ee 防具", RecipeCategory.ARMOR));

        List<RecipeEntry> sorted = RecipeBrowserFilter.arrange(source,
                RecipeBrowserFilter.SortMode.CATEGORY, RecipeBrowserFilter.KindMode.ALL, null);

        assertEquals(List.of("ee 防具", "dd 素材", "aa 武器", "cc 武器", "bb ツール", "zz その他"),
                sorted.stream().map(e -> e.displayName).toList());
    }

    @Test
    @DisplayName("分類未設定(null)でも落ちず『その他』として最後に回る")
    void nullCategoryIsTreatedAsOther() {
        RecipeEntry unset = entry("未設定", RecipeCategory.OTHER);
        unset.sortCategory = null;
        List<RecipeEntry> sorted = RecipeBrowserFilter.arrange(
                List.of(unset, entry("防具", RecipeCategory.ARMOR)),
                RecipeBrowserFilter.SortMode.CATEGORY, RecipeBrowserFilter.KindMode.ALL, null);
        assertEquals("防具", sorted.get(0).displayName);
        assertEquals("未設定", sorted.get(1).displayName);
    }

    @Test
    @DisplayName("巡回順の約束は壊さない: 先頭は名前順、末尾は登録順のまま")
    void cycleContractIsPreserved() {
        RecipeBrowserFilter.SortMode[] modes = RecipeBrowserFilter.SortMode.values();
        assertSame(RecipeBrowserFilter.SortMode.NAME, modes[0]);
        assertSame(RecipeBrowserFilter.SortMode.DEFAULT, modes[modes.length - 1]);
        assertSame(RecipeBrowserFilter.SortMode.NAME, RecipeBrowserFilter.SortMode.DEFAULT.next());
    }

    @Test
    @DisplayName("既定の初期分類は『その他』（分類前に武器などへ化けない）")
    void defaultCategoryIsOther() {
        assertSame(RecipeCategory.OTHER, new RecipeEntry().sortCategory);
    }

    @Test
    @DisplayName("最低ステータスは『品質0・ばらつき0』で求める（乱数シードに依存させない）")
    void minimumStatsUseAZeroSpreadRollModel() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/arspaper/integration/TrinityForgeStatPreview.java"));
        assertTrue(source.contains("new QualityRollModel(maxQuality, 0.0, 0.0, 0.0)"),
                "σ>0 や inset>0 を残すと reach が 0 に張り付かず、表示値が『最低』でなくなる");
        assertTrue(source.contains("profileStats("),
                "item-stats の fixed + random.min を引く経路が消えている");
    }

    @Test
    @DisplayName("最低ステータスの表示は全経路でフェイルソフト（TF未ロードで例外を出さない）")
    void statPreviewIsFailSoft() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/arspaper/integration/TrinityForgeStatPreview.java"));
        long guards = source.lines().filter(l -> l.contains("catch (Throwable")).count();
        assertTrue(guards >= 4,
                "TF未ロード環境でレシピGUIごと落ちる。public 経路は全て Throwable で受けること (現在 "
                        + guards + " 箇所)");
    }
}
