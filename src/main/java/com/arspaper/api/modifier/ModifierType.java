package com.arspaper.api.modifier;

/**
 * ValhallaMMO等の外部プラグインからArsPaperプレイヤーに適用できるModifier種別。
 *
 * 合算規則:
 * - MAX_MANA, REGEN_RATE                  : 加算 (例: +50, +0.5/sec)
 * - MANA_COST_MULT, MATERIAL_REDUCTION    : 加算 → final = max(0, 1.0 - sum)
 * - MANA_COST_REDUCTION_CHANCE,
 *   MATERIAL_REDUCTION_CHANCE             : 加算 → clamp(0,1) で確率判定
 */
public enum ModifierType {
    /** 最大マナ加算。整数 (1.0=+1) として丸める。 */
    MAX_MANA,
    /** マナ回復速度加算。整数 (1.0=+1) として丸める。 */
    REGEN_RATE,
    /** マナ消費削減割合 (0-1)。 */
    MANA_COST_MULT,
    /** マナ消費0発生確率 (0-1)。 */
    MANA_COST_REDUCTION_CHANCE,
    /** 素材消費削減割合 (0-1)。 */
    MATERIAL_REDUCTION,
    /** 素材消費1個スキップ確率 (0-1)。 */
    MATERIAL_REDUCTION_CHANCE
}
