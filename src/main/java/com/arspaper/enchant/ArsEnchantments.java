package com.arspaper.enchant;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * ArsPaperカスタムエンチャント。
 * Paper Registry APIで登録された正式なエンチャントとして機能する。
 * エンチャント台からは出ない（weight=1, cost=100+）が、金床で本から適用可能。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ArsEnchantments {

    private ArsEnchantments() {}

    /** マナ再生のレベルごとのリジェンボーナス */
    public static final int[] REGEN_PER_LEVEL = { 0, 1, 3, 6 };

    /** マナ上昇のレベルごとのマナボーナス */
    public static final int[] MANA_BOOST_PER_LEVEL = { 0, 15, 30, 50 };

    public static final int MAX_LEVEL = 3;

    // ============================================================
    // エンチャント参照（遅延初期化）
    // ============================================================

    private static Enchantment manaRegen;
    private static Enchantment manaBoost;
    private static Enchantment share;

    public static Enchantment getManaRegen() {
        if (manaRegen == null) {
            manaRegen = lookupEnchantment("mana_regen");
        }
        return manaRegen;
    }

    public static Enchantment getManaBoost() {
        if (manaBoost == null) {
            manaBoost = lookupEnchantment("mana_boost");
        }
        return manaBoost;
    }

    public static Enchantment getShare() {
        if (share == null) {
            share = lookupEnchantment("share");
        }
        return share;
    }

    private static Enchantment lookupEnchantment(String id) {
        Registry<Enchantment> registry = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT);
        Enchantment enchant = registry.get(Key.key("arspaper", id));
        if (enchant == null) {
            throw new IllegalStateException("Enchantment arspaper:" + id + " not found in registry. Is ArsBootstrap configured?");
        }
        return enchant;
    }

    // ============================================================
    // ユーティリティ
    // ============================================================

    public static int getManaRegenForLevel(int level) {
        if (level <= 0 || level >= REGEN_PER_LEVEL.length) return 0;
        return REGEN_PER_LEVEL[level];
    }

    public static int getManaBoostForLevel(int level) {
        if (level <= 0 || level >= MANA_BOOST_PER_LEVEL.length) return 0;
        return MANA_BOOST_PER_LEVEL[level];
    }

    /** アイテムからマナ再生レベルを取得 */
    public static int getManaRegenLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getEnchantmentLevel(getManaRegen());
    }

    /** アイテムからマナ上昇レベルを取得 */
    public static int getManaBoostLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getEnchantmentLevel(getManaBoost());
    }

    /** アイテムに共有エンチャントが付いているか */
    public static boolean hasShareEnchant(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getEnchantmentLevel(getShare()) > 0;
    }

    /** エンチャントIDからEnchantmentを取得 */
    public static Enchantment getFromId(String enchantId) {
        return switch (enchantId) {
            case "mana_regen" -> getManaRegen();
            case "mana_boost" -> getManaBoost();
            case "share" -> getShare();
            default -> null;
        };
    }

    /** エンチャントIDから表示名を取得 */
    public static String getDisplayName(String enchantId) {
        return switch (enchantId) {
            case "mana_regen" -> "マナ再生";
            case "mana_boost" -> "マナ上昇";
            case "share" -> "共有";
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
