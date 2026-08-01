package com.arspaper.mana;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * 本サーバ仕様の戦闘時マナ回復（COMBAT §3.4）を担うリスナー。
 * 設定駆動（ManaConfig.recovery.*）で、被弾時/攻撃時に最大マナに対する%＋固定値を回復する。
 *
 * 注意: 固定量の回復(hit_mana_recovery / damage_mana_recovery)は ArmorManaListener が一本化して
 * 処理する(装備分＋パーク/役職/永続バフ/base-stats の非装備分)。本リスナーは
 * 「最大マナの何%」という別意味の回復だけを担当する。両者は独立・加算的。
 * 非発動（idle）回復は周期処理のため ManaManager.tickRegeneration 側で扱う。
 */
public class ManaRecoveryListener implements Listener {

    /**
     * 攻撃時マナ回復の対象とする近接攻撃のDamageCause。
     *
     * <p>TrinityForgeBridge#applyMagicDamage は cause=MAGIC の DamageSource で
     * caster を causing/direct entity に設定するため、スペル命中も
     * damager=Player の EntityDamageByEntityEvent として観測される。
     * ここを近接攻撃のcauseのみに限定することで、スペル命中による
     * 攻撃時マナ回復の誘発（自己還流的な無限詠唱の芽）を防ぐ。
     */
    private static final Set<EntityDamageEvent.DamageCause> MELEE_ATTACK_CAUSES = EnumSet.of(
        EntityDamageEvent.DamageCause.ENTITY_ATTACK,
        EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
    );

    private final ManaManager manaManager;

    public ManaRecoveryListener(ManaManager manaManager) {
        this.manaManager = manaManager;
    }

    /**
     * 被弾時マナ回復: プレイヤーがエンティティからダメージを受けた時に設定分を回復。
     *
     * <p>攻撃側({@link #onPlayerDealDamage})と対称に MONITOR で最終確定後に判定し、
     * {@code getFinalDamage()>0} のみ回復対象とする（後続ハンドラでのキャンセル/吸収/無効化を反映し、
     * 0ダメージ被弾でのマナ回復誘発を防ぐ）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFinalDamage() <= 0) return;
        recover(player, ManaBaseStats.onHitPercent());
    }

    /**
     * 攻撃時マナ回復: プレイヤーが近接攻撃でエンティティにダメージを与えた時に設定分を回復。
     *
     * <p>MONITORで最終確定後に判定し、getFinalDamage()&gt;0のみ回復対象とする
     * （後続ハンドラでのキャンセル/ダメージ無効化を確実に反映するため）。
     * cause判定でスペル由来（MAGIC等）のダメージは対象外とする。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!MELEE_ATTACK_CAUSES.contains(event.getCause())) return;
        if (event.getFinalDamage() <= 0) return;
        recover(player, ManaBaseStats.onAttackPercent());
    }

    /**
     * 最大マナに対する%分を回復する。回復量が0以下なら何もしない。
     *
     * <p>2026-07-29(重複ステ間引き): 固定値(旧 mana-onhit-flat / mana-onattack-flat)は
     * {@link com.arspaper.item.ArmorManaListener} の hit_mana_recovery / damage_mana_recovery と
     * 完全に重複していたため廃止し、ここは%分専用になった。
     *
     * @param percent 分数[0,1](例 0.03 = 3%)。{@link ManaBaseStats} が既に正規化済みの値を返す。
     */
    private void recover(Player player, double percent) {
        if (percent <= 0) return;
        int max = manaManager.getMaxMana(player);
        int amount = (int) Math.round(max * percent);
        if (amount > 0) {
            manaManager.addMana(player, amount);
        }
    }
}
