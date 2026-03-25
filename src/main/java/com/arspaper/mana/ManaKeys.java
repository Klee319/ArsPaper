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

    /** スレッドによるリジェンボーナス */
    public static final NamespacedKey THREAD_REGEN_BONUS = new NamespacedKey(NAMESPACE, "thread_regen_bonus");

    /** スレッドによるマナボーナス */
    public static final NamespacedKey THREAD_MANA_BONUS = new NamespacedKey(NAMESPACE, "thread_mana_bonus");

    /** アンロック済みグリフIDのJSON配列 */
    public static final NamespacedKey UNLOCKED_GLYPHS = new NamespacedKey(NAMESPACE, "unlocked_glyphs");

    /** エンチャントによるマナボーナス */
    public static final NamespacedKey ENCHANT_MANA_BONUS = new NamespacedKey(NAMESPACE, "enchant_mana_bonus");

    /** エンチャントによるリジェンボーナス */
    public static final NamespacedKey ENCHANT_REGEN_BONUS = new NamespacedKey(NAMESPACE, "enchant_regen_bonus");

    /** スレッドによるスペル威力ボーナス（%） */
    public static final NamespacedKey THREAD_SPELL_POWER = new NamespacedKey(NAMESPACE, "thread_spell_power");

    /** スレッドによるマナコスト削減（%） */
    public static final NamespacedKey THREAD_COST_REDUCTION = new NamespacedKey(NAMESPACE, "thread_cost_reduction");

    /** 防具によるマナリジェンボーナス（config防具用） */
    public static final NamespacedKey ARMOR_REGEN_BONUS = new NamespacedKey(NAMESPACE, "armor_regen_bonus");

    /** 被ダメ時マナ回復量（config防具用） */
    public static final NamespacedKey ARMOR_HIT_MANA_RECOVERY = new NamespacedKey(NAMESPACE, "armor_hit_mana_recovery");

    /** 与ダメ時マナ回復量（config防具用） */
    public static final NamespacedKey ARMOR_DAMAGE_MANA_RECOVERY = new NamespacedKey(NAMESPACE, "armor_damage_mana_recovery");

    /** 飛行スレッドがアクティブかどうか */
    public static final NamespacedKey THREAD_FLIGHT_ACTIVE = new NamespacedKey(NAMESPACE, "thread_flight_active");

    /** 儀式飛行の終了時刻（エポックミリ秒） */
    public static final NamespacedKey RITUAL_FLIGHT_END = new NamespacedKey(NAMESPACE, "ritual_flight_end");

    /** 累計マナ消費量（統計用） */
    public static final NamespacedKey TOTAL_MANA_CONSUMED = new NamespacedKey(NAMESPACE, "total_mana_consumed");
}
