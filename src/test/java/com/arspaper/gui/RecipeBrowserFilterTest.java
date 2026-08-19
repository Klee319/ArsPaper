package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @DisplayName("既定は収集順のまま並べ替えない")
    void defaultKeepsCollectionOrder() {
        List<RecipeEntry> src = List.of(entry("ゼータ", "", 0), entry("アルファ", "", 0));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, null);
        assertEquals(List.of("ゼータ", "アルファ"), names(out));
    }

    @Test
    @DisplayName("名前順は表示名の昇順")
    void sortsByName() {
        List<RecipeEntry> src = List.of(entry("banana", "", 0), entry("Apple", "", 0));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.NAME, RecipeBrowserFilter.KindMode.ALL, null);
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
            RecipeBrowserFilter.SortMode.KIND, RecipeBrowserFilter.KindMode.ALL, null);
        assertEquals(List.of("弓", "剣A", "剣B"), names(out));
    }

    @Test
    @DisplayName("使用可能レベル順は昇順（素材数順の置き換え）")
    void sortsByLevel() {
        List<RecipeEntry> src = List.of(entry("上級", "", 40), entry("初級", "", 1), entry("中級", "", 15));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.LEVEL, RecipeBrowserFilter.KindMode.ALL, null);
        assertEquals(List.of("初級", "中級", "上級"), names(out));
    }

    @Test
    @DisplayName("作業台/儀式は別ボタン相当の独立フィルタとして効く (2026-07-30 解放状態から置換)")
    void filtersByRecipeKind() {
        RecipeEntry bench = entry("作業台レシピ", "", 0);
        RecipeEntry ritual = entry("儀式レシピ", "", 0);
        ritual.isRitual = true;
        List<RecipeEntry> src = List.of(bench, ritual);

        assertEquals(List.of("作業台レシピ", "儀式レシピ"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, null)));
        assertEquals(List.of("作業台レシピ"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.WORKBENCH, null)));
        assertEquals(List.of("儀式レシピ"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.RITUAL, null)));
    }

    @Test
    @DisplayName("儀式エフェクトは儀式レシピから外れ、独立して絞り込める (2026-08-19 W-123)")
    void ritualEffectsSplitOutOfRitualRecipes() {
        RecipeEntry bench = entry("作業台レシピ", "", 0);
        RecipeEntry craftRitual = entry("儀式で作るアイテム", "", 0);
        craftRitual.isRitual = true;
        craftRitual.effectType = "craft";
        RecipeEntry legacyRitual = entry("旧経路の儀式", "", 0);
        legacyRitual.isRitual = true; // effectType 未設定 = 結果アイテムを持つ従来の儀式として扱う
        RecipeEntry sunrise = entry("日の出の儀式", "", 0);
        sunrise.isRitual = true;
        sunrise.effectType = "sunrise";
        RecipeEntry slotExpand = entry("スレッド枠拡張の儀式", "", 0);
        slotExpand.isRitual = true;
        slotExpand.effectType = "thread_slot_expand";
        List<RecipeEntry> src = List.of(bench, craftRitual, legacyRitual, sunrise, slotExpand);

        assertEquals(List.of("儀式で作るアイテム", "旧経路の儀式"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.RITUAL, null)),
            "アイテムを作らない儀式が儀式レシピに混ざってはいけない(これが W-123 の報告そのもの)");
        assertEquals(List.of("日の出の儀式", "スレッド枠拡張の儀式"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.RITUAL_EFFECT, null)));
        assertEquals(List.of("作業台レシピ"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.WORKBENCH, null)),
            "作業台側は儀式の分割に影響されない");
        assertEquals(5, RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, null).size(),
            "すべて では1件も落ちない");
    }

    @Test
    @DisplayName("儀式エフェクトの判定は effect-type が craft 以外という1点だけで決まる (2026-08-19 W-123)")
    void effectRitualIsDecidedByEffectTypeAlone() {
        // 個別の effect-type を列挙して判定すると、新しい種別を足したときに絞り込みだけが
        // 無言で取りこぼす。判定は RitualRecipe#isCraft() の裏返しであることを固定する。
        for (String type : List.of("sunrise", "moonfall", "weather", "flight", "repair",
                "animal_summon", "mob_summon", "enchant_book", "thread", "thread_slot_expand",
                "まだ存在しない新しい効果")) {
            RecipeEntry e = entry(type, "", 0);
            e.isRitual = true;
            e.effectType = type;
            assertTrue(RecipeBrowserFilter.isEffectRitual(e), type + " は儀式エフェクト側");
        }
        RecipeEntry craft = entry("craft", "", 0);
        craft.isRitual = true;
        craft.effectType = "craft";
        assertFalse(RecipeBrowserFilter.isEffectRitual(craft), "craft はアイテムを作る儀式");
        RecipeEntry legacy = entry("legacy", "", 0);
        legacy.isRitual = true;
        assertFalse(RecipeBrowserFilter.isEffectRitual(legacy), "effect-type 未設定も従来の儀式扱い");
    }

    /** レシピキー(= 一覧の同定キー)を持つ作業台レシピ。圧縮の間引きはキーだけで判定する。 */
    private static RecipeEntry keyed(String recipeKey, String name) {
        RecipeEntry e = entry(name, "", 0);
        e.id = recipeKey;
        return e;
    }

    @Test
    @DisplayName("既定では圧縮は最大倍率だけを出し、中間段と解凍を隠す (2026-08-19 W-122/W-99)")
    void hidesIntermediateCompressionStepsByDefault() {
        List<RecipeEntry> src = List.of(
            keyed("stone_1x", "9倍圧縮石"),
            keyed("stone_2x", "81倍圧縮石"),
            keyed("stone_3x", "729倍圧縮石"),
            keyed("stone_1x_decompress", "9倍圧縮石の解凍"),
            keyed("stone_3x_decompress", "729倍圧縮石の解凍"),
            keyed("catalog_oak_wood_1x", "9倍圧縮オークの原木"),
            keyed("catalog_oak_wood_2x", "81倍圧縮オークの原木"),
            keyed("diamond_sword_tf", "ダイヤの剣"));

        assertEquals(List.of("729倍圧縮石", "81倍圧縮オークの原木", "ダイヤの剣"),
            names(RecipeBrowserFilter.arrange(src,
                RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, null)),
            "既定では各連鎖の最大段だけが残り、解凍と圧縮以外は影響を受けない");

        assertEquals(8, RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, null, true).size(),
            "トグルを on にすれば全段と解凍が戻る");
    }

    @Test
    @DisplayName("圧縮の間引きは検索語で変わらない (2026-08-19 W-122)")
    void topCompressionStageDoesNotDependOnSearch() {
        List<RecipeEntry> src = List.of(
            keyed("stone_1x", "9倍圧縮石"),
            keyed("stone_2x", "81倍圧縮石"),
            keyed("stone_3x", "729倍圧縮石"));
        // 検索で最大段が弾かれても、中間段が繰り上がって出てきてはいけない
        // (同じアイテムが検索のたびに出たり消えたりして見える)。
        assertEquals(List.of(), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, "81倍*")));
        assertEquals(List.of("729倍圧縮石"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, "*圧縮石")));
    }

    @Test
    @DisplayName("圧縮の判定はレシピキーだけで決まり、表示名の書き方に依存しない (2026-08-19 W-122)")
    void compressionIsDetectedFromRecipeKeyOnly() {
        RecipeEntry looksCompressed = keyed("ancient_debris_scrap", "9倍圧縮っぽい名前");
        assertNull(RecipeBrowserFilter.compressionBase(looksCompressed),
            "表示名に『倍圧縮』と書いてあるだけの品を巻き込んではいけない");
        assertEquals(0, RecipeBrowserFilter.compressionStage(looksCompressed));

        RecipeEntry compressed = keyed("catalog_oak_wood_3x", "任意の名前");
        assertEquals("oak_wood", RecipeBrowserFilter.compressionBase(compressed));
        assertEquals(3, RecipeBrowserFilter.compressionStage(compressed));
        assertFalse(RecipeBrowserFilter.isDecompression(compressed));

        RecipeEntry decompress = keyed("stone_2x_decompress", "任意の名前");
        assertEquals("stone", RecipeBrowserFilter.compressionBase(decompress),
            "解凍レシピも同じ連鎖として数える");
        assertTrue(RecipeBrowserFilter.isDecompression(decompress));
    }

    @Test
    @DisplayName("並べ替えの巡回は名前順から始まり、登録順が最後に来る (2026-07-30 ユーザー確定)")
    void sortCycleStartsAtNameAndEndsAtDefault() {
        RecipeBrowserFilter.SortMode[] modes = RecipeBrowserFilter.SortMode.values();
        assertEquals(RecipeBrowserFilter.SortMode.NAME, modes[0], "既定(先頭)は名前順");
        assertEquals(RecipeBrowserFilter.SortMode.DEFAULT, modes[modes.length - 1], "登録順は最後");
        assertEquals(RecipeBrowserFilter.SortMode.NAME, RecipeBrowserFilter.SortMode.DEFAULT.next(),
            "最後まで回ったら名前順へ戻る");
    }

    @Test
    @DisplayName("ワイルドカードを含まない検索語は部分一致になる")
    void plainSearchIsSubstring() {
        List<RecipeEntry> src = List.of(entry("鉄の剣", "", 0), entry("金の斧", "", 0));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, "剣");
        assertEquals(List.of("鉄の剣"), names(out));
    }

    @Test
    @DisplayName("* と ? がワイルドカードとして効き、大文字小文字は無視される")
    void wildcardSearch() {
        List<RecipeEntry> src = List.of(
            entry("iron_sword", "", 0), entry("iron_axe", "", 0), entry("gold_sword", "", 0));

        assertEquals(List.of("iron_sword", "iron_axe"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, "IRON_*")));
        assertEquals(List.of("iron_sword", "gold_sword"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, "*_sword")));
        assertEquals(List.of("iron_axe"), names(RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, "iron_?xe")));
    }

    @Test
    @DisplayName("正規表現メタ文字はリテラルとして扱う（検索語で例外を出さない）")
    void regexMetaCharsAreLiteral() {
        List<RecipeEntry> src = List.of(entry("a+b", "", 0), entry("aab", "", 0));
        List<RecipeEntry> out = RecipeBrowserFilter.arrange(src,
            RecipeBrowserFilter.SortMode.DEFAULT, RecipeBrowserFilter.KindMode.ALL, "a+b");
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
