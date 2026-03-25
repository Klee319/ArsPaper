package com.arspaper.source.sourcelink;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sourcelink（Source生成装置）の抽象基底クラス。
 * 隣接するSource Jarに生成したSourceを自動供給する。
 */
public abstract class Sourcelink extends CustomBlock {

    protected Sourcelink(JavaPlugin plugin, String blockId) {
        super(plugin, blockId);
    }

    /**
     * 1回のティックで生成するSource量。
     * 0を返すと生成しない。
     */
    public abstract int generateSource();

    /**
     * Source生成時の追加処理（副産物の生成など）。
     */
    public void onGenerate(Block block) {}

    /**
     * 隣接するSource JarにSourceを供給する。
     * SourceNetworkの転送タスクとは別に、
     * 直接隣接ブロックへの即時供給を行う。
     */
    public void supplyAdjacent(Block block, int amount) {
        Location loc = block.getLocation();
        int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

        int remaining = amount;
        for (int[] offset : offsets) {
            if (remaining <= 0) break;

            Block adjacent = loc.getWorld().getBlockAt(
                loc.getBlockX() + offset[0],
                loc.getBlockY() + offset[1],
                loc.getBlockZ() + offset[2]
            );

            if (!(adjacent.getState() instanceof TileState tile)) continue;

            String blockId = tile.getPersistentDataContainer()
                .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
            if (!"source_jar".equals(blockId)) continue;

            // 無限ソースジャーはスキップ（書き込み不要）
            if (com.arspaper.block.impl.SourceJar.isInfinite(tile)) continue;

            int added = com.arspaper.block.impl.SourceJar.addSource(tile, remaining);
            remaining -= added;
        }
    }
}
