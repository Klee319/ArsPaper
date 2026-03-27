package com.arspaper.block;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * カスタムブロック位置をキャッシュし、定期的にパーティクルを放出するタスク。
 * ArmorStandベースの表示に代わる軽量な視覚フィードバック。
 */
public class BlockParticleTask extends BukkitRunnable implements Listener {

    private static final int PARTICLE_RANGE = 32;
    private static final long TICK_INTERVAL = 20L;

    private final JavaPlugin plugin;
    private final Map<World, Set<Location>> blockCache = new ConcurrentHashMap<>();

    public BlockParticleTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        rebuildCache();
        runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * ロード済みチャンクからカスタムブロックを検出してキャッシュを再構築する。
     * サーバー再起動時にインメモリキャッシュを復元するために使用。
     */
    private void rebuildCache() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof TileState tileState)) continue;
                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (blockId != null) {
                        blockCache.computeIfAbsent(world, w -> ConcurrentHashMap.newKeySet())
                            .add(state.getLocation());
                        count++;
                    }
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Rebuilt block particle cache: " + count + " custom blocks found");
        }
    }

    public void addBlock(Location location, String blockId) {
        blockCache.computeIfAbsent(location.getWorld(), w -> ConcurrentHashMap.newKeySet())
            .add(location.getBlock().getLocation());
    }

    public void removeBlock(Location location) {
        Set<Location> worldBlocks = blockCache.get(location.getWorld());
        if (worldBlocks != null) {
            worldBlocks.remove(location.getBlock().getLocation());
        }
    }

    @Override
    public void run() {
        // ワールドごとにブロックを処理し、近くにプレイヤーがいるブロックのみパーティクルを生成
        // world.spawnParticle は全プレイヤーにブロードキャストされるため、1回だけ呼ぶ
        for (Map.Entry<World, Set<Location>> entry : blockCache.entrySet()) {
            World world = entry.getKey();
            Set<Location> worldBlocks = entry.getValue();
            if (worldBlocks == null || worldBlocks.isEmpty()) continue;

            List<Player> worldPlayers = world.getPlayers();
            if (worldPlayers.isEmpty()) continue;

            List<Location> staleBlocks = new ArrayList<>();
            for (Location blockLoc : worldBlocks) {
                // チャンクが未ロードの場合はスキップ（stale扱いしない）
                if (!world.isChunkLoaded(blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4)) {
                    continue;
                }

                // いずれかのプレイヤーが範囲内にいるかチェック
                boolean anyNearby = false;
                for (Player player : worldPlayers) {
                    if (blockLoc.distanceSquared(player.getLocation()) <= PARTICLE_RANGE * PARTICLE_RANGE) {
                        anyNearby = true;
                        break;
                    }
                }
                if (!anyNearby) continue;

                Block block = blockLoc.getBlock();
                if (!(block.getState() instanceof TileState tileState)) {
                    staleBlocks.add(blockLoc);
                    continue;
                }

                String blockId = tileState.getPersistentDataContainer()
                    .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                if (blockId == null) {
                    staleBlocks.add(blockLoc);
                    continue;
                }

                spawnParticleForBlock(blockLoc, blockId);
            }
            staleBlocks.forEach(worldBlocks::remove);
        }
    }

    /**
     * チャンクロード時にカスタムブロックをキャッシュに復元する。
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof TileState tileState)) continue;
            String blockId = tileState.getPersistentDataContainer()
                .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
            if (blockId != null) {
                blockCache.computeIfAbsent(world, w -> ConcurrentHashMap.newKeySet())
                    .add(state.getLocation());
            }
        }
    }

    private void spawnParticleForBlock(Location loc, String blockId) {
        Location center = loc.clone().add(0.5, 1.2, 0.5);
        World world = loc.getWorld();

        switch (blockId) {
            case "scribing_table" ->
                world.spawnParticle(Particle.ENCHANT, center, 3, 0.3, 0.2, 0.3, 0.5);
            case "source_jar" ->
                world.spawnParticle(Particle.END_ROD, center, 1, 0.2, 0.2, 0.2, 0.02);
            case "volcanic_sourcelink" ->
                world.spawnParticle(Particle.FLAME, center, 2, 0.2, 0.1, 0.2, 0.01);
            case "mycelial_sourcelink" ->
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 2, 0.3, 0.2, 0.3, 0);
            case "ritual_core" ->
                world.spawnParticle(Particle.DUST, center, 1, 0.3, 0.2, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(140, 0, 50), 0.7f));
            case "pedestal" ->
                world.spawnParticle(Particle.DUST, center, 2, 0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(128, 0, 255), 0.8f));
        }
    }
}
