package com.arspaper.source;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlockRegistry;
import com.arspaper.source.sourcelink.BotanicalSourcelink;
import com.arspaper.source.sourcelink.Sourcelink;
import com.arspaper.source.sourcelink.SourceYield;
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

    // 周期(旧 TICK_INTERVAL = 100tick = 5秒)は 2026-08-01 に sourcelinks.yml の
    // transfer.sourcelink.interval-ticks へ移設した。既定値は同じ100tick(挙動不変)。
    private final JavaPlugin plugin;
    private final CustomBlockRegistry blockRegistry;
    private BukkitTask task;
    private boolean listenerRegistered = false;

    /** 設置済みSourcelinkの位置キャッシュ (blockId → locations) */
    private final Map<String, Set<Location>> sourcelinkLocations = new ConcurrentHashMap<>();

    public SourcelinkTickTask(JavaPlugin plugin, CustomBlockRegistry blockRegistry) {
        this.plugin = plugin;
        this.blockRegistry = blockRegistry;
    }

    public void start() {
        // 起動時にロード済みチャンクをスキャンしてキャッシュ再構築
        rebuildCache();
        scheduleTick();
        // ChunkLoadイベントでキャッシュに追加(reload で二重登録しないよう1回だけ)
        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
    }

    /**
     * {@code transfer.sourcelink.interval-ticks} を読み直してタイマーを張り直す。
     * {@code /ars reload} から呼ぶ(Bukkitのタイマー周期は後から変更できないため再スケジュールが必要)。
     */
    public void restart() {
        scheduleTick();
    }

    private void scheduleTick() {
        if (task != null) {
            task.cancel();
        }
        long interval = transferConfig().sourcelinkIntervalTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private static com.arspaper.source.SourceTransferConfig transferConfig() {
        com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
        if (ars == null || ars.getSourcelinkConfig() == null) {
            return com.arspaper.source.SourceTransferConfig.defaults();
        }
        return ars.getSourcelinkConfig().transfer();
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
        // buffer-cap 警告のラッチも解放する(撤去済みブロックの座標を溜め続けないため)
        Sourcelink.forgetBufferCapWarning(blockLoc);
    }

    /**
     * Vitalic Sourcelink: 近くでmobが死亡した際にボーナスSourceをバッファに蓄積。
     * 固定idだけでなく、type=vitalic のカスタムソースリンクも対象。
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player) return;

        Location deathLoc = event.getEntity().getLocation();
        World deathWorld = deathLoc.getWorld();
        if (deathWorld == null) return;

        int radius = transferConfig().vitalicDetectionRadius();
        if (radius <= 0) return;
        accumulateNear(VitalicSourcelink.class, deathWorld, deathLoc, radius * radius,
            VitalicSourcelink.SOURCE_PER_KILL);
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

        int radius = transferConfig().botanicalDetectionRadius();
        if (radius <= 0) return;
        accumulateNear(BotanicalSourcelink.class, growthWorld, growthLoc, radius * radius,
            BotanicalSourcelink.SOURCE_PER_GROWTH);
    }

    /**
     * 指定typeのソースリンク (カスタムid含む) のうち、イベント地点の近傍にある設置ブロックの
     * バッファへ {@code amount} を加算する。次のtickで排出される。
     */
    private void accumulateNear(Class<? extends Sourcelink> type, World world,
                                Location center, int radiusSq, int amount) {
        for (var entry : sourcelinkLocations.entrySet()) {
            Sourcelink sourcelink = blockRegistry.get(entry.getKey())
                .filter(type::isInstance)
                .map(cb -> (Sourcelink) cb)
                .orElse(null);
            if (sourcelink == null || entry.getValue().isEmpty()) continue;

            for (Location loc : List.copyOf(entry.getValue())) {
                World locWorld = loc.getWorld();
                if (locWorld == null || !world.equals(locWorld)) continue;
                if (center.distanceSquared(loc) > radiusSq) continue;

                Block block = loc.getBlock();
                if (!(block.getState() instanceof TileState)) continue;
                // 成長/撃破ボーナスも「新しく生まれた分」なので階梯の生成量倍率を掛ける
                // (掛けないとボタニカル/バイタリックだけ階梯で生成量が伸びない)。
                sourcelink.addToBuffer(block, sourcelink.scaleGeneratedYield(amount));
            }
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

                    SourceYield yield = sourcelink.generateSource(block);
                    int generated = yield.total();
                    if (generated > 0) {
                        // 注ぎ切れなかった残量のうち「バッファから引いた分」だけを戻す。
                        // 全額戻すと、バイタリックの受動生成(バッファ由来ではない)が
                        // 隣接ジャー不在/満杯のあいだ毎周期バッファへ積み上がり、
                        // 保管容量の上限が消える(2026-08-01 の実バグ)。逆に一切戻さないと
                        // 焼べた分が「ジャーが満杯の間だけ無言で消える」。
                        int leftover = sourcelink.supplyAdjacent(block, generated);
                        int refund = SourceYield.refundToBuffer(yield, leftover);
                        if (refund > 0) {
                            sourcelink.addToBuffer(block, refund);
                        }
                        sourcelink.onGenerate(block);
                    }
                }
                staleLocations.forEach(stale -> {
                    locations.remove(stale);
                    Sourcelink.forgetBufferCapWarning(stale);
                });
            });
        }
    }
}
