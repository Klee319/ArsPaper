package com.arspaper.source;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlockRegistry;
import com.arspaper.source.sourcelink.Sourcelink;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * ロード済みチャンク内のSourcelinkを定期的にティックし、
 * Source生成 → 隣接Source Jar供給を行うタスク。
 */
public class SourcelinkTickTask {

    private static final int TICK_INTERVAL = 100; // 5秒
    private final JavaPlugin plugin;
    private final CustomBlockRegistry blockRegistry;
    private BukkitTask task;

    public SourcelinkTickTask(JavaPlugin plugin, CustomBlockRegistry blockRegistry) {
        this.plugin = plugin;
        this.blockRegistry = blockRegistry;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void tick() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof TileState tileState)) continue;

                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (blockId == null) continue;

                    blockRegistry.get(blockId).ifPresent(customBlock -> {
                        if (customBlock instanceof Sourcelink sourcelink) {
                            int generated = sourcelink.generateSource();
                            if (generated > 0) {
                                sourcelink.supplyAdjacent(state.getBlock(), generated);
                                sourcelink.onGenerate(state.getBlock());
                            }
                        }
                    });
                }
            }
        }
    }
}
