package com.arspaper.item.impl;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.source.SourceNetwork;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Dominion Wand - Source Relay/Jar/Sourcelink間の接続を設定するツール。
 *
 * 使い方:
 * 1. 送信元ブロックを右クリック → 選択
 * 2. 送信先ブロックを右クリック → 接続確立
 * 3. スニーク+右クリック → 選択解除
 * 4. 空中を右クリック → 接続先一覧表示
 */
public class Wand extends BaseCustomItem {

    private final Map<UUID, Location> selectedSource = new HashMap<>();

    /**
     * プレイヤーログアウト時にメモリをクリーンアップする。
     */
    public void clearSelection(UUID playerId) {
        selectedSource.remove(playerId);
    }

    public Wand(JavaPlugin plugin) {
        super(plugin, "dominion_wand");
    }

    @Override
    public Material getBaseMaterial() {
        return Material.STICK;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("ドミニオンワンド", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 100002;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();

        // スニーク → 選択解除
        if (player.isSneaking()) {
            selectedSource.remove(player.getUniqueId());
            player.sendMessage(Component.text("選択を解除しました", NamedTextColor.GRAY));
            return;
        }

        // ブロッククリック
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            handleBlockClick(player, event.getClickedBlock());
            return;
        }

        // 空中クリック → 選択中の接続先一覧
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            showConnectionInfo(player);
        }
    }

    private void handleBlockClick(Player player, Block block) {
        if (!(block.getState() instanceof TileState tileState)) {
            player.sendMessage(Component.text("有効なSourceブロックではありません！", NamedTextColor.RED));
            return;
        }

        String blockId = tileState.getPersistentDataContainer()
            .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) {
            player.sendMessage(Component.text("カスタムブロックではありません！", NamedTextColor.RED));
            return;
        }

        Location clickedLoc = block.getLocation();
        Location selected = selectedSource.get(player.getUniqueId());

        if (selected == null) {
            // 送信元を選択
            selectedSource.put(player.getUniqueId(), clickedLoc);
            player.sendMessage(Component.text(
                "送信元を選択: " + blockId + " (" + formatLocation(clickedLoc) + ")",
                NamedTextColor.GOLD
            ));
            // パーティクルで選択を可視化
            spawnSelectionParticle(clickedLoc);
        } else {
            // 送信先に接続
            if (selected.equals(clickedLoc)) {
                player.sendMessage(Component.text("自分自身には接続できません！", NamedTextColor.RED));
                return;
            }

            SourceNetwork network = ArsPaper.getInstance().getSourceNetwork();
            if (network.connect(selected, clickedLoc)) {
                player.sendMessage(Component.text(
                    "接続完了！ " + formatLocation(selected) + " → " + formatLocation(clickedLoc),
                    NamedTextColor.GREEN
                ));
                // 接続パーティクル
                spawnConnectionParticle(selected, clickedLoc);
            } else {
                player.sendMessage(Component.text(
                    "接続失敗！（最大距離: 30ブロック、同一ワールドのみ）",
                    NamedTextColor.RED
                ));
            }

            selectedSource.remove(player.getUniqueId());
        }
    }

    private void showConnectionInfo(Player player) {
        Location selected = selectedSource.get(player.getUniqueId());
        if (selected == null) {
            player.sendMessage(Component.text(
                "Sourceブロックを右クリックして選択してください", NamedTextColor.GRAY
            ));
            return;
        }

        SourceNetwork network = ArsPaper.getInstance().getSourceNetwork();
        Set<Location> connections = network.getConnections(selected);

        player.sendMessage(Component.text(
            "=== " + formatLocation(selected) + " の接続先 ===",
            NamedTextColor.GOLD
        ));

        if (connections.isEmpty()) {
            player.sendMessage(Component.text("  接続先なし", NamedTextColor.GRAY));
        } else {
            for (Location loc : connections) {
                player.sendMessage(Component.text(
                    "  → " + formatLocation(loc), NamedTextColor.AQUA
                ));
            }
        }
    }

    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private void spawnSelectionParticle(Location loc) {
        Location center = loc.clone().add(0.5, 1.2, 0.5);
        loc.getWorld().spawnParticle(Particle.END_ROD, center, 10, 0.3, 0.3, 0.3, 0.02);
    }

    private void spawnConnectionParticle(Location from, Location to) {
        Location start = from.clone().add(0.5, 0.5, 0.5);
        Location end = to.clone().add(0.5, 0.5, 0.5);
        double distance = start.distance(end);
        int points = (int) (distance * 2);

        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            Location point = start.clone().add(
                (end.getX() - start.getX()) * t,
                (end.getY() - start.getY()) * t,
                (end.getZ() - start.getZ()) * t
            );
            from.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0);
        }
    }
}
