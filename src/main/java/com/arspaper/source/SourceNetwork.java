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

    private static final int TRANSFER_AMOUNT = 100;
    private static final int MAX_RELAY_RANGE = 30;
    private static final int TRANSFER_INTERVAL_TICKS = 40; // 2秒

    private final JavaPlugin plugin;
    private final Map<LocationKey, Set<LocationKey>> connections = new HashMap<>();
    private BukkitTask transferTask;
    private boolean dirty = false;

    public SourceNetwork(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
        startTransferTask();
    }

    /**
     * 送信元→送信先の接続を追加する。
     * 逆方向の接続が既にある場合は自動削除する（相殺防止）。
     *
     * @return 接続に成功したか（距離チェック含む）
     */
    public boolean connect(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        if (from.distance(to) > MAX_RELAY_RANGE) return false;

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
        transferTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::tickTransfer, TRANSFER_INTERVAL_TICKS, TRANSFER_INTERVAL_TICKS
        );
    }

    private void tickTransfer() {
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
            if (!fromTile.getPersistentDataContainer().has(BlockKeys.CUSTOM_BLOCK_ID)) continue;

            int available = fromTile.getPersistentDataContainer()
                .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);
            if (available <= 0) continue;

            for (LocationKey toKey : entry.getValue()) {
                Location toLoc = toKey.toLocation();
                if (toLoc == null) continue;
                if (!toLoc.getWorld().isChunkLoaded(toKey.x() >> 4, toKey.z() >> 4)) continue;

                Block toBlock = toLoc.getBlock();
                if (!(toBlock.getState() instanceof TileState toTile)) continue;
                if (!toTile.getPersistentDataContainer().has(BlockKeys.CUSTOM_BLOCK_ID)) continue;

                int toAmount = toTile.getPersistentDataContainer()
                    .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);
                int toMax = SourceJar.MAX_SOURCE;

                // 無限ソースジャーへの転送はスキップ
                if (com.arspaper.block.impl.SourceJar.isInfinite(toTile)) continue;

                int space = toMax - toAmount;
                if (space <= 0) continue;

                int transfer = Math.min(TRANSFER_AMOUNT, Math.min(available, space));
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

            int fromAmount = fromTile.getPersistentDataContainer()
                .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);

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

                int toAmount = toTile.getPersistentDataContainer()
                    .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);

                fromTile.getPersistentDataContainer().set(
                    BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, fromAmount - transfer
                );
                fromTile.update();

                toTile.getPersistentDataContainer().set(
                    BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, toAmount + transfer
                );
                toTile.update();

                fromAmount -= transfer;
                if (fromAmount <= 0) break;
            }
        }
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
