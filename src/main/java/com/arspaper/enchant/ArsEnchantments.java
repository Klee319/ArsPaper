package com.arspaper.enchant;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * ArsPaperカスタムエンチャント（PDCベース）。
 * Paper Bootstrapに依存せず全バージョンで動作する。
 * エンチャント情報は防具PDCに格納され、ArmorManaListenerで読み取られる。
 */
public final class ArsEnchantments {

    private ArsEnchantments() {}

    private static final String NAMESPACE = "arspaper";

    /** マナ加速エンチャントのPDCキー（値: レベル 1-3） */
    public static final NamespacedKey MANA_REGEN_KEY = new NamespacedKey(NAMESPACE, "enchant_mana_regen");

    /** マナ上昇エンチャントのPDCキー（値: レベル 1-3） */
    public static final NamespacedKey MANA_BOOST_KEY = new NamespacedKey(NAMESPACE, "enchant_mana_boost");

    /** マナ加速の1レベルあたりのリジェンボーナス */
    public static final int REGEN_PER_LEVEL = 1;

    /** マナ上昇のレベルごとのマナボーナス */
    public static final int[] MANA_BOOST_PER_LEVEL = { 0, 15, 30, 50 };

    public static final int MAX_LEVEL = 3;

    public static int getManaBoostForLevel(int level) {
        if (level <= 0 || level >= MANA_BOOST_PER_LEVEL.length) return 0;
        return MANA_BOOST_PER_LEVEL[level];
    }

    /** アイテムからマナ加速レベルを取得 */
    public static int getManaRegenLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(MANA_REGEN_KEY, PersistentDataType.INTEGER, 0);
    }

    /** アイテムからマナ上昇レベルを取得 */
    public static int getManaBoostLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(MANA_BOOST_KEY, PersistentDataType.INTEGER, 0);
    }

    /** エンチャントIDからPDCキーを取得 */
    public static NamespacedKey getKeyFromId(String enchantId) {
        return switch (enchantId) {
            case "mana_regen" -> MANA_REGEN_KEY;
            case "mana_boost" -> MANA_BOOST_KEY;
            default -> null;
        };
    }

    /** エンチャントIDから表示名を取得 */
    public static String getDisplayName(String enchantId) {
        return switch (enchantId) {
            case "mana_regen" -> "マナ加速";
            case "mana_boost" -> "マナ上昇";
            default -> "不明";
        };
    }

    /** ローマ数字変換 */
    public static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }
}
