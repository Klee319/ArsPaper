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
 * </ul>
 *
 * <p>粒子は {@link Player#spawnParticle} でワンド保持者だけに送る(ワールド全体へは撒かない)。
 * ⚠ {@code count} には決して0を渡さないこと — 0は「消える」ではなく
 * offset をベクトルとして解釈する別機能になる({@link SourcePathVisualPolicy} 参照)。
 */
public class SourceNetworkParticleTask {

    /** 送信元→送信先の線。 */
    private static final Particle.DustOptions LINE =
            new Particle.DustOptions(Color.fromRGB(80, 160, 255), 0.8f);
    /** 流れを示す明色(phase で移動する)。 */
    private static final Particle.DustOptions FLOW =
            new Particle.DustOptions(Color.fromRGB(180, 255, 255), 1.0f);
    /** 送信先マーカー。 */
    private static final Particle.DustOptions SINK =
            new Particle.DustOptions(Color.fromRGB(120, 255, 140), 1.2f);
    /** 明色を何個おきに置くか。 */
    private static final int FLOW_STRIDE = 4;

    private final JavaPlugin plugin;
    private BukkitTask task;
    private int phase = 0;

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

        for (Player player : holders) {
            drawSelection(player, wand);
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
                drawPath(player, from, to, cfg.pathParticleSpacing());
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

    private void drawPath(Player player, Location from, Location to, double spacing) {
        Location start = from.clone().add(0.5, 0.5, 0.5);
        Location end = to.clone().add(0.5, 0.5, 0.5);
        double distance = start.distance(end);
        int dots = SourcePathVisualPolicy.dotCount(distance, spacing);

        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();

        for (int i = 0; i <= dots; i++) {
            double t = (double) i / dots;
            Location point = new Location(start.getWorld(),
                    start.getX() + dx * t, start.getY() + dy * t, start.getZ() + dz * t);
            Particle.DustOptions color =
                    SourcePathVisualPolicy.isFlowDot(i, phase, FLOW_STRIDE) ? FLOW : LINE;
            // count は必ず1以上。0は「非表示」ではなく offset をベクトル扱いする別機能になる。
            player.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, color);
        }
        // 送信先が分かるよう終端だけ色を変える
        player.spawnParticle(Particle.DUST, end.clone().add(0.0, 0.5, 0.0),
                2, 0.15, 0.15, 0.15, 0.0, SINK);
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
