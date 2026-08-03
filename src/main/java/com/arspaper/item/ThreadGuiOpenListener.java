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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * スレッド枠を持つ装備の {@link ThreadGui} 入口と、その入口の案内。
 *
 * <h2>入口は3つ</h2>
 * <ul>
 *   <li><b>着用防具</b>: スニーク+右クリックで直接 {@link ThreadGui} を開く(従来どおり)。</li>
 *   <li><b>手持ちのツール</b>(つるはし/シャベル/クワ/釣竿/ハサミ/火打石。{@code
 *       ThreadApplicationPolicy#isToolMaterial}。<b>斧は含まない</b> ── 戦闘武器も兼ねるため下記の
 *       通常操作の理屈がそのまま当てはまる): <b>スニーク+真上を見る+右クリック</b>
 *       (2026-08-02 依頼#44)。Bedrock/Geyser はコマンド UX が弱い(スラッシュコマンドの補完が弱く、
 *       画面キーボード入力が重い)ため、頻繁に持ち替えるツールにだけジェスチャー入口を足す。
 *       「真上を見る」を同時に要求するのは、下記(手持ち武器)と同じ理由でスニーク+右クリック単独では
 *       採掘・耕作・伐採などの<b>通常操作と衝突する</b>ため ── ツール使用中に真上(ピッチ -80°以下)を
 *       見ることは実プレイでまず起きない。{@code /ars thread} コマンドも引き続き有効
 *       (ジェスチャーは追加入口であり置き換えではない。真上ジェスチャーが取りづらい環境や、
 *       視点操作が苦手なプレイヤーの保険にする)。</li>
 *   <li><b>手持ちの武器・触媒</b>(剣・斧・弓・クロスボウ・トライデント・鎌・メイス・杖): {@code /ars thread}
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
 * <h2>案内は「持ち替えたとき」だけに寄せる(2026-07-31 F3 指摘1 → F6 指摘3 で縮小)</h2>
 * バインド済みの杖・武器では {@code SpellBindListener} がスニーク判定より前に無条件で
 * キャンセルするため、「キャンセル済みなら黙る」条件を付けた右クリック案内は<b>永久に出ない</b>
 * (杖はバインドして使うものなので、これが一番普通の状態だった)。そこで案内を
 * 右クリックイベントから独立させ、スレッド枠を持つ装備を<b>メインハンドに選択したとき</b>に出す
 * (持ち替え/オフハンド入れ替え/ホットバースワップ)。スパム防止は {@link ThreadSlotHintPolicy} の
 * 二重ガード(間隔30秒 + 同一アイテム1セッション1回)。
 *
 * <p><b>⚠️ スニーク+右クリックの案内は撤去した(F6 指摘3)</b>: 一時的に
 * 「キャンセル済みでも案内は出す」形にしていたが、<b>スニーク+右クリックは通常操作</b>である ──
 * 弓(5件)・クロスボウ(5件)・トライデント(5件)・斧(4件)・鍬はスレッド枠を持ち、
 * スニーク狙撃やスニーク耕作は普通の遊び方なので、5秒間隔のガードでは
 * <b>TF の EXP/会心アクションバー({@code SkillExpFeedbackService} / {@code CombatListener})を
 * 5秒ごとに無限に上書きし続ける</b>。「自分から試した操作だから毎回応答したい」という前提が
 * この操作には成り立たない。持ち替え時の案内(30秒 + 同一アイテム1セッション1回)と
 * {@code /ars help} で発見経路は足りているので、右クリック側は<b>案内も GUI も出さない</b>
 * (防具の GUI 起動だけが残る)。<b>この段落はツール以外の手持ち武器に限る</b> ── ツールは
 * 「真上を見る」という平常時に起きない姿勢を追加で要求しているため、無条件スニーク+右クリックと
 * 同じ誤爆リスクを持たない(上の「入口は3つ」を参照)。
 *
 * <p>コマンド一覧側の発見経路は {@code /ars help}
 * ({@link com.arspaper.command.handlers.HelpCommands})。
 */
public final class ThreadGuiOpenListener implements Listener {

    private final JavaPlugin plugin;
    private final ThreadSlotHintPolicy hintPolicy = new ThreadSlotHintPolicy();
    /**
     * 次tickの案内評価を待っているプレイヤー。シフトクリック連打で {@code runTask} が
     * 積み上がるのを1件へ畳む(F6 指摘5)。イベントはメインスレッドだが
     * {@code forget} 系と同じ流儀で並行安全な集合にしておく。
     */
    private final Set<UUID> pendingSelectHint = ConcurrentHashMap.newKeySet();

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
        boolean armor = isArmorPiece(item);
        if (!armor) {
            if (!isToolItem(item)) {
                // 手持ち武器・触媒はこの経路では何もしない ── GUI は /ars thread が唯一の
                // 入口で、案内も出さない。スニーク+右クリックは弓/クロスボウ/トライデント/斧の
                // 通常操作なので、ここで喋ると TF の EXP/会心アクションバーを潰し続ける(F6 指摘3)。
                // 発見経路は持ち替え時の案内(hintForSelectedItem)と /ars help。
                return;
            }
            if (!isLookingStraightUp(player)) {
                // ツールは採掘・耕作・伐採等でスニーク+右クリックを常用するため、「真上を見ている」
                // ことも同時に要求して誤爆を防ぐ(依頼#44)。この条件を満たさない間は
                // 通常のツール操作としてそのまま素通りさせる(GUI も案内も出さない)。
                return;
            }
        }
        int slots = effectiveThreadSlots(item, player);
        if (slots <= 0) {
            return;
        }
        if (event.isCancelled()) {
            // 他リスナー(SpellBindListener 等)が既に処理済み = 呪文が出た。GUI は開かない(F3 指摘2)。
            return;
        }
        // F6 指摘1(HIGH): ItemMeta はスタック単位なので、2個以上のスタックへ装着すると複製/データ喪失。
        // 防具は通常スタックしないが、経路として同じガードを通す(将来スタック可能な防具材質が
        // 増えても穴が開かない)。詳細は ThreadApplicationPolicy#isStackTooLargeToSocket。
        if (ThreadApplicationPolicy.isStackTooLargeToSocket(item.getAmount())) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "同じ装備が" + item.getAmount() + "個重なっています。"
                            + "スレッドは1個ずつしか装着できません（1個だけ持ってから開いてください）。",
                    NamedTextColor.RED));
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
        // F6 指摘5: シフトクリックは「どのインベントリでも」該当するので、二重チェストを整理すると
        // 1クリックごとに runTask が積まれる(約50件)。同一プレイヤーの評価は次tickの1回に畳む。
        if (!pendingSelectHint.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingSelectHint.remove(player.getUniqueId());
            if (player.isOnline()) {
                hintForSelectedItem(player, player.getInventory().getItemInMainHand());
            }
        });
    }

    /** 常駐マップにオフラインプレイヤーを溜めない。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hintPolicy.forget(event.getPlayer().getUniqueId());
        pendingSelectHint.remove(event.getPlayer().getUniqueId());
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
            // F6 指摘5: 枠を持たない品はここで「評価済み」として覚える(負のキャッシュ)。
            // これが無いと lastSelectHintAt が一度も書かれないため isSelectHintSuppressed が
            // 永久に false を返し、スレッド枠を1つも持たないプレイヤーのホットバー操作ごとに
            // TF item-stats のフル解決が走り続ける(足切りが構造的に働かない)。
            // 儀式で枠が増えた同一 material#CMD は再ログインまで案内されないが、
            // 儀式そのものが枠の存在を伝えるので発見経路は保たれる。
            hintPolicy.markSelectHintEvaluated(player.getUniqueId(), itemKey);
            return;
        }
        if (!hintPolicy.allowSelectHint(player.getUniqueId(), itemKey, now)) {
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

    private static boolean isToolItem(ItemStack item) {
        return ThreadApplicationPolicy.isToolMaterial(item.getType());
    }

    /**
     * プレイヤーがほぼ真上(ピッチ -80°以下。-90°が真上)を見ているか。
     *
     * <p>閾値を -90°ちょうどにしないのは、Bedrock/Geyser 側の視点入力が Java 版ほど滑らかでなく
     * ちょうど真上でピタッと止めにくいため(ヒットボックス的な余裕を持たせる)。-80°は
     * 「見上げてはいるが地平線付近」を除外しつつ、通常のツール操作(採掘・耕作は水平〜下向き、
     * 釣りは水平、火打石は目の前のブロック)では自然に到達しない角度として選んだ。
     */
    private static boolean isLookingStraightUp(Player player) {
        return player.getLocation().getPitch() <= -80f;
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
