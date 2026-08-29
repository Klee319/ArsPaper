package com.arspaper.spell;

/**
 * 詠唱マナ消費の割合軽減。Bukkit 非依存の純関数。
 *
 * <p>item-stats の {@code mana-cost-reduction-percent} は分数(0.15 = 15%)。
 * スレッドカウンタと触媒 yml の percent は整数%。単位を取り違えて ×100 すると
 * 15 が 1500 → キャップ 100% → 消費マナ 1 になる。
 */
public final class SpellManaCost {

    private SpellManaCost() {
    }

    /**
     * 分数 0.15 もパーセントポイント 15 も整数 15% に揃える。
     */
    public static int toPercent(double raw) {
        if (!Double.isFinite(raw)) {
            return 0;
        }
        double abs = Math.abs(raw);
        double fraction = abs > 1.0 ? raw / 100.0 : raw;
        int pct = (int) Math.round(fraction * 100.0);
        if (pct < 0) {
            return 0;
        }
        return Math.min(100, pct);
    }

    /**
     * {@code baseCost * (1 - reductionPercent/100)} を四捨五入し、最低 1。
     * 合計 15%・基礎 200 なら 170。
     */
    public static int afterPercent(int baseCost, int reductionPercent) {
        if (baseCost <= 0) {
            return 0;
        }
        int pct = Math.min(100, Math.max(0, reductionPercent));
        int reduced = baseCost - (int) Math.round(baseCost * pct / 100.0);
        return Math.max(1, reduced);
    }
}
