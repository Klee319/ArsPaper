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
    /**
     * 最大マナ加算。ArsPaperの最大マナは整数管理のため、合計 (sum) を round して整数に丸める。
     * 例: 30.4 と 20.3 を加算→ round(50.7) = 51 が反映される。
     */
    MAX_MANA,
    /**
     * マナ回復速度加算。ArsPaperの回復は 1tickあたり整数値のため、
     * 合計を round して整数に丸める (fractional rate は非対応)。
     * 細粒度な調整が必要な場合は regen-interval-ticks を縮める方針。
     */
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
