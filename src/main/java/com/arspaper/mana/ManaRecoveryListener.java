package com.arspaper.mana;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 本サーバ仕様の戦闘時マナ回復（COMBAT §3.4）を担うリスナー。
 * 設定駆動（ManaConfig.recovery.*）で、被弾時/攻撃時に最大マナに対する%＋固定値を回復する。
 *
 * 注意: 装備（防具/スレッド）由来の ARMOR_HIT_MANA_RECOVERY / ARMOR_DAMAGE_MANA_RECOVERY は
 * ArmorManaListener が別途処理する。本リスナーはサーバ全体の設定値を加算するもので、両者は独立・加算的。
 * 非発動（idle）回復は周期処理のため ManaManager.tickRegeneration 側で扱う。
 */
public class ManaRecoveryListener implements Listener {

    private static final int PERCENT_DIVISOR = 100;
    private final ManaManager manaManager;

    public ManaRecoveryListener(ManaManager manaManager) {
        this.manaManager = manaManager;
    }

    /**
     * 被弾時マナ回復: プレイヤーがエンティティからダメージを受けた時に設定分を回復。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        ManaConfig config = manaManager.getConfig();
        recover(player, config.onHitPercent(), config.onHitFlat());
    }

    /**
     * 攻撃時マナ回復: プレイヤーがエンティティにダメージを与えた時に設定分を回復。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        ManaConfig config = manaManager.getConfig();
        recover(player, config.onAttackPercent(), config.onAttackFlat());
    }

    /**
     * 最大マナに対する%＋固定値を回復する。回復量が0以下なら何もしない。
     */
    private void recover(Player player, int percent, int flat) {
        int amount = flat;
        if (percent > 0) {
            int max = manaManager.getMaxMana(player);
            amount += (int) Math.round(max * percent / (double) PERCENT_DIVISOR);
        }
        if (amount > 0) {
            manaManager.addMana(player, amount);
        }
    }
}
