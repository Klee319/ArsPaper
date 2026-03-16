package com.arspaper.ritual;

import org.bukkit.Material;

/**
 * 儀式の素材を表す。バニラMaterialまたはカスタムアイテムIDのいずれか。
 *
 * @param materialOrCustomId Material名またはカスタムアイテムID
 * @param isCustom           trueならカスタムアイテム
 */
public record RitualIngredient(String materialOrCustomId, boolean isCustom) {

    public static RitualIngredient ofMaterial(Material mat) {
        return new RitualIngredient(mat.name(), false);
    }

    public static RitualIngredient ofCustom(String customId) {
        return new RitualIngredient(customId, true);
    }
}
