package com.arspaper.source;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.impl.Wand;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;

/**
 * ドミニオンワンドで結んだソースリレー経路をパーティクルで可視化するタスク(2026-08-01)。
 *
 * <p>従来は「接続した瞬間に1回だけ線を描く」だけで、設置後は経路がどこへ繋がっているのか
 * 目視できなかった。ここでは<b>ワンドを持っている間だけ</b>、<b>その本人にだけ</b>、
 * <b>一定間隔で</b>経路を描く。
 *
 * <h2>負荷の歯止め(全て {@code sourcelinks.yml} の {@code transfer.network.path-particles})</h2>
 * <ul>
 *   <li>{@code enabled}: 完全にOFFにできる</li>
 *   <li>{@code interval-ticks}: 描画間隔(既定10tick = 0.5秒)</li>
 *   <li>{@code view-distance}: プレイヤーからこの距離内に端点がある経路だけ描く</li>
 *   <li>{@code max-paths}: 1プレイヤー1回の描画で扱う経路数の上限</li>
 *   <li>{@code spacing}: 粒子の間隔。1経路あたりの粒子数は
 *       {@link SourcePathVisualPolicy#MAX_DOTS_PER_PATH} で更にハードキャップされる</li>
 *   <li>{@code dot-size}: 粒の大きさ(2026-08-24 追加)。1.0 がバニラのレッドストーン粒と同じで、
 *       線に使うと太い玉が飛び石で並ぶだけになり<b>線として読めない</b>。
 *       終端マーカーと隣接供給の大きさもこの値からの相対値で決まる</li>
 * </ul>
 *
 * <p>⚠ 「見にくい」の原因は密度・大きさ・寿命の3つが絡む。DUST の寿命は約8〜40tick(乱数)なので、
 * {@code interval-ticks} をそれ以上にすると<b>描き直しの合間に消えて点滅する</b>。
 * 濃くしたいときは {@code spacing} を詰めるだけでなく {@code interval-ticks} も
 * 10tick 以下に保つこと(2026-08-24 の既定は 10tick / 0.5m / 粒0.45 / 24m / 8経路)。
 *
 * <p>粒子は {@link Player#spawnParticle} でワンド保持者だけに送る(ワールド全体へは撒かない)。
 * ⚠ {@code count} には決して0を渡さないこと — 0は「消える」ではなく
 * offset をベクトルとして解釈する別機能になる({@link SourcePathVisualPolicy} 参照)。
 */
public class SourceNetworkParticleTask {

    /** 送信元→送信先の線。 */
    private static final Color LINE_COLOR = Color.fromRGB(80, 160, 255);
    /** 流れを示す明色(phase で移動する)。 */
    private static final Color FLOW_COLOR = Color.fromRGB(180, 255, 255);
    /** 送信先マーカー。 */
    private static final Color SINK_COLOR = Color.fromRGB(120, 255, 140);
    /**
     * ソースリンク→<b>隣接</b>ジャーの自動供給（ワンドで結んだ経路ではない）。
     * 経路の線と混ざらないよう暖色にする。
     */
    private static final Color SUPPLY_COLOR = Color.fromRGB(255, 190, 80);

    // 粒の大きさは全部 dot-size からの相対値にする(2026-08-24)。
    // 以前は 0.8 / 1.0 / 1.2 / 0.9 を直接書いていたため、
    // 「線が太い玉の飛び石になって読めない」のを設定で直せなかった。
    /** 流れの明色は線より少し大きくして、向きが分かるようにする。 */
    private static final float FLOW_SIZE_SCALE = 1.3f;
    /** 終端(送信先)は最も大きくして「ここへ入る」を目立たせる。 */
    private static final float SINK_SIZE_SCALE = 1.8f;
    /** 隣接供給は経路の線と区別できる程度に少し大きく。 */
    private static final float SUPPLY_SIZE_SCALE = 1.15f;
    /** 明色を何個おきに置くか。 */
    private static final int FLOW_STRIDE = 4;
    /** 隣接供給は1ブロックしか離れていないので、経路より細かく粒子を置く。 */
    private static final double SUPPLY_SPACING = 0.2;
    /** ソースリンクの6近傍（隣接供給の探索先）。 */
    private static final int[][] ADJACENT =
            {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final JavaPlugin plugin;
    private BukkitTask task;
    private int phase = 0;

    /**
     * 1回の描画で使う粒の色と大きさ。{@code dot-size} から導くので毎tick 1回だけ作る
     * (粒ごとに作ると1秒あたり千個の {@code DustOptions} を捨てることになる)。
     */
    private record Palette(Particle.DustOptions line, Particle.DustOptions flow,
                           Particle.DustOptions sink, Particle.DustOptions supply) {

        static Palette of(double dotSize) {
            float base = (float) dotSize;
            return new Palette(
                    new Particle.DustOptions(LINE_COLOR, base),
                    new Particle.DustOptions(FLOW_COLOR, base * FLOW_SIZE_SCALE),
                    new Particle.DustOptions(SINK_COLOR, base * SINK_SIZE_SCALE),
                    new Particle.DustOptions(SUPPLY_COLOR, base * SUPPLY_SIZE_SCALE));
        }
    }

    public SourceNetworkParticleTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        schedule();
    }

    /** {@code /ars reload} 後に間隔とON/OFFを反映する。 */
    public void restart() {
        schedule();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void schedule() {
        stop();
        SourceTransferConfig cfg = transferConfig();
        if (!cfg.pathParticlesEnabled()) {
            return;
        }
        long interval = cfg.pathParticleIntervalTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private static SourceTransferConfig transferConfig() {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourcelinkConfig() == null) {
            return SourceTransferConfig.defaults();
        }
        return ars.getSourcelinkConfig().transfer();
    }

    private void tick() {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourceNetwork() == null) {
            return;
        }
        SourceTransferConfig cfg = transferConfig();
        if (!cfg.pathParticlesEnabled()) {
            return;
        }

        // ワンドを持っているプレイヤーを先に絞る。誰も持っていなければ経路の取得すら行わない。
        List<Player> holders = plugin.getServer().getOnlinePlayers().stream()
                .filter(SourceNetworkParticleTask::holdsDominionWand)
                .map(p -> (Player) p)
                .toList();
        if (holders.isEmpty()) {
            return;
        }

        phase++;
        List<Location[]> paths = ars.getSourceNetwork().snapshotPaths();
        Optional<Wand> wand = dominionWand(ars);
        Palette palette = Palette.of(cfg.pathParticleDotSize());

        for (Player player : holders) {
            drawSelection(player, wand);
            drawSupplyLinks(player, cfg, palette);
            int drawn = 0;
            for (Location[] path : paths) {
                if (drawn >= cfg.pathParticleMaxPaths()) break;
                Location from = path[0];
                Location to = path[1];
                if (!player.getWorld().equals(from.getWorld())) continue;
                if (!from.getWorld().equals(to.getWorld())) continue;

                Location eye = player.getLocation();
                if (!SourcePathVisualPolicy.withinView(
                        eye.distanceSquared(from), eye.distanceSquared(to), cfg.pathParticleViewDistance())) {
                    continue;
                }
                drawPath(player, from, to, cfg.pathParticleSpacing(), palette);
                drawn++;
            }
        }
    }

    /** 選択中(まだ接続先を指定していない)の送信元を目立たせる。 */
    private void drawSelection(Player player, Optional<Wand> wand) {
        wand.flatMap(w -> w.selectedSource(player.getUniqueId())).ifPresent(sel -> {
            if (!player.getWorld().equals(sel.getWorld())) return;
            player.spawnParticle(Particle.END_ROD, sel.clone().add(0.5, 1.2, 0.5),
                    4, 0.2, 0.2, 0.2, 0.0);
        });
    }

    /**
     * ソースリンク→<b>隣接</b>ジャーの自動供給を描く（W-105）。
     *
     * <p>ソースリンクは隣接ジャーへ勝手に注ぐので、この関係は
     * {@code SourceNetwork} に1件も載らない ―― つまり修正前は
     * <b>「実際にソースが流れている繋がり」が一切見えなかった</b>。
     * ワンドで結んだ経路（青→水色）とは別色（橙）で描き分ける。
     *
     * <p>負荷: 設置済みソースリンクの座標キャッシュを使い、視界内のものだけを
     * 6近傍だけ見る。経路と同じ {@code max-paths} を上限に使う。
     */
    private void drawSupplyLinks(Player player, SourceTransferConfig cfg, Palette palette) {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourcelinkTickTask() == null) {
            return;
        }
        double limitSq = (double) cfg.pathParticleViewDistance() * cfg.pathParticleViewDistance();
        Location eye = player.getLocation();
        int drawn = 0;

        for (Location link : ars.getSourcelinkTickTask().trackedSourcelinks()) {
            if (drawn >= cfg.pathParticleMaxPaths()) break;
            if (link.getWorld() == null || !player.getWorld().equals(link.getWorld())) continue;
            if (eye.distanceSquared(link) > limitSq) continue;

            boolean any = false;
            for (int[] offset : ADJACENT) {
                org.bukkit.block.Block adjacent = link.getWorld().getBlockAt(
                        link.getBlockX() + offset[0],
                        link.getBlockY() + offset[1],
                        link.getBlockZ() + offset[2]);
                if (!(adjacent.getState() instanceof org.bukkit.block.TileState tile)) continue;

                String blockId = tile.getPersistentDataContainer().get(
                        com.arspaper.block.BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                if (!com.arspaper.block.impl.SourceJar.isSourceJarId(blockId)) continue;

                drawSupplyLink(player, link, adjacent.getLocation(), palette);
                any = true;
            }
            if (any) drawn++;
        }
    }

    /** 隣接1ブロックぶんの短い線。終端（ジャー側）を少し強調する。 */
    private void drawSupplyLink(Player player, Location from, Location to, Palette palette) {
        Location start = from.clone().add(0.5, 0.5, 0.5);
        Location end = to.clone().add(0.5, 0.5, 0.5);
        int dots = SourcePathVisualPolicy.dotCount(start.distance(end), SUPPLY_SPACING);

        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();

        for (int i = 0; i < dots; i++) {
            double t = SourcePathVisualPolicy.dotRatio(i, dots);
            Location point = new Location(start.getWorld(),
                    start.getX() + dx * t, start.getY() + dy * t, start.getZ() + dz * t);
            // count は必ず1以上。0は「非表示」ではなく offset をベクトル扱いする別機能になる。
            player.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, palette.supply());
        }
    }

    private void drawPath(Player player, Location from, Location to, double spacing, Palette palette) {
        Location start = from.clone().add(0.5, 0.5, 0.5);
        Location end = to.clone().add(0.5, 0.5, 0.5);
        double distance = start.distance(end);
        int dots = SourcePathVisualPolicy.dotCount(distance, spacing);

        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();

        // ⚠ dots は「実個数」。以前は i <= dots で回していたため上限128のはずが129個出ていた。
        for (int i = 0; i < dots; i++) {
            double t = SourcePathVisualPolicy.dotRatio(i, dots);
            Location point = new Location(start.getWorld(),
                    start.getX() + dx * t, start.getY() + dy * t, start.getZ() + dz * t);
            Particle.DustOptions color = SourcePathVisualPolicy.isFlowDot(i, phase, FLOW_STRIDE)
                    ? palette.flow() : palette.line();
            // count は必ず1以上。0は「非表示」ではなく offset をベクトル扱いする別機能になる。
            player.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, color);
        }
        // 送信先が分かるよう終端だけ色を変える。
        // ⚠ offset は「線の周りに散らす量」なので、大きくすると経路が滲んで線に見えなくなる。
        //   ここは終端の1点を示すマーカーなので 0.05 まで絞る(2026-08-24。以前は 0.15)。
        player.spawnParticle(Particle.DUST, end.clone().add(0.0, 0.5, 0.0),
                2, 0.05, 0.05, 0.05, 0.0, palette.sink());
    }

    private static Optional<Wand> dominionWand(ArsPaper ars) {
        if (ars.getItemRegistry() == null) {
            return Optional.empty();
        }
        return ars.getItemRegistry().get(Wand.ITEM_ID)
                .filter(Wand.class::isInstance)
                .map(Wand.class::cast);
    }

    /** メインハンド/オフハンドのどちらかがドミニオンワンドか。 */
    private static boolean holdsDominionWand(Player player) {
        return isDominionWand(player.getInventory().getItemInMainHand())
                || isDominionWand(player.getInventory().getItemInOffHand());
    }

    private static boolean isDominionWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        return Wand.ITEM_ID.equals(id);
    }
}
