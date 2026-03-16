package com.arspaper.block;

import org.bukkit.NamespacedKey;

/**
 * カスタムブロック用のPDC NamespacedKey定数。
 */
public final class BlockKeys {

    private BlockKeys() {}

    private static final String NAMESPACE = "arspaper";

    /** カスタムブロックの識別子 */
    public static final NamespacedKey CUSTOM_BLOCK_ID = new NamespacedKey(NAMESPACE, "custom_block_id");

    /** ブロック固有のJSONデータ */
    public static final NamespacedKey CUSTOM_DATA = new NamespacedKey(NAMESPACE, "custom_data");

    /** Source Jar のSource貯蔵量 */
    public static final NamespacedKey SOURCE_AMOUNT = new NamespacedKey(NAMESPACE, "source_amount");

    /** Source Jar の無限フラグ（クリエイティブ瓶） */
    public static final NamespacedKey SOURCE_INFINITE = new NamespacedKey(NAMESPACE, "source_infinite");

    /** ArmorStand識別用マーカー */
    public static final NamespacedKey DISPLAY_MARKER = new NamespacedKey(NAMESPACE, "block_display");
}
