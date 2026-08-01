package com.arspaper.block.impl;

import com.arspaper.ArsPaper;
import com.arspaper.block.CustomBlock;
import com.arspaper.source.InfinityCoreTracker;
import com.arspaper.source.SourceTransferConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 無限ソース核 - 「1億ソース到達」の証明アイテムを<b>設置して機能させる</b>カスタムブロック
 * (2026-08-01 確定仕様 柱6)。
 *
 * <p>マルチブロックのパターン判定はしない。設置位置は {@link InfinityCoreTracker} が追跡し、
 * その半径内にあるソースリンクの転送量・バッファ上限へ {@code sourcelinks.yml} の
 * {@code transfer.infinity-core.*} の倍率が乗る(実際の適用は {@code Sourcelink} 側)。
 *
 * <p>到達証明であることに変わりはないため<b>消費されない</b> ―― 右クリックで回収して
 * 別の場所へ据え直せる(バニラの「置いて壊すと手元に戻る」通常挙動のまま)。
 */
public class InfinitySourceCore extends CustomBlock {

    public InfinitySourceCore(JavaPlugin plugin) {
        super(plugin, InfinityCoreTracker.BLOCK_ID);
    }

    @Override
    public Material getBlockMaterial() {
        return Material.BEACON;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("無限ソース核", NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 500001;
    }

    @Override
    protected List<Component> getDefaultLore() {
        return List.of(
            Component.text("第2の目標「1億ソース」の到達証明", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("設置すると周囲のソースリンクを強化する", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("右クリックで回収可能(消費されない)", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        );
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.NETHER_STAR);
        head.editMeta(meta -> meta.setCustomModelData(getCustomModelData()));
        return head;
    }

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        // 追跡は InfinityCoreTracker が BlockPlaceEvent(MONITOR) を直接見て行うため、
        // ここでは演出のみ。
        player.sendMessage(Component.text(
            "無限ソース核を設置しました。半径内のソースリンクが強化されます", NamedTextColor.GOLD));
        block.getWorld().spawnParticle(Particle.END_ROD,
            block.getLocation().add(0.5, 1.5, 0.5), 40, 0.4, 0.6, 0.4, 0.05);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        SourceTransferConfig cfg = transferConfig();
        int radius = cfg.infinityCoreRadius();
        player.sendMessage(Component.text("無限ソース核", NamedTextColor.GOLD));
        player.sendMessage(Component.text(
            "半径 " + radius + " ブロック内のソースリンクを転送量×"
                + cfg.infinityCoreTransferMultiplier() + " / バッファ上限×"
                + cfg.infinityCoreBufferMultiplier() + " で強化中",
            NamedTextColor.AQUA));
    }

    /**
     * 転送設定。{@link com.arspaper.source.sourcelink.Sourcelink#transferConfig} と同じフォールバック方針
     * (ArsPaper未初期化/config未読込でも落ちない)。
     */
    private static SourceTransferConfig transferConfig() {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourcelinkConfig() == null) {
            return SourceTransferConfig.defaults();
        }
        return ars.getSourcelinkConfig().transfer();
    }
}
