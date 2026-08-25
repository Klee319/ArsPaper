package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.item.impl.SourceBerry;
import com.arspaper.util.PdcHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * ソースベリーの食べるイベントを監視し、マナを回復する。
 *
 * <p><b>2026-08-25 (W-258): 満腹度が満タンでも使えるようにした。</b>
 * それまでこのリスナーは {@link PlayerItemConsumeEvent} だけを見ていたが、
 * <b>バニラは満腹度が満タンのとき食事そのものを始めない</b>（{@code canAlwaysEat} を持つ
 * 金のリンゴ等だけが例外）。ソースベリーの素材は GLOW_BERRIES なので、
 * <b>腹が減っていない間はイベントが一度も発火せず、売りであるマナ回復が使えなかった</b>
 * （ユーザー報告「強みであるマナ回復が機能してない」）。
 *
 * <p>直し方は<b>「バニラが食べさせない状況だけ、こちらで消費する」</b>の一点。
 * {@link #onInteract} は満腹度が満タンのときにだけ働き、それ以外は何もしない
 * ── バニラが食べる状況で両方が動くと <b>1回のクリックで2個消える</b>。
 * この境界（バニラが食事を始める条件 = 満腹度 &lt; 20）は
 * {@link SourceBerryConsumePolicy} に純関数で切り出してテストしてある。
 *
 * <p><b>アイテムの component を書き換える方式（{@code food.can_always_eat}）は採らなかった</b>:
 * component はスタックに焼き付くので<b>既に配ってあるソースベリーは永久に直らない</b>。
 * 経路を1本足すほうが、配布済みの個体にもその場で効く。
 */
public class SourceBerryListener implements Listener {

    private final ArsPaper plugin;

    public SourceBerryListener(ArsPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!isSourceBerry(event.getItem())) return;
        restoreMana(event.getPlayer());
    }

    /**
     * 満腹度が満タンでバニラが食事を始めない状況だけ、こちらで1個消費してマナを回復する。
     *
     * <p>⚠ <b>手（メイン/オフ）の判定を必ず入れる</b>: {@link PlayerInteractEvent} は
     * 1回のクリックで両手ぶん発火しうるので、入れないと1クリックで2個消えることがある。
     * ⚠ <b>ブロックへの右クリックでも発火する</b>（額縁・チェスト等）。ブロック操作が
     * 取り消されていないときだけ食べる ── そうしないとチェストを開けるたびに実を失う。
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.ALLOW
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable()) {
            // チェスト・作業台などの「開く」操作を食事で潰さない。
            return;
        }
        ItemStack item = event.getItem();
        if (!isSourceBerry(item)) return;

        Player player = event.getPlayer();
        if (!SourceBerryConsumePolicy.needsManualConsume(player.getFoodLevel())) {
            // バニラがこのあと食事を始める → PlayerItemConsumeEvent 側に任せる（二重消費の防止）。
            return;
        }
        // ここから先はバニラが何もしない経路なので、消費もこちらで行う。
        event.setCancelled(true);
        restoreMana(player);
        item.setAmount(item.getAmount() - 1);
    }

    private boolean isSourceBerry(ItemStack item) {
        if (item == null) return false;
        var customId = PdcHelper.getCustomItemId(item);
        return customId.isPresent() && "source_berry".equals(customId.get());
    }

    private void restoreMana(Player player) {
        int current = plugin.getManaManager().getCurrentMana(player);
        int max = plugin.getManaManager().getMaxMana(player);
        int restore = Math.min(SourceBerry.MANA_RESTORE, max - current);

        if (restore > 0) {
            plugin.getManaManager().addMana(player, restore);
            player.sendMessage(Component.text("マナを" + restore + "回復しました", NamedTextColor.AQUA));
        } else {
            player.sendMessage(Component.text("マナは満タンです", NamedTextColor.GRAY));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 1.5f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
            8, 0.3, 0.3, 0.3, 0.05);
    }
}
