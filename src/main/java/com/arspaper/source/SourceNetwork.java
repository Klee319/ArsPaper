package com.arspaper.source;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.impl.SourceJar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Source Relayネットワーク管理。
 * Dominion Wandで接続された送信元→送信先の経路を管理し、
 * 定期的にSourceを転送する。
 *
 * ネットワーク定義はsource-network.ymlに永続化。
 */
public class SourceNetwork {

    // 転送量(旧 TRANSFER_AMOUNT=100) / 転送範囲(旧 MAX_RELAY_RANGE=30) /
    // 転送周期(旧 TRANSFER_INTERVAL_TICKS=40) は 2026-08-01 に sourcelinks.yml の
    // transfer.network.* へ移設した。既定値は移設前と同値(挙動不変)。

    private final JavaPlugin plugin;
    private final Map<LocationKey, Set<LocationKey>> connections = new HashMap<>();
    private BukkitTask transferTask;
    private boolean dirty = false;

    public SourceNetwork(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
        startTransferTask();
    }

    private static SourceTransferConfig transferConfig() {
        com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
        if (ars == null || ars.getSourcelinkConfig() == null) {
            return SourceTransferConfig.defaults();
        }
        return ars.getSourcelinkConfig().transfer();
    }

    /** リンクを張れる最大距離（{@code transfer.network.max-link-range}）。 */
    public static int maxLinkRange() {
        return transferConfig().networkMaxLinkRange();
    }

    /**
     * 送信元→送信先の接続を追加する。
     * 逆方向の接続が既にある場合は自動削除する（相殺防止）。
     *
     * @return 接続に成功したか（距離チェック含む）
     */
    public boolean connect(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        if (from.distance(to) > maxLinkRange()) return false;

        LocationKey fromKey = LocationKey.of(from);
        LocationKey toKey = LocationKey.of(to);

        // 自己接続を防止
        if (fromKey.equals(toKey)) return false;

        connections.computeIfAbsent(fromKey, k -> new LinkedHashSet<>()).add(toKey);
        markDirtyAndSaveAsync();
        return true;
    }

    /**
     * 送信元からの全接続を除去する。
     */
    public void disconnect(Location from) {
        if (connections.remove(LocationKey.of(from)) != null) {
            markDirtyAndSaveAsync();
        }
    }

    /**
     * 特定の接続を除去する。
     */
    public void disconnect(Location from, Location to) {
        LocationKey fromKey = LocationKey.of(from);
        Set<LocationKey> targets = connections.get(fromKey);
        if (targets != null) {
            targets.remove(LocationKey.of(to));
            if (targets.isEmpty()) {
                connections.remove(fromKey);
            }
            markDirtyAndSaveAsync();
        }
    }

    /**
     * 指定座標に関連する全接続（送信元・送信先両方）を除去する。
     * ブロック破壊時に呼び出す。
     */
    public void disconnectAll(Location loc) {
        LocationKey key = LocationKey.of(loc);
        boolean changed = false;

        // 送信元として登録されている接続を削除
        if (connections.remove(key) != null) {
            changed = true;
        }

        // 送信先として登録されている接続から削除
        var iterator = connections.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().remove(key)) {
                changed = true;
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }

        if (changed) {
            markDirtyAndSaveAsync();
        }
    }

    /**
     * 送信元の接続先一覧を取得。
     */
    public Set<Location> getConnections(Location from) {
        Set<LocationKey> targets = connections.get(LocationKey.of(from));
        if (targets == null) return Set.of();

        Set<Location> result = new LinkedHashSet<>();
        for (LocationKey key : targets) {
            Location loc = key.toLocation();
            if (loc != null) result.add(loc);
        }
        return result;
    }

    /**
     * 指定座標への接続元（逆引き）一覧を取得。
     */
    public Set<Location> getIncomingConnections(Location to) {
        LocationKey toKey = LocationKey.of(to);
        Set<Location> result = new LinkedHashSet<>();
        for (var entry : connections.entrySet()) {
            if (entry.getValue().contains(toKey)) {
                Location loc = entry.getKey().toLocation();
                if (loc != null) result.add(loc);
            }
        }
        return result;
    }

    private void startTransferTask() {
        if (transferTask != null) {
            transferTask.cancel();
        }
        long interval = transferConfig().networkIntervalTicks();
        transferTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::tickTransfer, interval, interval
        );
    }

    /**
     * {@code transfer.network.interval-ticks} を読み直してタイマーを張り直す。
     * {@code /ars reload} から呼ぶ（Bukkitのタイマー周期は後から変更できないため）。
     */
    public void restartTransferTask() {
        startTransferTask();
    }

    /**
     * 経路(送信元→送信先)のスナップショットを返す。パーティクル描画など読み取り専用の用途向け。
     * ワールドが未ロードの端点は除外する。
     */
    public List<Location[]> snapshotPaths() {
        List<Location[]> paths = new ArrayList<>();
        for (var entry : connections.entrySet()) {
            Location from = entry.getKey().toLocation();
            if (from == null) continue;
            for (LocationKey toKey : entry.getValue()) {
                Location to = toKey.toLocation();
                if (to == null) continue;
                paths.add(new Location[]{from, to});
            }
        }
        return paths;
    }

    private void tickTransfer() {
        // ⚠ 1リンク・1周期の上限は「定額 x 送信元の階梯倍率」と「残量に対する割合」の大きい方
        // ({@link SourceDrainPolicy})。
        //
        // 2026-08-25 (W-257): 定額だけの時代は「上位リンクにしても上位ジャーにしても毎秒2.5点」で
        // 固定だった(網には階梯倍率が1つも掛からなかった)。いったん割合排出で速度を出したが、
        // 割合は (1) 速度が残量で変わる (2) 定額側と大きい方を採るので階梯倍率を無意味にする
        // の2点でユーザー要件に反していたため、割合を既定0にして
        // **送信元の階梯倍率**(NetworkTierMultiplier)で速度を出す形へ移した。
        // 割合ぶんは yml から再有効化できるので判定そのものは残してある。
        final int flatPerTransfer = transferConfig().networkMaxPerTransfer();
        final double drainRatio = transferConfig().networkDrainRatio();
        // フェーズ1: 全転送を計算（net flowで相殺を回避）
        // ペアごとの正味転送量を計算
        Map<LocationKey, Map<LocationKey, Integer>> pendingTransfers = new LinkedHashMap<>();

        for (var entry : connections.entrySet()) {
            LocationKey fromKey = entry.getKey();
            Location fromLoc = fromKey.toLocation();
            if (fromLoc == null) continue;
            if (!fromLoc.getWorld().isChunkLoaded(fromKey.x() >> 4, fromKey.z() >> 4)) continue;

            Block fromBlock = fromLoc.getBlock();
            if (!(fromBlock.getState() instanceof TileState fromTile)) continue;

            // ⚠ W-104: 貯蔵先はブロック種別で違う（ジャー=source_amount / ソースリンク=sourcelink_buffer）。
            // 以前はここで source_amount 決め打ちだったため、ソースリンクを送信元にすると
            // 残量が常に0と判定され、接続は成立しているのに一度も転送されなかった。
            SourceStorage fromStorage = storageAt(fromTile);
            if (!fromStorage.canSend()) continue;

            int available = storedSource(fromTile, fromStorage);
            if (available <= 0) continue;

            // 送信元の階梯倍率。上位リンク・上位ジャーほど1周期に多く送れる(W-257)。
            final int tieredPerTransfer = NetworkTierMultiplier.scale(
                flatPerTransfer, NetworkTierMultiplier.forBlockId(blockIdOf(fromTile)));

            for (LocationKey toKey : entry.getValue()) {
                Location toLoc = toKey.toLocation();
                if (toLoc == null) continue;
                if (!toLoc.getWorld().isChunkLoaded(toKey.x() >> 4, toKey.z() >> 4)) continue;

                Block toBlock = toLoc.getBlock();
                if (!(toBlock.getState() instanceof TileState toTile)) continue;

                // ソースを保持しないブロックへ送ると、誰も読まないPDCへ書くだけで送信元からは減る
                // ＝ソースが黙って消える。受け取れるのはジャーだけ。
                if (!storageAt(toTile).canReceive()) continue;

                // 無限ソースジャーへの転送はスキップ
                if (SourceJar.isInfinite(toTile)) continue;

                // ⚠ 容量は上位ジャーごとに違う。static な MAX_SOURCE（＝設定未読込時のフォールバック
                // 10,000）を見ていたため、上位ジャーは網経由だと 10,000 で頭打ちになっていた。
                int space = SourceJar.maxSource(toTile) - SourceJar.getSourceAmount(toTile);
                if (space <= 0) continue;

                // 上限は「今の残量」から毎リンク引き直す。リンクが複数ある送信元では
                // available が減るほど1本あたりの上限も下がるので、定額時代と同じ
                // 「1リンクにつき上限1つ」の意味を保ったまま残量に追随する。
                int allowance = SourceDrainPolicy.allowance(tieredPerTransfer, available, drainRatio);
                int transfer = Math.min(allowance, Math.min(available, space));
                if (transfer <= 0) continue;

                pendingTransfers.computeIfAbsent(fromKey, k -> new LinkedHashMap<>())
                    .put(toKey, transfer);

                available -= transfer;
                if (available <= 0) break;
            }
        }

        // フェーズ2: 転送を適用
        for (var fromEntry : pendingTransfers.entrySet()) {
            LocationKey fromKey = fromEntry.getKey();
            Location fromLoc = fromKey.toLocation();
            if (fromLoc == null) continue;

            Block fromBlock = fromLoc.getBlock();
            if (!(fromBlock.getState() instanceof TileState fromTile)) continue;

            SourceStorage fromStorage = storageAt(fromTile);
            if (!fromStorage.canSend()) continue;

            int fromAmount = storedSource(fromTile, fromStorage);

            for (var toEntry : fromEntry.getValue().entrySet()) {
                LocationKey toKey = toEntry.getKey();
                int transfer = toEntry.getValue();

                // 実際に利用可能な量で再制限
                transfer = Math.min(transfer, fromAmount);
                if (transfer <= 0) continue;

                Location toLoc = toKey.toLocation();
                if (toLoc == null) continue;

                Block toBlock = toLoc.getBlock();
                if (!(toBlock.getState() instanceof TileState toTile)) continue;
                if (!storageAt(toTile).canReceive()) continue;

                // 実際に入った分だけ送信元から引く。ジャー個体の容量で切り詰められても消えない。
                int added = SourceJar.addSource(toTile, transfer);
                if (added <= 0) continue;

                setStoredSource(fromTile, fromStorage, fromAmount - added);

                fromAmount -= added;
                if (fromAmount <= 0) break;
            }
        }
    }

    /**
     * その端点がソースを<b>どこに</b>持っているか。判定は custom_block_id から引く。
     * ソースリンクかどうかはブロックレジストリの実体で見る（sourcelinks.yml に足した
     * カスタムソースリンクも {@code Sourcelink} として登録されるので追随する）。
     */
    private static SourceStorage storageAt(TileState tile) {
        String blockId = blockIdOf(tile);
        if (blockId == null) return SourceStorage.NONE;
        return SourceStorage.of(SourceJar.isSourceJarId(blockId), isSourcelinkId(blockId));
    }

    /** 設置ブロックの {@code custom_block_id}。未設定なら null。 */
    private static String blockIdOf(TileState tile) {
        return tile.getPersistentDataContainer()
            .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
    }

    private static boolean isSourcelinkId(String blockId) {
        com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
        if (ars == null || ars.getBlockRegistry() == null) return false;
        return ars.getBlockRegistry().get(blockId).orElse(null)
            instanceof com.arspaper.source.sourcelink.Sourcelink;
    }

    /** 貯蔵種別に応じたPDCキー。ここを間違えると「残量0」で無言に読み飛ばされる（W-104）。 */
    private static org.bukkit.NamespacedKey amountKey(SourceStorage storage) {
        return storage == SourceStorage.SOURCELINK
            ? com.arspaper.source.sourcelink.Sourcelink.SOURCE_BUFFER
            : BlockKeys.SOURCE_AMOUNT;
    }

    private static int storedSource(TileState tile, SourceStorage storage) {
        return tile.getPersistentDataContainer()
            .getOrDefault(amountKey(storage), PersistentDataType.INTEGER, 0);
    }

    private static void setStoredSource(TileState tile, SourceStorage storage, int amount) {
        tile.getPersistentDataContainer().set(
            amountKey(storage), PersistentDataType.INTEGER, Math.max(0, amount)
        );
        tile.update();
    }

    public void shutdown() {
        if (transferTask != null) {
            transferTask.cancel();
        }
        // シャットダウン時は同期保存
        if (dirty) {
            saveSync();
        }
    }

    // === 永続化 ===

    private File getDataFile() {
        return new File(plugin.getDataFolder(), "source-network.yml");
    }

    /**
     * 変更フラグを立て、非同期で保存する。
     */
    private void markDirtyAndSaveAsync() {
        dirty = true;
        // メインスレッドで接続データのスナップショットを取得
        List<Map<String, Object>> snapshot = buildSnapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            saveSnapshot(snapshot);
            dirty = false;
        });
    }

    private List<Map<String, Object>> buildSnapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (var entry : connections.entrySet()) {
            Map<String, Object> conn = new LinkedHashMap<>();
            conn.put("from", entry.getKey().serialize());
            List<String> targets = new ArrayList<>();
            for (LocationKey target : entry.getValue()) {
                targets.add(target.serialize());
            }
            conn.put("to", targets);
            list.add(conn);
        }
        return list;
    }

    private void saveSnapshot(List<Map<String, Object>> snapshot) {
        FileConfiguration config = new YamlConfiguration();
        config.set("connections", snapshot);
        try {
            plugin.getDataFolder().mkdirs();
            config.save(getDataFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save source network: " + e.getMessage());
        }
    }

    private void saveSync() {
        saveSnapshot(buildSnapshot());
    }

    @SuppressWarnings("unchecked")
    private void load() {
        File file = getDataFile();
        if (!file.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> list = config.getMapList("connections");

        for (Map<?, ?> conn : list) {
            String fromStr = (String) conn.get("from");
            LocationKey fromKey = LocationKey.deserialize(fromStr);
            if (fromKey == null) continue;

            List<String> targets = (List<String>) conn.get("to");
            if (targets == null) continue;

            Set<LocationKey> targetKeys = new LinkedHashSet<>();
            for (String target : targets) {
                LocationKey key = LocationKey.deserialize(target);
                if (key != null) targetKeys.add(key);
            }
            if (!targetKeys.isEmpty()) {
                connections.put(fromKey, targetKeys);
            }
        }
    }

    /**
     * シリアライズ可能なLocation表現。
     */
    record LocationKey(String world, int x, int y, int z) {
        static LocationKey of(Location loc) {
            return new LocationKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z);
        }

        String serialize() {
            return world + "," + x + "," + y + "," + z;
        }

        static LocationKey deserialize(String str) {
            String[] parts = str.split(",");
            if (parts.length != 4) return null;
            try {
                return new LocationKey(parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
