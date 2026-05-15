package com.arspaper.ritual;

import com.arspaper.ArsPaper;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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

    /** プレビュー/ArsAPI用にItemStackへ変換する。 */
    public ItemStack toItemStack() {
        if (isCustom) {
            return ArsPaper.getInstance().getItemRegistry().get(materialOrCustomId)
                .map(item -> item.createItemStack())
                .orElseGet(() -> new ItemStack(Material.PAPER));
        }
        Material mat = Material.matchMaterial(materialOrCustomId);
        return new ItemStack(mat != null ? mat : Material.PAPER);
    }
}
