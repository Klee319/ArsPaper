package com.arspaper.mana;

import com.arspaper.integration.TrinityForgeBridge;

/**
 * マナ初期値の唯一の読み出し口(2026-07-25 config editor T2)。
 *
 * <p>従来 {@code config.yml} の {@code mana.default-max} / {@code mana.default-regen-rate} /
 * {@code mana.regen-interval-ticks} / {@code mana.recovery.*} で設定していた初期値を、
 * TrinityForge の「プレイヤー基礎ステータス」({@code combat/base-stats.yml}) へ移設した。
 * ここから読む値は常に TrinityForge 側が最新の設定の権威であり、{@link TrinityForgeBridge#manaBaseStat}
 * 経由でTF未ロード時にのみ下記フォールバック定数(移設前のArsPaper既定値)へ落ちる(fail-open)。
 *
 * <p>%系(on-hit / on-attack / idle-bonus)は TF 側で PERCENT stat として保存されるため、ここで返す値は
 * 既に分数(0.03 = 3%)である。旧 {@code PERCENT_DIVISOR} による /100 補正は不要になった点に注意。
 */
public final class ManaBaseStats {

    // 移設前の ArsPaper config.yml 既定値(TF未ロード時のみ使用するフォールバック)。
    private static final int FALLBACK_DEFAULT_MAX = 100;
    private static final int FALLBACK_DEFAULT_REGEN_RATE = 5;
    private static final int FALLBACK_REGEN_INTERVAL_TICKS = 20;
    private static final double FALLBACK_ON_HIT_PERCENT = 0.03;
    private static final int FALLBACK_ON_HIT_FLAT = 0;
    private static final double FALLBACK_ON_ATTACK_PERCENT = 0.03;
    private static final int FALLBACK_ON_ATTACK_FLAT = 0;
    private static final int FALLBACK_IDLE_SECONDS = 5;
    private static final double FALLBACK_IDLE_BONUS_PERCENT = 0.01;
    private static final int FALLBACK_IDLE_BONUS_FLAT = 0;

    private ManaBaseStats() {
    }

    public static int defaultMax() {
        return round(TrinityForgeBridge.manaBaseStat("mana-max-base", FALLBACK_DEFAULT_MAX));
    }

    public static int defaultRegenRate() {
        return round(TrinityForgeBridge.manaBaseStat("mana-regen-base", FALLBACK_DEFAULT_REGEN_RATE));
    }

    public static int regenIntervalTicks() {
        int v = round(TrinityForgeBridge.manaBaseStat("mana-regen-interval-ticks", FALLBACK_REGEN_INTERVAL_TICKS));
        return v > 0 ? v : FALLBACK_REGEN_INTERVAL_TICKS;
    }

    /** 分数[0,1]で返す(例 0.03 = 3%)。 */
    public static double onHitPercent() {
        return TrinityForgeBridge.manaBaseStat("mana-onhit-percent", FALLBACK_ON_HIT_PERCENT);
    }

    public static int onHitFlat() {
        return round(TrinityForgeBridge.manaBaseStat("mana-onhit-flat", FALLBACK_ON_HIT_FLAT));
    }

    /** 分数[0,1]で返す(例 0.03 = 3%)。 */
    public static double onAttackPercent() {
        return TrinityForgeBridge.manaBaseStat("mana-onattack-percent", FALLBACK_ON_ATTACK_PERCENT);
    }

    public static int onAttackFlat() {
        return round(TrinityForgeBridge.manaBaseStat("mana-onattack-flat", FALLBACK_ON_ATTACK_FLAT));
    }

    public static int idleSeconds() {
        return round(TrinityForgeBridge.manaBaseStat("mana-idle-seconds", FALLBACK_IDLE_SECONDS));
    }

    /** 分数[0,1]で返す(例 0.01 = 1%)。 */
    public static double idleBonusPercent() {
        return TrinityForgeBridge.manaBaseStat("mana-idle-bonus-percent", FALLBACK_IDLE_BONUS_PERCENT);
    }

    public static int idleBonusFlat() {
        return round(TrinityForgeBridge.manaBaseStat("mana-idle-bonus-flat", FALLBACK_IDLE_BONUS_FLAT));
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
