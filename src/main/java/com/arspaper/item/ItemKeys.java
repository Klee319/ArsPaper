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

    /** ワンドのティア (1=Novice, 2=Apprentice, 3=Archmage) */
    public static final NamespacedKey WAND_TIER = new NamespacedKey(NAMESPACE, "wand_tier");

    /** 防具スレッドのタイプ（旧形式、後方互換用） */
    public static final NamespacedKey THREAD_TYPE = new NamespacedKey(NAMESPACE, "thread_type");

    /** 防具スレッドスロット（JSON配列: ["mana_regen", null, "mana_boost"]） */
    public static final NamespacedKey THREAD_SLOTS = new NamespacedKey(NAMESPACE, "thread_slots");

    /** ArsPaperが所有するスレッド表示Lore（Adventure Component JSON文字列の配列） */
    public static final NamespacedKey THREAD_LORE = new NamespacedKey(NAMESPACE, "thread_lore");

    /** スレッドアイテムのタイプID */
    public static final NamespacedKey THREAD_ITEM_TYPE = new NamespacedKey(NAMESPACE, "thread_item_type");

    /**
     * スレッドアイテム個体の厳選値（rollSeed + quality）は 2026-08-03 以降、Ars独自PDCではなく
     * TrinityForge の {@code ItemData}(=通常アイテムと同じ {@code trinityforge:item_roll_seed}/
     * {@code trinityforge:item_quality})に統一された（{@code com.arspaper.integration.
     * TrinityForgeBridge#stampThreadIdentity}/{@code #readThreadIdentity} 参照）。
     * このキー自体はもう使われていない（過去に書かれた個体の残骸が読めても無視される）。
     */
    public static final NamespacedKey THREAD_ROLL = new NamespacedKey(NAMESPACE, "thread_roll");

    /**
     * 防具に装着済みスレッドの厳選結果（JSON配列。{@link #THREAD_SLOTS} と<b>同じ添字</b>で対応する）。
     * <p>キーを分けているのは後方互換のため: 既存の防具はこのキーを持たないので「厳選なし」＝
     * 現行とまったく同じ挙動になり、移行処理が一切要らない。{@link #THREAD_SLOTS} の形式を
     * 変えるとロード時の分岐が必要になり、失敗すると装着済みスレッドが消える。
     *
     * <p><b>格納値の形式(2026-08-03移行)</b>: 各要素は {@code "<rollSeed>:<quality>"}
     * （{@code com.arspaper.item.ThreadSlotIdentity} が符号化/復号を担う）。旧形式
     * （{@code "<rarityId>|main=値|sub=値;..."}）が残る既存装備は、{@code ":"} 区切りで
     * long/int としてパースできないため {@code ThreadSlotIdentity#decode} が rollSeed=0/
     * quality=0（＝個体差なしの従来どおりの幅）へfail-openする。空スロットは空文字のまま。
     */
    public static final NamespacedKey THREAD_SLOT_ROLLS = new NamespacedKey(NAMESPACE, "thread_slot_rolls");

    /** スペルブックのUUID（個体識別用） */
    public static final NamespacedKey SPELL_BOOK_UUID = new NamespacedKey(NAMESPACE, "spell_book_uuid");

    /** バインド先スペルブックのUUID */
    public static final NamespacedKey BOUND_BOOK_UUID = new NamespacedKey(NAMESPACE, "bound_book_uuid");

    /** バインド先スペルスロット番号（0-indexed） */
    public static final NamespacedKey BOUND_SPELL_SLOT = new NamespacedKey(NAMESPACE, "bound_spell_slot");

    /** 設定ベース防具のセットID（armors.ymlの定義キー） */
    public static final NamespacedKey ARMOR_SET_ID = new NamespacedKey(NAMESPACE, "armor_set_id");

    /** スペルブックの所有者UUID */
    public static final NamespacedKey SPELL_BOOK_OWNER = new NamespacedKey(NAMESPACE, "spell_book_owner");

    /**
     * 魂縛スレッドの所有者UUID（2026-08-25 W-259）。
     *
     * <p>対象は構造物のルートチェスト専属のトレジャースレッドだけ
     * （判定は {@link TreasureThreadSoulbindPolicy}）。刻印は「最初に拾った人」で決まる。
     *
     * <p>⚠ TrinityForge の {@code ItemData#owner()} とは<b>別の台帳</b>。TF 側は
     * PDC に {@code bind_type} と {@code roll_seed} がある品にしか刻印しないので、
     * {@code external-source: arspaper} のスレッドには一度も発火しない。
     * TF の所有者が入っている品ではそちらが正なので、読むときは
     * {@code TrinityForgeBridge.tfOwnerId} を先に見ること
     * （2本の台帳がずれると「lore に自分の名前が出ているのに弾かれる」になる。
     *   魔導書で実際に起きた事故 W-100 と同じ形）。
     */
    public static final NamespacedKey THREAD_SOULBOUND_OWNER =
        new NamespacedKey(NAMESPACE, "thread_soulbound_owner");

    /**
     * 装着済みスレッドの所有者UUID（スロット添字ごとのJSON配列／2026-08-25 W-259）。
     * {@link #THREAD_SLOTS} と<b>同じ添字</b>で並ぶ。所有者が居ない枠は空文字。
     *
     * <p><b>なぜ装備側にも持つのか</b>: スレッド単体を弾くだけでは
     * 「自分で挿してから装備ごと渡す」で素通りする ── ユーザーが最初に指摘した抜け道
     * 「他の人にスレッド付きの武器とかが渡された場合にどうやって対処しよう」がこれ。
     * さらに取り外しでスレッドを作り直す({@code ThreadGui#createThreadItemStack})ので、
     * ここに残しておかないと<b>挿して外すだけで所有者が消える</b>(洗浄できてしまう)。
     *
     * <p>{@link #THREAD_SLOT_ROLLS} と同じく、全部空なら<b>書かない</b>
     * (魂縛スレッドを一度も挿していない装備は導入前と同じPDC形状のまま)。
     */
    public static final NamespacedKey THREAD_SLOT_OWNERS =
        new NamespacedKey(NAMESPACE, "thread_slot_owners");
}
