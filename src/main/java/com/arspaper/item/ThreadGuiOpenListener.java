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
 * <h2>入口は2種類 + コマンド</h2>
 * <ul>
 *   <li><b>着用防具</b>: スニーク+右クリックで直接 {@link ThreadGui} を開く(従来どおり。
 *       視線とジャンプの条件は無い)。</li>
 *   <li><b>スレッド枠を持つ非防具装備全般</b>(つるはし/シャベル/クワ/釣竿/ハサミ/火打石はもちろん、
 *       <b>斧・剣・弓・クロスボウ・トライデント・鎌・メイス・杖・触媒も含む</b>):
 *       <b>下を向く + スニーク + 右クリック + 直近にジャンプ</b>
 *       ({@link #isOpenGesture})。素材による分類は使わない ──
 *       {@link #effectiveThreadSlots} が正の値を返す装備なら種別を問わず開ける。</li>
 * </ul>
 *
 * <h2>2026-08-04: 真上ジェスチャー → 下向き+ジャンプへ変更した理由</h2>
 * このジェスチャーは以前「スニーク+<b>真上</b>を見る+右クリック」だった。真上は通常操作と重ならない
 * 良い安全装置だったが、<b>真上+スニークを「装着スレッドの内訳をチャットへ出す」操作へ割り当てる</b>
 * 仕様変更({@link ThreadStatChatListener})が入ったため、GUI 側を明け渡した。
 *
 * <p>置き換え先の「下向き」は<b>それ単独では安全装置にならない</b>点が真上と決定的に違う ──
 * 採掘・耕作・パス化・ブロック設置はどれも「下を向いてスニーク+右クリック」なので、
 * 下向きだけを条件にすると通常操作を奪う。そのため<b>直近のジャンプ</b>を必須条件として足している
 * (意図しないと成立しない組み合わせ)。詳細は {@link #isLookingDown} / {@link #jumpedRecently}。
 * {@code /ars thread}({@link com.arspaper.command.handlers.ThreadCommands})は
 * 引き続き全装備共通のフォールバック入口として有効(ジェスチャーが取りづらい環境や、
 * 視点操作が苦手なプレイヤーの保険)。枠数は item-stats の {@code thread_slots} が正のときのみ
 * (枠の拡張は {@link com.arspaper.ritual.effect.ThreadSlotExpandRitualEffect} が装備自身へ書き込む)。
 *
 * <h2>防具側でも {@code isCancelled()} を見る(2026-07-31 F3 指摘2)</h2>
 * 「防具にはバインドできないから二重発火しない」は成り立たない。
 * {@code SpellBindListener#canBind} が弾くのは {@code arspaper:custom_item_id} を持つ品だけで、
 * TF カタログ防具は {@code trinityforge:catalog_id} なので通る(しかも {@code /ars bind} は
 * バインド先を<b>オフハンド</b>から取るので、兜をオフハンド・魔導書をメインハンドに持てば成立する)。
 * その防具を手に持ってスニーク+右クリックすれば NORMAL で呪文が出て、
 * このリスナー(HIGH / {@code ignoreCancelled = false})が続けて GUI を開いてしまう。
 * <b>呪文が出たなら GUI は開かない</b>のが正しいので、開く直前でキャンセル済みかを見る。
 * <b>ジェスチャー対象を武器・杖・触媒へ広げたことで、この判定の重要性はさらに上がった</b> ──
 * 剣・杖・弓等は {@link com.arspaper.spell.SpellBindListener}(NORMAL 優先度)経由で
 * バインド詠唱の対象になり得るため、ジェスチャーで GUI を開こうとした瞬間に
 * 同じ右クリックで詠唱も飛びうる。この {@code isCancelled()} ガードが二重発火を唯一防いでいる。
 *
 * <h2>案内は「持ち替えたとき」だけに寄せる(2026-07-31 F3 指摘1 → F6 指摘3 で縮小、挙動不変)</h2>
 * バインド済みの杖・武器では {@code SpellBindListener} がスニーク判定より前に無条件で
 * キャンセルするため、「キャンセル済みなら黙る」条件を付けた右クリック案内は<b>永久に出ない</b>
 * (杖はバインドして使うものなので、これが一番普通の状態だった)。そこで案内を
 * 右クリックイベントから独立させ、スレッド枠を持つ装備を<b>メインハンドに選択したとき</b>に出す
 * (持ち替え/オフハンド入れ替え/ホットバースワップ)。スパム防止は {@link ThreadSlotHintPolicy} の
 * 二重ガード(間隔30秒 + 同一アイテム1セッション1回)。
 *
 * <p><b>⚠️ ジェスチャー不成立時に案内を出してはいけない(F6 指摘3)</b>:
 * 一時的に「キャンセル済みでも案内は出す」形にしていたが、<b>スニーク+右クリックは通常操作</b>である
 * ── 弓(5件)・クロスボウ(5件)・トライデント(5件)・斧(4件)・鍬はスレッド枠を持ち、
 * スニーク狙撃やスニーク耕作は普通の遊び方なので、5秒間隔のガードでは
 * <b>TF の EXP/会心アクションバー({@code SkillExpFeedbackService} / {@code CombatListener})を
 * 5秒ごとに無限に上書きし続ける</b>。「自分から試した操作だから毎回応答したい」という前提が
 * この操作には成り立たない。持ち替え時の案内(30秒 + 同一アイテム1セッション1回)と
 * {@code /ars help} で発見経路は足りているので、ジェスチャーが成立しないスニーク+右クリックは
 * <b>装備種別を問わず案内も GUI も出さない</b>(防具の GUI 起動だけが視線/ジャンプ判定なしで残る)。
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

    /** 「直近にジャンプ」と認める猶予。ジャンプ→着地→スニーク→右クリックが無理なく入る幅。 */
    private static final long JUMP_WINDOW_MS = 1_500L;

    /** プレイヤーごとの最終ジャンプ時刻(ms)。{@link #jumpedRecently} が読む。 */
    private final Map<UUID, Long> lastJumpAt = new ConcurrentHashMap<>();

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
        if (!isOpenGesture(player, item)) {
            // 条件を満たさない間は通常操作としてそのまま素通りさせる(GUI も案内も出さない)。
            return;
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
        lastJumpAt.remove(event.getPlayer().getUniqueId());
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
                "スレッド枠 " + slots + "枠 — ジャンプ→下を見てスニーク右クリック / または /ars thread",
                NamedTextColor.AQUA));
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

    /**
     * GUI を開くジェスチャーが成立しているか。
     *
     * <ul>
     *   <li><b>着用材質の防具</b>: スニーク+右クリックだけで成立(従来どおり。防具を手に持って
     *       右クリックする通常操作が無いので、追加の安全装置が要らない)。</li>
     *   <li><b>それ以外のスレッド枠付き装備</b>: <b>下を向いている + 直近にジャンプした</b>の両方。</li>
     * </ul>
     */
    private boolean isOpenGesture(Player player, ItemStack item) {
        if (isArmorPiece(item)) {
            return true;
        }
        return isLookingDown(player) && jumpedRecently(player);
    }

    /**
     * プレイヤーがはっきり下(ピッチ +55°以上。+90°が真下)を向いているか。
     *
     * <p>閾値を +90°ちょうどにしないのは、Bedrock/Geyser 側の視点入力が Java 版ほど滑らかでなく
     * ちょうど真下でピタッと止めにくいため(余裕を持たせる)。+55°は「足元付近を見ている」姿勢を拾い、
     * 水平前方を見る通常の戦闘/移動を除外する角度。
     *
     * <p><b>下向き単独では安全装置にならない</b>のがこのジェスチャーの難所 ── 採掘・耕作・
     * 土のパス化・ブロック設置はどれも「下を向いてスニーク+右クリック」で、旧仕様の真上ジェスチャーと
     * 違って通常操作そのものと重なる。そこで {@link #jumpedRecently} を必須条件として足している
     * (2026-08-04 の仕様変更)。真上は {@link ThreadStatChatListener}(内訳のチャット出力)に譲った。
     */
    private static boolean isLookingDown(Player player) {
        return player.getLocation().getPitch() >= 55f;
    }

    /**
     * 直近 {@link #JUMP_WINDOW_MS} 以内にジャンプしたか。
     *
     * <p>「下向き+スニーク+右クリック」は採掘・耕作・設置と同じ姿勢なので、これ単独では通常操作を
     * 奪ってしまう。<b>直前にジャンプを挟む</b>のは意図しないと成立しない組み合わせで、かつ
     * Bedrock/Geyser でも確実に入力できる(スラッシュコマンドや視点の微調整より易しい)。
     */
    private boolean jumpedRecently(Player player) {
        Long at = lastJumpAt.get(player.getUniqueId());
        return at != null && System.currentTimeMillis() - at <= JUMP_WINDOW_MS;
    }

    /**
     * ジャンプ時刻を記録する。{@code PlayerJumpEvent} は Paper 専用イベントで、移動パケットから
     * ジャンプだけを切り出してくれる(自前で速度やY差分を見る必要がない)。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(com.destroystokyo.paper.event.player.PlayerJumpEvent event) {
        lastJumpAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
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
