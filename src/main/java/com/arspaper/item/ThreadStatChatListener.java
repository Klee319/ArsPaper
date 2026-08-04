package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.integration.TrinityForgeBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「装備を手に持って<b>真上を見ながらスニークを開始</b>」でその装備の装着スレッド内訳を
 * チャットへ出す(2026-08-04 依頼#47)。
 *
 * <h2>なぜチャットへ出すのか</h2>
 * 装備の lore には {@code ・<スレッド名>【品質】} の1行だけを載せる方針に変えた
 * ({@link com.arspaper.gui.ThreadGui} の {@code buildThreadLore})。5枠を埋めた装備で
 * スレッドの厳選ステ明細まで展開すると、ツールチップが数十行に膨らんで
 * <b>装備本体のステが画面外へ押し出されていた</b>。明細が要るのは「今この装備を見比べている」
 * ときだけなので、その瞬間に取り出せる操作を用意して lore からは外す。
 *
 * <h2>ジェスチャーが2つある理由と、両者が衝突しない根拠</h2>
 * <ul>
 *   <li><b>真上 + スニーク開始</b>(このクラス): 内訳をチャットへ。右クリックを<b>要求しない</b>ので
 *       手に持っている物の通常操作(攻撃・詠唱・設置)を一切奪わない。</li>
 *   <li><b>下向き + スニーク + 右クリック + 直近にジャンプ</b>
 *       ({@link ThreadGuiOpenListener}): スレッド設定GUIを開く。</li>
 * </ul>
 * 視線が真上と下向きで排他なので、片方の条件を満たす姿勢はもう片方を必ず満たさない。
 *
 * <h2>スパム防止</h2>
 * スニーク開始そのものは頻繁なイベントだが、<b>真上(ピッチ -80°以下)を見ている状態でのスニーク開始</b>は
 * 実プレイでまず起きない(採掘・戦闘・移動はすべて水平〜下向き)。それでも真上を向いたまま
 * スニークを連打されると内訳が何度も流れるので、{@link #COOLDOWN_MS} の間隔ガードを置く。
 * アクションバーではなく<b>チャット</b>へ出すのは、TF の EXP/会心アクションバー
 * ({@code SkillExpFeedbackService} / {@code CombatListener})を上書きしないため。
 */
public final class ThreadStatChatListener implements Listener {

    /** 同一プレイヤーの再出力を抑える間隔。 */
    private static final long COOLDOWN_MS = 3_000L;

    /** 真上判定のピッチ閾値。{@link ThreadGuiOpenListener} と同じ値(理由は同クラスの javadoc)。 */
    private static final float STRAIGHT_UP_PITCH = -80f;

    private final Map<UUID, Long> lastSentAt = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            // スニーク解除では出さない(1操作で2回流れる)。
            return;
        }
        Player player = event.getPlayer();
        if (player.getLocation().getPitch() > STRAIGHT_UP_PITCH) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || !held.hasItemMeta()) {
            return;
        }
        int slots = effectiveThreadSlots(held, player);
        if (slots <= 0) {
            return;
        }
        List<SocketedThreads.Entry> socketed =
                SocketedThreads.read(held.getItemMeta().getPersistentDataContainer(), slots);
        if (socketed.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        lastSentAt.put(player.getUniqueId(), now);
        send(player, socketed);
    }

    /** 常駐マップにオフラインプレイヤーを溜めない。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSentAt.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 装着スレッド1件ごとに「見出し(名前+品質) → threads.yml の効果説明 → 厳選ステ明細」を送る。
     * 明細の整形は {@link TrinityForgeBridge#threadStatLore} 一本 ── ここで自前に組み直すと
     * lore と桁・単位・色が食い違う(過去に実際に起きた)。
     */
    private static void send(Player player, List<SocketedThreads.Entry> socketed) {
        player.sendMessage(Component.text("── 装着スレッドの内訳 ──", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        ThreadConfig threadConfig = ArsPaper.getInstance().getThreadConfig();
        for (SocketedThreads.Entry entry : socketed) {
            player.sendMessage(SocketedThreads.summaryLine(entry.type(), entry.quality()));
            if (threadConfig != null) {
                threadConfig.getEffectLore(entry.type()).forEach(player::sendMessage);
            }
            Map<String, Double> stats = TrinityForgeBridge.resolveThreadStats(
                    entry.type().getBaseMaterial(), entry.type().getCustomModelData(),
                    entry.quality(), entry.rollSeed());
            TrinityForgeBridge.threadStatLore(stats).forEach(player::sendMessage);
        }
    }

    private static int effectiveThreadSlots(ItemStack item, Player player) {
        try {
            Map<String, Double> stats = TrinityForgeBridge.resolveFullItemStats(item);
            return TrinityForgeBridge.tfEffectiveThreadSlotCap(stats, player);
        } catch (Throwable tfUnavailable) {
            return 0;
        }
    }
}
