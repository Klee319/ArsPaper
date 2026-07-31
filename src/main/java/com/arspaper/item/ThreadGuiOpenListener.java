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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * スレッド枠を持つ装備の {@link ThreadGui} 入口と、その入口の案内。
 *
 * <h2>入口は2つ</h2>
 * <ul>
 *   <li><b>着用防具</b>: スニーク+右クリックで直接 {@link ThreadGui} を開く(従来どおり)。</li>
 *   <li><b>手持ち装備(武器・触媒・ツール)</b>: {@code /ars thread}
 *       ({@link com.arspaper.command.handlers.ThreadCommands})。右クリックでは開かない ──
 *       {@link com.arspaper.spell.SpellBindListener}(NORMAL 優先度)が同じ右クリックで呪文を
 *       発動させるので「呪文が飛びつつ画面が開く」二重発火になる
 *       ({@code SpellWand}/{@code Wand}/{@code SpellBook}/{@code CustomItemListener}/
 *       {@code RitualCore}/{@code Waystone} も独自のスニーク判定を持つ)。</li>
 * </ul>
 * 枠数は item-stats の {@code thread_slots} が正のときのみ(枠の拡張は
 * {@link com.arspaper.ritual.effect.ThreadSlotExpandRitualEffect} が装備自身へ書き込む)。
 *
 * <h2>防具側でも {@code isCancelled()} を見る(2026-07-31 F3 指摘2)</h2>
 * 「防具にはバインドできないから二重発火しない」は成り立たない。
 * {@code SpellBindListener#canBind} が弾くのは {@code arspaper:custom_item_id} を持つ品だけで、
 * TF カタログ防具は {@code trinityforge:catalog_id} なので通る(しかも {@code /ars bind} は
 * バインド先を<b>オフハンド</b>から取るので、兜をオフハンド・魔導書をメインハンドに持てば成立する)。
 * その防具を手に持ってスニーク+右クリックすれば NORMAL で呪文が出て、
 * このリスナー(HIGH / {@code ignoreCancelled = false})が続けて GUI を開いてしまう。
 * <b>呪文が出たなら GUI は開かない</b>のが正しいので、開く直前でキャンセル済みかを見る。
 *
 * <h2>案内は右クリックに依存させない(2026-07-31 F3 指摘1)</h2>
 * バインド済みの杖・武器では {@code SpellBindListener} がスニーク判定より前に無条件で
 * キャンセルするため、「キャンセル済みなら黙る」条件を付けた案内は<b>永久に出ない</b>
 * (杖はバインドして使うものなので、これが一番普通の状態だった)。そこで
 * <ol>
 *   <li>スレッド枠を持つ装備を<b>メインハンドに選択したとき</b>にも案内する
 *       (持ち替え/オフハンド入れ替え/ホットバースワップ)。スパム防止は
 *       {@link ThreadSlotHintPolicy} の二重ガード(間隔 + 同一アイテム1セッション1回)。</li>
 *   <li>スニーク+右クリックの案内からは {@code isCancelled()} 条件を<b>外した</b>
 *       (呪文が出ても案内だけは出す。アクションバーなので操作を邪魔しない)。</li>
 * </ol>
 * コマンド一覧側の発見経路は {@code /ars help}
 * ({@link com.arspaper.command.handlers.HelpCommands})。
 */
public final class ThreadGuiOpenListener implements Listener {

    private final JavaPlugin plugin;
    private final ThreadSlotHintPolicy hintPolicy = new ThreadSlotHintPolicy();

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
            // 【キャンセル済みでも案内は出す】── バインド済みの杖は SpellBindListener が常に
            // キャンセルするので、ここで黙ると案内が永久に出ない(F3 指摘1)。
            sendHandheldHint(player, slots);
            return;
        }
        if (event.isCancelled()) {
            // 他リスナー(SpellBindListener 等)が既に処理済み = 呪文が出た。GUI は開かない(F3 指摘2)。
            return;
        }
        event.setCancelled(true);
        openForHeldItem(player, item);
    }

    /**
     * ホットバーの選択スロット変更(ホイール/数字キー)で、選んだ装備にスレッド枠があれば案内する。
     * {@code getNewSlot()} のスロットの中身はこの時点で確定しているので遅延は不要。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        hintForSelectedItem(event.getPlayer(),
                event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    /** F キーのメインハンド/オフハンド入れ替えでも案内する(メインハンドに来る側を見る)。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        hintForSelectedItem(event.getPlayer(), event.getMainHandItem());
    }

    /**
     * インベントリ画面で「選択中のホットバー枠の装備を入れ替える」経路。
     * {@link PlayerItemHeldEvent} は選択スロットが変わらないので飛ばない
     * ({@code ArmorManaListener} がステ再計算を足したのと同じ経路)。
     * クリック確定後の中身を見る必要があるので 1 tick 後に評価する。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int heldSlot = player.getInventory().getHeldItemSlot();
        boolean heldSlotTouched = event.getClickedInventory() == player.getInventory()
                && event.getSlot() == heldSlot;
        boolean hotbarSwap = event.getClick() == ClickType.NUMBER_KEY
                && event.getHotbarButton() == heldSlot;
        if (!heldSlotTouched && !hotbarSwap && !event.isShiftClick()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                hintForSelectedItem(player, player.getInventory().getItemInMainHand());
            }
        });
    }

    /** 常駐マップにオフラインプレイヤーを溜めない。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hintPolicy.forget(event.getPlayer().getUniqueId());
    }

    /**
     * メインハンドに来た装備がスレッド枠を持つなら案内する。
     * 着用防具の材質(手に持っている兜など)は既存のスニーク+右クリックで開けるので、
     * 手持ち専用の {@code /ars thread} 案内は非防具に限る。
     */
    private void hintForSelectedItem(Player player, ItemStack selected) {
        if (selected == null || selected.getType().isAir() || isArmorPiece(selected)) {
            return;
        }
        String itemKey = itemKeyOf(selected);
        long now = System.currentTimeMillis();
        // 枠数の解決は TF item-stats のフル解決なので、抑止されているなら先に降りる。
        if (hintPolicy.isSelectHintSuppressed(player.getUniqueId(), itemKey, now)) {
            return;
        }
        int slots = effectiveThreadSlots(selected, player);
        if (slots <= 0) {
            return;
        }
        if (!hintPolicy.allowSelectHint(player.getUniqueId(), itemKey, now)) {
            return;
        }
        sendHint(player, slots);
    }

    /** 手持ち装備でスレッド枠を持つ品に同じ手つきをしたとき、コマンドの入口を教える。 */
    private void sendHandheldHint(Player player, int slots) {
        if (!hintPolicy.allowInteractHint(player.getUniqueId(), System.currentTimeMillis())) {
            return;
        }
        sendHint(player, slots);
    }

    private static void sendHint(Player player, int slots) {
        player.sendActionBar(Component.text(
                "スレッド枠 " + slots + "枠 — /ars thread で装着", NamedTextColor.AQUA));
    }

    /**
     * メインハンドの品で GUI を開く。<b>スロット番号を必ず渡す</b> ──
     * {@link ThreadGui} は装着の直前にそのスロットの中身と対象の同一性を再確認して
     * 「対象を手から離した状態でスレッドを溶かす」事故を止める(F3 指摘5)。
     */
    private void openForHeldItem(Player player, ItemStack item) {
        new ThreadGui(player, item, plugin, player.getInventory().getHeldItemSlot()).open();
    }

    /** 「同一アイテム」判定のキー(material + CustomModelData)。 */
    private static String itemKeyOf(ItemStack item) {
        Integer cmd = null;
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            cmd = item.getItemMeta().getCustomModelData();
        }
        return ThreadSlotHintPolicy.itemKey(item.getType().name(), cmd);
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
