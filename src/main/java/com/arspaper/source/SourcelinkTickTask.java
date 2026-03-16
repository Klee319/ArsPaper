package com.arspaper.source;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlockRegistry;
import com.arspaper.source.sourcelink.Sourcelink;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登録済みSourcelink位置を定期的にティックし、
 * Source生成 → 隣接Source Jar供給を行うタスク。
 * 全チャンク走査ではなく、設置済み位置のSetで管理する。
 */
public class SourcelinkTickTask {

    private static final int TICK_INTERVAL = 100; // 5秒
    private final JavaPlugin plugin;
    private final CustomBlockRegistry blockRegistry;
    private BukkitTask task;

    /** 設置済みSourcelinkの位置キャッシュ (blockId → locations) */
    private final Map<String, Set<Location>> sourcelinkLocations = new ConcurrentHashMap<>();

    public SourcelinkTickTask(JavaPlugin plugin, CustomBlockRegistry blockRegistry) {
        this.plugin = plugin;
        this.blockRegistry = blockRegistry;
    }

    public void start() {
        // 起動時にロード済みチャンクをスキャンしてキャッシュ再構築
        rebuildCache();
        task = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL
        );
    }

    /**
     * ロード済みチャンクからSourcelinkを検出してキャッシュを再構築する。
     * サーバー再起動時にインメモリキャッシュを復元するために使用。
     */
    private void rebuildCache() {
        int[] count = {0};
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof TileState tileState)) continue;
                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (blockId == null) continue;
                    blockRegistry.get(blockId).ifPresent(cb -> {
                        if (cb instanceof Sourcelink) {
                            sourcelinkLocations
                                .computeIfAbsent(blockId, k -> ConcurrentHashMap.newKeySet())
                                .add(state.getLocation());
                            count[0]++;
                        }
                    });
                }
            }
        }
        if (count[0] > 0) {
            plugin.getLogger().info("Rebuilt sourcelink cache: " + count[0] + " sourcelinks found");
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Sourcelinkの設置を登録する。
     */
    public void addSourcelink(Location location, String blockId) {
        sourcelinkLocations
            .computeIfAbsent(blockId, k -> ConcurrentHashMap.newKeySet())
            .add(location.getBlock().getLocation()); // ブロック座標に正規化
    }

    /**
     * Sourcelinkの撤去を登録する。
     */
    public void removeSourcelink(Location location) {
        Location blockLoc = location.getBlock().getLocation();
        sourcelinkLocations.values().forEach(locs -> locs.remove(blockLoc));
    }

    private void tick() {
        for (var entry : sourcelinkLocations.entrySet()) {
            String blockId = entry.getKey();
            Set<Location> locations = entry.getValue();

            blockRegistry.get(blockId).ifPresent(customBlock -> {
                if (!(customBlock instanceof Sourcelink sourcelink)) return;

                List<Location> staleLocations = new ArrayList<>();
                for (Location loc : locations) {
                    World world = loc.getWorld();
                    if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                        continue; // アンロード済みチャンクはスキップ
                    }

                    Block block = loc.getBlock();
                    if (!(block.getState() instanceof TileState)) {
                        // ブロックが撤去されている場合は後で除去
                        staleLocations.add(loc);
                        continue;
                    }

                    int generated = sourcelink.generateSource();
                    if (generated > 0) {
                        sourcelink.supplyAdjacent(block, generated);
                        sourcelink.onGenerate(block);
                    }
                }
                // イテレーション後に一括除去
                staleLocations.forEach(locations::remove);
            });
        }
    }
}
