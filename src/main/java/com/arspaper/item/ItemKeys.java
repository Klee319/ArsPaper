package com.arspaper.item;

import org.bukkit.NamespacedKey;

/**
 * カスタムアイテム用のPDC NamespacedKey定数。
 */
public final class ItemKeys {

    private ItemKeys() {}

    private static final String NAMESPACE = "arspaper";

    /** カスタムアイテムの識別子 */
    public static final NamespacedKey CUSTOM_ITEM_ID = new NamespacedKey(NAMESPACE, "custom_item_id");

    /** アイテム固有のJSONデータ */
    public static final NamespacedKey CUSTOM_DATA = new NamespacedKey(NAMESPACE, "custom_data");

    /** スペル構成JSON */
    public static final NamespacedKey SPELL_RECIPE = new NamespacedKey(NAMESPACE, "spell_recipe");

    /** 現在選択中のスペルスロット */
    public static final NamespacedKey SPELL_SLOT = new NamespacedKey(NAMESPACE, "spell_slot");

    /** スペルブックのティア (1=Novice, 2=Apprentice, 3=Archmage) */
    public static final NamespacedKey BOOK_TIER = new NamespacedKey(NAMESPACE, "book_tier");

    /** 全スペルスロットのJSON配列 */
    public static final NamespacedKey SPELL_SLOTS = new NamespacedKey(NAMESPACE, "spell_slots");

    /** 防具のティア (1=Novice, 2=Apprentice, 3=Archmage) */
    public static final NamespacedKey ARMOR_TIER = new NamespacedKey(NAMESPACE, "armor_tier");
}
