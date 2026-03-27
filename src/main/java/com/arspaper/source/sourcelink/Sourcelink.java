package com.arspaper.source.sourcelink;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sourcelink（Source生成装置）の抽象基底クラス。
 * 隣接するSource Jarに生成したSourceを自動供給する。
 *
 * 燃料消費型: PDCにソースポイントを蓄積し、tickごとに排出。
 * イベント駆動型: 外部イベントで直接supplyAdjacentを呼び出す。
 */
public abstract class Sourcelink extends CustomBlock {

    /** 蓄積されたソースポイントを保持するPDCキー */
    public static final NamespacedKey SOURCE_BUFFER = new NamespacedKey("arspaper", "sourcelink_buffer");

    /** 1tickあたりの最大排出量 */
    private static final int MAX_DRAIN_PER_TICK = 50;

    protected Sourcelink(JavaPlugin plugin, String blockId) {
        super(plugin, blockId);
    }

    /**
     * 1回のティックで生成するSource量。
     * ブロックのTileStateを参照してバッファから排出等の判断が可能。
     * 0を返すと生成しない。
     */
    public abstract int generateSource(Block block);

    /**
     * 指定アイテムのソースポイント値を返す。対応しないアイテムは0。
     * ホッパー連携で使用。
     */
    public int getSourceValueForItem(org.bukkit.inventory.ItemStack item) {
        return 0;
    }

    /**
     * Source生成時の追加処理（副産物の生成など）。
     */
    public void onGenerate(Block block) {}

    /**
     * バッファからソースポイントを取得する。
     */
    protected int getBuffer(TileState tile) {
        return tile.getPersistentDataContainer()
            .getOrDefault(SOURCE_BUFFER, PersistentDataType.INTEGER, 0);
    }

    /**
     * バッファにソースポイントを設定する。
     */
    protected void setBuffer(TileState tile, int amount) {
        tile.getPersistentDataContainer().set(SOURCE_BUFFER, PersistentDataType.INTEGER, amount);
        tile.update();
    }

    /**
     * バッファにソースポイントを追加する。
     */
    public void addToBuffer(Block block, int amount) {
        if (!(block.getState() instanceof TileState tile)) return;
        setBuffer(tile, getBuffer(tile) + amount);
    }

    /**
     * バッファから最大MAX_DRAIN_PER_TICKを排出する。排出量を返す。
     */
    protected int drainBuffer(Block block) {
        if (!(block.getState() instanceof TileState tile)) return 0;
        int buffer = getBuffer(tile);
        if (buffer <= 0) return 0;
        int drain = Math.min(buffer, MAX_DRAIN_PER_TICK);
        setBuffer(tile, buffer - drain);
        return drain;
    }

    /**
     * 隣接するSource JarにSourceを供給する。
     * 供給成功時にパーティクルとサウンドを再生。
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
            if (added > 0) {
                remaining -= added;
                // ソース瓶に溜まるエフェクト
                spawnJarFillFx(adjacent.getLocation());
            }
        }
    }

    /**
     * ソース瓶にソースが供給された時のパーティクル＆サウンド。
     */
    private void spawnJarFillFx(Location jarLoc) {
        Location center = jarLoc.clone().add(0.5, 0.8, 0.5);
        jarLoc.getWorld().spawnParticle(
            Particle.ENCHANT, center, 8, 0.2, 0.3, 0.2, 0.5);
        jarLoc.getWorld().spawnParticle(
            Particle.WITCH, center, 4, 0.15, 0.2, 0.15, 0.02);
        jarLoc.getWorld().playSound(center,
            Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.4f, 1.4f);
    }
}
