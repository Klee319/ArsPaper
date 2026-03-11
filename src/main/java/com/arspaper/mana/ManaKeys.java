package com.arspaper.mana;

import org.bukkit.NamespacedKey;

/**
 * マナシステム用のPDC NamespacedKey定数。
 */
public final class ManaKeys {

    private ManaKeys() {}

    private static final String NAMESPACE = "arspaper";

    /** 現在のマナ値 */
    public static final NamespacedKey CURRENT_MANA = new NamespacedKey(NAMESPACE, "current_mana");

    /** マナボーナス（後方互換用、非推奨） */
    public static final NamespacedKey MANA_BONUS = new NamespacedKey(NAMESPACE, "mana_bonus");

    /** グリフアンロックによるマナボーナス */
    public static final NamespacedKey GLYPH_MANA_BONUS = new NamespacedKey(NAMESPACE, "glyph_mana_bonus");

    /** 防具によるマナボーナス */
    public static final NamespacedKey ARMOR_MANA_BONUS = new NamespacedKey(NAMESPACE, "armor_mana_bonus");

    /** マナ回復レート */
    public static final NamespacedKey REGEN_RATE = new NamespacedKey(NAMESPACE, "regen_rate");

    /** アンロック済みグリフIDのJSON配列 */
    public static final NamespacedKey UNLOCKED_GLYPHS = new NamespacedKey(NAMESPACE, "unlocked_glyphs");
}
