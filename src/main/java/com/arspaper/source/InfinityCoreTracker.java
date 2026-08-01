package com.arspaper.source;

import com.arspaper.block.BlockKeys;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 設置された {@code infinity_source_core} ブロックの位置を追跡する。
 *
 * <p>マルチブロックのパターン判定はしない(半径判定だけで成立させる)ので、必要なのは
 * 「今どこに置かれているか」の集合だけ。{@link Sourcelink} 側はこの集合に対して
 * {@link #isWithinRadiusOfAny} で問い合わせ、半径内なら {@code transfer.infinity-core.*} の
 * 倍率を適用する。
 *
 * <p>{@link com.arspaper.block.CustomBlockListener} には手を入れず、{@code CUSTOM_BLOCK_ID}
 * PDC を直接読む独立リスナーとして動く(既存の設置/破壊/採掘/爆発ハンドリングへの影響をゼロにするため)。
 * 設置イベントは {@link com.arspaper.block.CustomBlockListener} が {@code HIGH} で処理し
 * バリデーション失敗時にキャンセルすることがあるため、{@code MONITOR} + {@code ignoreCancelled}
 * で「実際に設置が確定した後」だけを拾う。
 */
public class InfinityCoreTracker implements Listener {

    /** {@code sourcelinks.yml} の {@code infinity-core} が対象とするブロックid。 */
    public static final String BLOCK_ID = "infinity_source_core";

    private final JavaPlugin plugin;
    private final Set<Location> cores = ConcurrentHashMap.newKeySet();

    public InfinityCoreTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 起動時にロード済みチャンクをスキャンしてキャッシュを構築し、以後のイベントを購読する。 */
    public void start() {
        rebuildCache();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** 現在追跡中のコア数(デバッグ/ステータス表示用)。 */
    public int size() {
        return cores.size();
    }

    /**
     * 指定座標が、追跡中のいずれかのコアから半径 {@code radius} 以内かどうか。
     * ワールドが異なるコアは対象外。
     */
    public boolean isWithinRadiusOfAny(Location point, int radius) {
        if (radius <= 0 || cores.isEmpty()) {
            return false;
        }
        World world = point.getWorld();
        if (world == null) {
            return false;
        }
        for (Location core : cores) {
            World coreWorld = core.getWorld();
            if (coreWorld == null || !coreWorld.equals(world)) {
                continue;
            }
            boolean within = InfinityCoreEffect.withinRadius(
                    point.getX() - core.getX(),
                    point.getY() - core.getY(),
                    point.getZ() - core.getZ(),
                    radius);
            if (within) {
                return true;
            }
        }
        return false;
    }

    // ---- 設置/破壊の追跡 ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }
        if (isCore(tile)) {
            cores.add(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        // このリスナーの時点ではまだブロックは実体として残っている(除去はイベント処理後)ので、
        // PDCを読んで対象かどうかを確認できる。読めなくても撤去自体は安全側(remove)に倒す。
        cores.remove(loc);
    }

    /**
     * 爆発によるコアの破壊も追跡から外す。{@link com.arspaper.block.CustomBlockListener} が
     * {@code HIGH} でブロックを {@code AIR} に差し替えるため、それより前({@code LOW})で
     * PDC を読んでおく。
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        for (Block block : event.blockList()) {
            removeIfCore(block);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        for (Block block : event.blockList()) {
            removeIfCore(block);
        }
    }

    private void removeIfCore(Block block) {
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }
        if (isCore(tile)) {
            cores.remove(block.getLocation());
        }
    }

    private boolean isCore(TileState tile) {
        String id = tile.getPersistentDataContainer()
                .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        return BLOCK_ID.equals(id);
    }

    // ---- チャンクロード時の再検出(SourcelinkTickTaskと同じ方式) ----

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            return;
        }
        Chunk chunk = event.getChunk();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!chunk.isLoaded()) {
                return;
            }
            for (BlockState state : chunk.getTileEntities()) {
                if (!(state instanceof TileState tile)) {
                    continue;
                }
                if (isCore(tile)) {
                    cores.add(state.getLocation());
                }
            }
        }, 1L);
    }

    private void rebuildCache() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof TileState tile)) {
                        continue;
                    }
                    if (isCore(tile)) {
                        cores.add(state.getLocation());
                        count++;
                    }
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Rebuilt infinity_source_core cache: " + count + " core(s) found");
        }
    }
}
