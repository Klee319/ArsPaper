package com.arspaper.item;

import com.arspaper.gui.ThreadGui;
import com.arspaper.integration.TrinityForgeBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 着用防具向けスレッドGUI開放。
 * armors.yml / ConfigurableArmor 撤去後も、スニーク+右クリックで {@link ThreadGui} を開けるようにする。
 * 枠数は item-stats の {@code thread_slots} が正のときのみ(枠の拡張は
 * {@link com.arspaper.ritual.effect.ThreadSlotExpandRitualEffect} が装備自身へ書き込む)。
 *
 * <h2>手持ち装備(武器・触媒・ツール)は右クリックでは開かない — 二重発火するため</h2>
 * {@code thread_slots} は防具だけの属性ではない(武器・触媒にも付く)が、この右クリック
 * トリガーをそのまま非防具へ広げると<b>他のスニーク+右クリック処理と同時に走る</b>。
 * とくに {@link com.arspaper.spell.SpellBindListener}(NORMAL 優先度)は
 * バインド済み触媒/武器の右クリックで呪文を発動させるので、このリスナー(HIGH)が
 * 続けて GUI を開くと「呪文が飛びつつ画面が開く」ことになる
 * ({@code SpellWand}/{@code Wand}/{@code SpellBook}/{@code CustomItemListener}/
 * {@code RitualCore}/{@code Waystone} も独自のスニーク判定を持つ)。
 *
 * <p>そのため手持ち装備は専用コマンド {@code /ars thread} を入口にし
 * ({@link com.arspaper.command.handlers.ThreadCommands})、ここでは
 * <b>同じ手つきを試した人へその案内だけを出す</b>。案内はアクションバーなので
 * 操作を邪魔せず、他リスナーが既にイベントをキャンセルしている(=呪文が出た)ときは出さない。
 */
public final class ThreadGuiOpenListener implements Listener {

    /** 同一プレイヤーへ案内を再送しない間隔(ms)。スニーク中の連続右クリックで溢れさせない。 */
    private static final long HINT_COOLDOWN_MS = 5_000L;

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastHintAt = new ConcurrentHashMap<>();

    public ThreadGuiOpenListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        int slots = effectiveThreadSlots(item, player);
        if (slots <= 0) {
            return;
        }
        if (!isArmorPiece(item)) {
            // 手持ち装備: GUI は開かず /ars thread へ誘導する(上の javadoc の二重発火対策)。
            // 既に他リスナーがキャンセルしている場合(=呪文が発動した)は黙る。
            if (!event.isCancelled()) {
                sendHandheldHint(player, slots);
            }
            return;
        }
        event.setCancelled(true);
        new ThreadGui(player, item, plugin).open();
    }

    /** 手持ち装備でスレッド枠を持つ品に同じ手つきをしたとき、コマンドの入口を教える。 */
    private void sendHandheldHint(Player player, int slots) {
        long now = System.currentTimeMillis();
        Long previous = lastHintAt.get(player.getUniqueId());
        if (previous != null && now - previous < HINT_COOLDOWN_MS) {
            return;
        }
        lastHintAt.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text(
                "スレッド枠 " + slots + "枠 — /ars thread で装着", NamedTextColor.AQUA));
    }

    private static boolean isArmorPiece(ItemStack item) {
        return ThreadApplicationPolicy.isArmorSlotMaterial(item.getType());
    }

    private static int effectiveThreadSlots(ItemStack item, Player player) {
        try {
            Map<String, Double> stats = TrinityForgeBridge.resolveFullItemStats(item);
            return TrinityForgeBridge.tfEffectiveThreadSlotCap(stats, player);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
