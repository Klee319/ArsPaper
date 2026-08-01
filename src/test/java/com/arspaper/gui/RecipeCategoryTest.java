package com.arspaper.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * レシピ一覧の5分類（N4: 防具・素材・武器・ツール・その他）の判定固定。
 */
class RecipeCategoryTest {

    @Test
    @DisplayName("5分類がこの順に並ぶ（宣言順＝表示順）")
    void declarationOrderIsDisplayOrder() {
        RecipeCategory[] values = RecipeCategory.values();
        assertEquals(5, values.length, "5分類から増減している");
        assertEquals("防具", values[0].label());
        assertEquals("素材", values[1].label());
        assertEquals("武器", values[2].label());
        assertEquals("ツール", values[3].label());
        assertEquals("その他", values[4].label());
    }

    @Test
    @DisplayName("TFの最上位カテゴリが最優先（使用スキルより強い）")
    void tfCategoryWinsOverUseSkill() {
        // 斧は伐採スキルで使うが、TF側で武器として登録されているなら武器。
        assertSame(RecipeCategory.WEAPON,
                RecipeCategory.classify("weapon", "woodcutting", Material.NETHERITE_AXE, false));
        assertSame(RecipeCategory.ARMOR,
                RecipeCategory.classify("armor", "", Material.DIAMOND_HELMET, false));
        assertSame(RecipeCategory.TOOL,
                RecipeCategory.classify("tool", "", Material.IRON_PICKAXE, false));
    }

    @Test
    @DisplayName("装備でないTFカテゴリ（触媒/魔導書/スレッド）は「その他」へ落とす")
    void nonEquipmentTfCategoriesFallToOther() {
        assertSame(RecipeCategory.OTHER, RecipeCategory.classify("catalyst", "", Material.STICK, true));
        assertSame(RecipeCategory.OTHER, RecipeCategory.classify("spellbook", "", Material.BOOK, false));
        assertSame(RecipeCategory.OTHER, RecipeCategory.classify("thread", "", Material.STRING, true));
    }

    @Test
    @DisplayName("TFカテゴリが無ければ使用スキルで分類する")
    void useSkillIsTheSecondSource() {
        assertSame(RecipeCategory.ARMOR,
                RecipeCategory.classify(null, "heavy_armor", Material.PAPER, false));
        assertSame(RecipeCategory.WEAPON,
                RecipeCategory.classify(null, "archery", Material.PAPER, false));
        assertSame(RecipeCategory.TOOL,
                RecipeCategory.classify(null, "mining", Material.PAPER, false));
    }

    @Test
    @DisplayName("TFカテゴリもスキルも無ければ Material 名で拾う（バニラ装備）")
    void materialNameIsTheFallback() {
        assertSame(RecipeCategory.ARMOR,
                RecipeCategory.classify(null, null, Material.LEATHER_BOOTS, false));
        assertSame(RecipeCategory.ARMOR,
                RecipeCategory.classify(null, null, Material.TURTLE_HELMET, false));
        assertSame(RecipeCategory.WEAPON,
                RecipeCategory.classify(null, null, Material.BOW, false));
        assertSame(RecipeCategory.TOOL,
                RecipeCategory.classify(null, null, Material.IRON_SHOVEL, false));
    }

    @Test
    @DisplayName("装備でないものは「他のレシピの素材か」で 素材 / その他 を分ける")
    void ingredientUsageDecidesMaterialVsOther() {
        assertSame(RecipeCategory.MATERIAL,
                RecipeCategory.classify(null, null, Material.IRON_INGOT, true),
                "他のレシピで使われている完成品は素材として扱う");
        assertSame(RecipeCategory.OTHER,
                RecipeCategory.classify(null, null, Material.CAKE, false),
                "どのレシピの素材にもならない非装備は『その他』");
    }

    @Test
    @DisplayName("未知のTFカテゴリ文字列は無視して次の判定へ進む（黙って『その他』に潰さない）")
    void unknownTfCategoryFallsThrough() {
        assertSame(RecipeCategory.WEAPON,
                RecipeCategory.classify("なにか未知のタブ", null, Material.DIAMOND_SWORD, false));
    }
}
