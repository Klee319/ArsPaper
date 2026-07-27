package com.arspaper.enchant;

import com.arspaper.integration.TrinityForgeBridge;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 要件⑥ lapis-cost-reduction: skilltree由来のperkでエンチャント台のラピス消費を個数減する。
 *
 * <p><b>実装方式（要調整）:</b> バニラのエンチャント台はラピスの消費量をAPIで直接操作できず、
 * {@link EnchantItemEvent} 発火後にエンジン側が実消費量({@link EnchantItemEvent#whichButton()}
 * (0/1/2) + 1 = 1〜3個、クリエイティブモードを除く)を消費する仕様に依存している。本リスナーは
 * イベント処理中に削減分のラピスを先に返却し、直後にエンジンが実消費することで結果的に個数減を
 * 実現する「返却型」の回避策であり、確実な直接制御ではない。
 *
 * <p>2026-07-23 追加確定仕様: {@code lapis_cost_reduction} は分数(%割引)ではなく
 * 「軽減するラピス個数」のFLAT値(例 1.0 = 1個軽減)。TF側 enchanting.yml が
 * {@code lapis_cost_reduction: 1} へ変更され、PercentStatNormalizeのRATE_KEYSから除外済みのため、
 * {@link TrinityForgeBridge#tfStatTotal} はそのまま個数を返す。コスト以上の軽減であれば
 * ラピス0個でエンチャント可能(全額免除)であり、上限クランプは不要(actualCost=1〜3が自然な上限)。
 *
 * <p>TF未ロード/軽減0個時はno-op(バニラ挙動そのまま、fail-open)。
 */
public class LapisCostReductionListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        // lapis_cost_reduction は分数ではなく「軽減するラピス個数」のFLAT値。
        // 負値は0にクランプするのみで、上限クランプはしない(コスト超過分はrefundをactualCostで自然に頭打ち)。
        int reduction = (int) Math.floor(
            Math.max(0.0, TrinityForgeBridge.tfStatTotal(player, TrinityForgeBridge.STAT_LAPIS_COST_REDUCTION)));
        if (reduction <= 0) {
            return;
        }

        // バニラの実ラピス消費量はボタン選択段(0/1/2)+1 = 1〜3個の可変。
        // 軽減個数がactualCostを超える場合は全額(actualCost個)を返却し、ラピス0個でエンチャント可能とする。
        int actualCost = event.whichButton() + 1;
        int refund = Math.min(actualCost, reduction);
        if (refund <= 0) {
            return;
        }

        // バニラのラピス消費(実消費actualCost個)が確定する前に削減分を先に返却する。
        // インベントリが満杯の場合はプレイヤー足元にドロップする。
        ItemStack refundStack = new ItemStack(Material.LAPIS_LAZULI, refund);
        var leftover = player.getInventory().addItem(refundStack);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }
}
