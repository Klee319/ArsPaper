package com.arspaper.item;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 装着スレッドの item-stats から出てきたステのうち、<b>マナ系だけを別の受け皿へ振り分ける</b>
 * 純粋な仕分け (2026-08-03)。
 *
 * <h2>なぜ必要か — 書いても無言で死ぬキーがあった</h2>
 * <p>{@code ArmorManaListener#collectThreadsInto} は装着スレッド1個ごとに
 * {@code TrinityForgeBridge#resolveThreadStats} を呼び、その結果を<b>まるごと</b>
 * {@code totals.combatStats} へ足していた。そこは最終的に
 * {@code TrinityForgeBridge#writeAddonCombatStats} → TF の {@code AddonCombatStats}(PDC)へ流れ、
 * TF 側は {@code PlayerStatAggregator#aggregate} / {@code PlayerDefenseResolver} という
 * <b>戦闘の集計にだけ</b>畳み込む。
 *
 * <p>ところがマナの消費側はそこを一切読まない:
 * <ul>
 *   <li>{@code mana_bonus} / {@code mana_regen} … {@code ManaManager} が
 *       {@code ManaKeys.ARMOR_*} / {@code THREAD_*} の PDC を読む。</li>
 *   <li>{@code hit_mana_recovery} / {@code damage_mana_recovery} … 同じく PDC 経由。
 *       非装備分は TF の {@code tfNonItemStatTotal} で足すが、
 *       <b>あちらは addon チャネルを意図的に除外している</b>
 *       ({@code PlayerStatAggregator#nonItemContribution} の javadoc)。</li>
 *   <li>{@code mana_cost_reduction_percent} … {@code SpellCaster} が触媒 +
 *       {@code tfNonItemStatTotal} から取る。ここも addon を通らない。</li>
 * </ul>
 * つまり <b>{@code stats/item-stats.yml} のスレッド行にマナ系を書くと、エラーも警告も出ないまま
 * 何も起きなかった</b>。「マナ増幅のスレッド」の厳選にマナを乗せる(要件: 名称に合ったステータスに
 * 重きを置く)には、この仕分けが前提になる。
 *
 * <h2>やっていること</h2>
 * <p>{@link #extract} は渡されたマップから下記5キーを<b>取り除き</b>、
 * threads.yml 由来の値と同じ整数カウンタへ足せる形にして返す。取り除くのは、
 * 残したままだと同じ値が addon チャネルにも載り「片方は効き、片方は死んでいる」という
 * 読み手に判別不能な状態になるため(将来 TF 側が addon から同キーを読み始めたら二重取りになる)。
 *
 * <p>扱わないキーと理由:
 * <ul>
 *   <li>{@code mana_cost_reduction_flat} … スレッド側に対応する整数カウンタが無い
 *       ({@code ManaKeys} は割合の {@code THREAD_COST_REDUCTION} だけ)。受け皿を作らずに
 *       ここへ足すと単位が混ざるので、対応しないことを明示して素通りさせる
 *       (= 従来どおり addon チャネルへ行き、効かない)。</li>
 *   <li>{@code mana_max_percent} / {@code regen_percent} … threads.yml 固有のレバーで
 *       TF の item-stats 語彙({@code stats/lore.yml})に存在しないため、そもそも出てこない。</li>
 * </ul>
 *
 * <p>Bukkit を必要としない純関数だけを置く(このフォークのテスト基盤は MockBukkit を持たない)。
 */
public final class ThreadManaStatRouting {

    /** 最大マナ加算。TF canonical キー(区切りは {@code _})。 */
    public static final String KEY_MANA_BONUS = "mana_bonus";
    /** マナ回復速度加算(/tick)。 */
    public static final String KEY_MANA_REGEN = "mana_regen";
    /** 被弾時マナ回復。 */
    public static final String KEY_HIT_MANA_RECOVERY = "hit_mana_recovery";
    /** 与ダメージ時マナ回復。 */
    public static final String KEY_DAMAGE_MANA_RECOVERY = "damage_mana_recovery";
    /** 詠唱コスト軽減率。item-stats 側は分数(0.06 = 6%)、スレッドのカウンタ側は整数%。 */
    public static final String KEY_MANA_COST_REDUCTION_PERCENT = "mana_cost_reduction_percent";

    /** {@link #extract} が取り除くキー(canonical)。 */
    public static final Set<String> ROUTED_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(java.util.List.of(
            KEY_MANA_BONUS,
            KEY_MANA_REGEN,
            KEY_HIT_MANA_RECOVERY,
            KEY_DAMAGE_MANA_RECOVERY,
            KEY_MANA_COST_REDUCTION_PERCENT)));

    private ThreadManaStatRouting() {
    }

    /**
     * 仕分け結果。すべて threads.yml 由来の値と同じ単位・同じ整数型。
     *
     * @param manaBonus            最大マナ加算
     * @param manaRegen            マナ回復速度加算(/tick)
     * @param hitManaRecovery      被弾時マナ回復
     * @param damageManaRecovery   与ダメージ時マナ回復
     * @param costReductionPercent 詠唱コスト軽減(<b>整数%</b>。分数から ×100 して丸めた値)
     */
    public record Deltas(int manaBonus, int manaRegen, int hitManaRecovery,
                         int damageManaRecovery, int costReductionPercent) {

        static final Deltas NONE = new Deltas(0, 0, 0, 0, 0);
    }

    /**
     * {@code stats} からマナ系5キーを<b>破壊的に取り除き</b>、整数カウンタ用の {@link Deltas} を返す。
     *
     * <p>{@code null} / 空マップ / 対象キー無しはすべて {@link Deltas} が全0(fail-open)。
     * 非有限値(NaN / Inf)は 0 として扱いつつキー自体は取り除く ── 残すと addon チャネルへ
     * 流れて TF 側の encode で落とされるだけなので、ここで消しておくほうが挙動が読みやすい。
     *
     * @param stats {@code resolveThreadStats} が返した caller-owned な可変マップ
     */
    public static Deltas extract(Map<String, Double> stats) {
        if (stats == null || stats.isEmpty()) {
            return Deltas.NONE;
        }
        int manaBonus = takeRounded(stats, KEY_MANA_BONUS, 1.0);
        int manaRegen = takeRounded(stats, KEY_MANA_REGEN, 1.0);
        int hitRecovery = takeRounded(stats, KEY_HIT_MANA_RECOVERY, 1.0);
        int damageRecovery = takeRounded(stats, KEY_DAMAGE_MANA_RECOVERY, 1.0);
        // item-stats は割合キーを分数で持つ(0.06 = 6%)。スレッドのカウンタは threads.yml の
        // cost-reduction と同じ整数%なので、ここで単位を合わせる。
        int costReduction = takeRounded(stats, KEY_MANA_COST_REDUCTION_PERCENT, 100.0);
        return new Deltas(manaBonus, manaRegen, hitRecovery, damageRecovery, costReduction);
    }

    private static int takeRounded(Map<String, Double> stats, String key, double scale) {
        Double raw = stats.remove(key);
        if (raw == null || !Double.isFinite(raw)) {
            return 0;
        }
        return (int) Math.round(raw * scale);
    }
}
