package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * レシピ一覧の並べ替え・絞り込み・ワイルドカード検索 (2026-07-27)。
 *
 * <p>Bukkit を触らない純粋ロジックだけを検証する（{@link RecipeEntry#icon} は使わず、
 * 種別キーは {@code sortSkill} 側だけを見る）。
 */
class RecipeBrowserFilterTest {

    private static RecipeEntry entry(String name, String skill, int level) {
        RecipeEntry e = new RecipeEntry();
        e.id = name;
        e.displayName = name;
        e.icon = null;
        e.sortSkill = skill;
        e.sortLevel = level;
        return e;
    }

    private static List<String> names(List<RecipeEntry> entries) {
        return entries.stream().map(e -> e.displayName).toList();
    }

    private static final Predicate<RecipeEntry> ALL_UNLOCKED = e -> true;

    @Test
    @DisplayName("既定は収集順のまま並べ替えない")
    void defaultKeepsCollectionOrder() {
        List<RecipeEntry> src = List.of(entry("ゼータ", "", 0), entry("アルファ", "", 0));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.ALL, null, ALL_UNLOCKED);
        assertEquals(List.of("ゼータ", "アルファ"), names(out));
    }

    @Test
    @DisplayName("名前順は表示名の昇順")
    void sortsByName() {
        List<RecipeEntry> src = List.of(entry("banana", "", 0), entry("Apple", "", 0));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.NAME, RecipeBrowserFilter.FilterMode.ALL, null, ALL_UNLOCKED);
        assertEquals(List.of("Apple", "banana"), names(out));
    }

    @Test
    @DisplayName("種別順はスキル種別で並び、同種別内は名前順で安定する")
    void sortsByKindThenName() {
        List<RecipeEntry> src = List.of(
            entry("剣B", "SWORD", 10),
            entry("弓", "BOW", 5),
            entry("剣A", "SWORD", 30));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.KIND, RecipeBrowserFilter.FilterMode.ALL, null, ALL_UNLOCKED);
        assertEquals(List.of("弓", "剣A", "剣B"), names(out));
    }

    @Test
    @DisplayName("使用可能レベル順は昇順（素材数順の置き換え）")
    void sortsByLevel() {
        List<RecipeEntry> src = List.of(entry("上級", "", 40), entry("初級", "", 1), entry("中級", "", 15));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.LEVEL, RecipeBrowserFilter.FilterMode.ALL, null, ALL_UNLOCKED);
        assertEquals(List.of("初級", "中級", "上級"), names(out));
    }

    @Test
    @DisplayName("解放済み/未解放は別ボタン相当の独立フィルタとして効く")
    void filtersByUnlockState() {
        RecipeEntry open = entry("解放済み", "", 0);
        RecipeEntry locked = entry("未解放", "", 0);
        List<RecipeEntry> src = List.of(open, locked);
        Predicate<RecipeEntry> unlocked = e -> e == open;

        assertEquals(List.of("解放済み", "未解放"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.ALL, null, unlocked)));
        assertEquals(List.of("解放済み"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.UNLOCKED, null, unlocked)));
        assertEquals(List.of("未解放"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.LOCKED, null, unlocked)));
    }

    @Test
    @DisplayName("ワイルドカードを含まない検索語は部分一致になる")
    void plainSearchIsSubstring() {
        List<RecipeEntry> src = List.of(entry("鉄の剣", "", 0), entry("金の斧", "", 0));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.ALL, "剣", ALL_UNLOCKED);
        assertEquals(List.of("鉄の剣"), names(out));
    }

    @Test
    @DisplayName("* と ? がワイルドカードとして効き、大文字小文字は無視される")
    void wildcardSearch() {
        List<RecipeEntry> src = List.of(
            entry("iron_sword", "", 0), entry("iron_axe", "", 0), entry("gold_sword", "", 0));

        assertEquals(List.of("iron_sword", "iron_axe"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.ALL, "IRON_*", ALL_UNLOCKED)));
        assertEquals(List.of("iron_sword", "gold_sword"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.ALL, "*_sword", ALL_UNLOCKED)));
        assertEquals(List.of("iron_axe"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.ALL, "iron_?xe", ALL_UNLOCKED)));
    }

    @Test
    @DisplayName("正規表現メタ文字はリテラルとして扱う（検索語で例外を出さない）")
    void regexMetaCharsAreLiteral() {
        List<RecipeEntry> src = List.of(entry("a+b", "", 0), entry("aab", "", 0));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.FilterMode.ALL, "a+b", ALL_UNLOCKED);
        assertEquals(List.of("a+b"), names(out));
    }

    @Test
    @DisplayName("空白のみ/nullの検索語は検索なし扱い")
    void blankSearchIsNoop() {
        assertNull(RecipeBrowserFilter.compileGlob(null));
        assertNull(RecipeBrowserFilter.compileGlob("   "));
    }

    @Test
    @DisplayName("表示名の「×N」接尾辞はソートキーから外れる")
    void sortNameDropsAmountSuffix() {
        RecipeEntry e = entry("ソースジェム ×4", "", 0);
        assertEquals("ソースジェム", e.sortName());
    }

    @Test
    @DisplayName("素材トークンは儀式ならコア+台座、作業台なら素材マップの値")
    void ingredientTokens() {
        RecipeEntry ritual = entry("儀式", "", 0);
        ritual.isRitual = true;
        ritual.coreItem = "custom:source_gem";
        ritual.ingredients = List.of("DIAMOND", "DIAMOND", "custom:source_gem");
        assertEquals(Set.of("custom:source_gem", "DIAMOND"), ritual.ingredientTokens());

        RecipeEntry bench = entry("作業台", "", 0);
        bench.ingredientMap = java.util.Map.of("A", "STICK");
        assertTrue(bench.ingredientTokens().contains("STICK"));
    }
}
