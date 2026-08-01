package com.arspaper.gui;

import org.bukkit.Material;

import java.util.Locale;

/**
 * レシピ一覧の「分類順」で使う 5 分類（N4: 防具・素材・武器・ツール・その他）。
 *
 * <p><b>宣言順がそのまま表示順</b>。既存の {@code 種別順(スキル→素材)} は
 * item-stats の使用スキル文字列をそのまま並べるだけで分類にはならない
 * （使用スキル未設定のアイテムが Material 名で散らばる）ため、
 * 「防具はどれか」を見るには使えなかった。こちらは必ず 5 つのどれかに落ちる。
 *
 * <p>判定の優先順位は<b>確度の高い情報から</b>:
 * <ol>
 *   <li>TrinityForge の item-stats 最上位カテゴリ（{@code weapon/armor/tool/...}）。
 *       config エディタで人が割り当てた値なので最も確か。</li>
 *   <li>item-stats の使用スキル（{@code heavy_armor} など）。</li>
 *   <li>Material 名の語尾（{@code _CHESTPLATE} など）。バニラ装備のフォールバック。</li>
 *   <li>他のレシピの素材として使われているか。ここまで来て使われていれば「素材」。</li>
 * </ol>
 *
 * <p><b>使用スキルを最優先にしてはいけない</b>: {@code use-skill} は分類マーカーではなく、
 * 採取ツールにも武器にも付く「どのスキルのレベルで使えるか」でしかない。TF の最上位カテゴリが
 * ある場合は必ずそちらを勝たせること。
 */
enum RecipeCategory {

    ARMOR("防具"),
    MATERIAL("素材"),
    WEAPON("武器"),
    TOOL("ツール"),
    OTHER("その他");

    private final String label;

    RecipeCategory(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    /**
     * 分類を決める純関数。
     *
     * @param tfCategory        TF item-stats の最上位カテゴリ（{@code weapon/armor/tool/catalyst/...}）。無ければ null
     * @param useSkill          item-stats の使用スキル。無ければ空文字/null
     * @param icon              結果アイテムの Material。無ければ null
     * @param usedAsIngredient  このレシピの結果が他のレシピの素材として登場するか
     */
    static RecipeCategory classify(String tfCategory, String useSkill, Material icon,
                                   boolean usedAsIngredient) {
        RecipeCategory byTf = fromTfCategory(tfCategory);
        if (byTf != null) return byTf;

        RecipeCategory bySkill = fromUseSkill(useSkill);
        if (bySkill != null) return bySkill;

        RecipeCategory byMaterial = fromMaterialName(icon == null ? null : icon.name());
        if (byMaterial != null) return byMaterial;

        return usedAsIngredient ? MATERIAL : OTHER;
    }

    /** TF item-stats の最上位カテゴリ。{@code catalyst/spellbook/thread/other} は装備でないので「その他」。 */
    private static RecipeCategory fromTfCategory(String tfCategory) {
        if (tfCategory == null || tfCategory.isBlank()) return null;
        return switch (tfCategory.trim().toLowerCase(Locale.ROOT)) {
            case "armor" -> ARMOR;
            case "weapon" -> WEAPON;
            case "tool" -> TOOL;
            case "catalyst", "spellbook", "thread", "other" -> OTHER;
            default -> null;
        };
    }

    /** 使用スキル → 分類。スキル体系は TF の {@code skills/base/*_progression.yml} に対応。 */
    private static RecipeCategory fromUseSkill(String useSkill) {
        if (useSkill == null || useSkill.isBlank()) return null;
        return switch (useSkill.trim().toLowerCase(Locale.ROOT)) {
            case "heavy_armor", "light_armor" -> ARMOR;
            case "heavy_weapons", "light_weapons", "archery" -> WEAPON;
            case "mining", "woodcutting", "farming" -> TOOL;
            default -> null;
        };
    }

    /**
     * Material 名からの推定（バニラ装備のフォールバック）。
     * 斧は武器にも道具にもなるが、{@code use-skill} が付いていない素の斧は道具として扱う
     * （TF カテゴリか使用スキルが付いていれば、そもそもここへは来ない）。
     */
    private static RecipeCategory fromMaterialName(String name) {
        if (name == null || name.isBlank()) return null;
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("TURTLE_HELMET") || name.equals("ELYTRA")
                || name.equals("SHIELD")) {
            return ARMOR;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_SPEAR")
                || name.equals("BOW") || name.equals("CROSSBOW")
                || name.equals("TRIDENT") || name.equals("MACE")) {
            return WEAPON;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE") || name.equals("SHEARS") || name.equals("FISHING_ROD")
                || name.equals("FLINT_AND_STEEL") || name.equals("BRUSH")) {
            return TOOL;
        }
        return null;
    }
}
