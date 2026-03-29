package com.arspaper.source;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlockRegistry;
import com.arspaper.source.sourcelink.BotanicalSourcelink;
import com.arspaper.source.sourcelink.Sourcelink;
import com.arspaper.source.sourcelink.VitalicSourcelink;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.StructureGrowEvent;
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
public class SourcelinkTickTask implements Listener {

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
        // ChunkLoadイベントでキャッシュに追加
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * チャンクロード時にSourcelinkをキャッシュに追加する。
     * 新規生成チャンクはスキップ（Sourcelinkがあるはずがない）。
     * メインスレッドへの負荷軽減のため1tick遅延で処理。
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) return;
        Chunk chunk = event.getChunk();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!chunk.isLoaded()) return;
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
                    }
                });
            }
        }, 1L);
    }

    /**
     * ロード済みチャンクからSourcelinkを検出してキャッシュを再構築する。
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

    public void addSourcelink(Location location, String blockId) {
        sourcelinkLocations
            .computeIfAbsent(blockId, k -> ConcurrentHashMap.newKeySet())
            .add(location.getBlock().getLocation());
    }

    public void removeSourcelink(Location location) {
        Location blockLoc = location.getBlock().getLocation();
        sourcelinkLocations.values().forEach(locs -> locs.remove(blockLoc));
    }

    /**
     * Vitalic Sourcelink: 近くでmobが死亡した際にボーナスSourceをバッファに蓄積。
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player) return;

        Location deathLoc = event.getEntity().getLocation();
        World deathWorld = deathLoc.getWorld();
        if (deathWorld == null) return;

        Set<Location> vitalicLocs = sourcelinkLocations.get("vitalic_sourcelink");
        if (vitalicLocs == null || vitalicLocs.isEmpty()) return;

        int radiusSq = VitalicSourcelink.DETECTION_RADIUS * VitalicSourcelink.DETECTION_RADIUS;

        for (Location loc : List.copyOf(vitalicLocs)) {
            World locWorld = loc.getWorld();
            if (locWorld == null || !deathWorld.equals(locWorld)) continue;
            if (deathLoc.distanceSquared(loc) > radiusSq) continue;

            Block block = loc.getBlock();
            if (!(block.getState() instanceof TileState)) continue;

            blockRegistry.get("vitalic_sourcelink").ifPresent(cb -> {
                if (cb instanceof Sourcelink sourcelink) {
                    // バッファに蓄積（次のtickで排出される）
                    sourcelink.addToBuffer(block, VitalicSourcelink.SOURCE_PER_KILL);
                }
            });
        }
    }

    /**
     * Botanical Sourcelink: 近くで作物・植物が成長した際にソースをバッファに蓄積。
     */
    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        handlePlantGrowth(event.getBlock().getLocation());
    }

    /**
     * Botanical Sourcelink: 木や巨大キノコ等の構造物成長時にも蓄積。
     */
    @EventHandler
    public void onStructureGrow(StructureGrowEvent event) {
        handlePlantGrowth(event.getLocation());
    }

    private void handlePlantGrowth(Location growthLoc) {
        World growthWorld = growthLoc.getWorld();
        if (growthWorld == null) return;

        Set<Location> botanicalLocs = sourcelinkLocations.get("botanical_sourcelink");
        if (botanicalLocs == null || botanicalLocs.isEmpty()) return;

        int radiusSq = BotanicalSourcelink.DETECTION_RADIUS * BotanicalSourcelink.DETECTION_RADIUS;

        for (Location loc : List.copyOf(botanicalLocs)) {
            World locWorld = loc.getWorld();
            if (locWorld == null || !growthWorld.equals(locWorld)) continue;
            if (growthLoc.distanceSquared(loc) > radiusSq) continue;

            Block block = loc.getBlock();
            if (!(block.getState() instanceof TileState)) continue;

            blockRegistry.get("botanical_sourcelink").ifPresent(cb -> {
                if (cb instanceof Sourcelink sourcelink) {
                    sourcelink.addToBuffer(block, BotanicalSourcelink.SOURCE_PER_GROWTH);
                }
            });
        }
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
                        continue;
                    }

                    Block block = loc.getBlock();
                    if (!(block.getState() instanceof TileState)) {
                        staleLocations.add(loc);
                        continue;
                    }

                    int generated = sourcelink.generateSource(block);
                    if (generated > 0) {
                        sourcelink.supplyAdjacent(block, generated);
                        sourcelink.onGenerate(block);
                    }
                }
                staleLocations.forEach(locations::remove);
            });
        }
    }
}
