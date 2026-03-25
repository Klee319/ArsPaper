package com.arspaper.item.impl;

import com.arspaper.block.BlockKeys;
import com.arspaper.block.impl.Waystone;
import com.arspaper.item.BaseCustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * テレポートコンパス - ウェイストーンにリンクしてテレポートできるアイテム。
 * ウェイストーンに右クリックでリンク、空中で右クリックでテレポート。
 */
public class TeleportCompass extends BaseCustomItem {

    private static final NamespacedKey LINKED_WORLD = new NamespacedKey("arspaper", "tp_compass_world");
    private static final NamespacedKey LINKED_X = new NamespacedKey("arspaper", "tp_compass_x");
    private static final NamespacedKey LINKED_Y = new NamespacedKey("arspaper", "tp_compass_y");
    private static final NamespacedKey LINKED_Z = new NamespacedKey("arspaper", "tp_compass_z");
    private static final NamespacedKey LINKED_NAME = new NamespacedKey("arspaper", "tp_compass_name");

    public TeleportCompass(JavaPlugin plugin) {
        super(plugin, "teleport_compass");
    }

    @Override
    public Material getBaseMaterial() {
        return Material.COMPASS;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("テレポートコンパス", NamedTextColor.DARK_AQUA)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 100020;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("ウェイストーンに右クリックで紐づけ", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("右クリックでテレポート", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack compass = event.getItem();
        if (compass == null) return;

        Block clicked = event.getClickedBlock();

        // ウェイストーンへのリンク処理
        if (clicked != null && clicked.getState() instanceof TileState ts) {
            String blockId = ts.getPersistentDataContainer()
                .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
            if ("waystone".equals(blockId)) {
                linkToWaystone(player, compass, clicked, ts);
                return;
            }
        }

        // テレポート処理
        teleport(player, compass);
    }

    /**
     * コンパスをウェイストーンにリンクする。
     */
    private void linkToWaystone(Player player, ItemStack compass, Block block, TileState tileState) {
        String waystoneName = tileState.getPersistentDataContainer()
            .getOrDefault(Waystone.WAYSTONE_NAME, PersistentDataType.STRING, "無名のウェイストーン");

        Location loc = block.getLocation();
        compass.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(LINKED_WORLD, PersistentDataType.STRING, loc.getWorld().getName());
            pdc.set(LINKED_X, PersistentDataType.INTEGER, loc.getBlockX());
            pdc.set(LINKED_Y, PersistentDataType.INTEGER, loc.getBlockY());
            pdc.set(LINKED_Z, PersistentDataType.INTEGER, loc.getBlockZ());
            pdc.set(LINKED_NAME, PersistentDataType.STRING, waystoneName);

            // Lore更新
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("リンク先: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(waystoneName, NamedTextColor.WHITE)));
            lore.add(Component.text("座標: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                    + " (" + loc.getWorld().getName() + ")", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("右クリックでテレポート", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });

        player.sendMessage(Component.text("コンパスを ", NamedTextColor.GREEN)
            .append(Component.text(waystoneName, NamedTextColor.WHITE))
            .append(Component.text(" にリンクしました", NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.2f);
        block.getWorld().spawnParticle(Particle.PORTAL,
            block.getLocation().add(0.5, 1.5, 0.5), 20, 0.3, 0.5, 0.3, 0.5);
    }

    /**
     * リンク先のウェイストーンにテレポートする。
     */
    private void teleport(Player player, ItemStack compass) {
        if (!compass.hasItemMeta()) {
            player.sendMessage(Component.text(
                "ウェイストーンにリンクされていません", NamedTextColor.RED));
            return;
        }

        PersistentDataContainer pdc = compass.getItemMeta().getPersistentDataContainer();
        String worldName = pdc.get(LINKED_WORLD, PersistentDataType.STRING);
        if (worldName == null) {
            player.sendMessage(Component.text(
                "ウェイストーンにリンクされていません", NamedTextColor.RED));
            return;
        }

        Integer x = pdc.get(LINKED_X, PersistentDataType.INTEGER);
        Integer y = pdc.get(LINKED_Y, PersistentDataType.INTEGER);
        Integer z = pdc.get(LINKED_Z, PersistentDataType.INTEGER);
        String linkedName = pdc.getOrDefault(LINKED_NAME, PersistentDataType.STRING, "不明");

        if (x == null || y == null || z == null) {
            player.sendMessage(Component.text("リンクデータが破損しています", NamedTextColor.RED));
            return;
        }

        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(Component.text("ワールドが見つかりません: " + worldName, NamedTextColor.RED));
            return;
        }

        // ウェイストーンが存在するか確認
        Block target = world.getBlockAt(x, y, z);
        if (!(target.getState() instanceof TileState ts)) {
            player.sendMessage(Component.text(
                "リンク先のウェイストーンが破壊されています", NamedTextColor.RED));
            clearLink(compass);
            return;
        }
        String blockId = ts.getPersistentDataContainer()
            .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (!"waystone".equals(blockId)) {
            player.sendMessage(Component.text(
                "リンク先のウェイストーンが破壊されています", NamedTextColor.RED));
            clearLink(compass);
            return;
        }

        // テレポート実行
        Location from = player.getLocation();
        Location destination = new Location(world, x + 0.5, y + 1.0, z + 0.5,
            player.getLocation().getYaw(), player.getLocation().getPitch());

        // 出発エフェクト
        from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.5);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        player.teleport(destination);

        // 到着エフェクト
        world.spawnParticle(Particle.PORTAL, destination.clone().add(0, 0.5, 0), 40, 0.5, 1, 0.5, 0.5);
        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        player.sendMessage(Component.text(linkedName, NamedTextColor.WHITE)
            .append(Component.text(" にテレポートしました", NamedTextColor.DARK_AQUA)));
    }

    /**
     * リンクを解除する（ウェイストーン破壊時）。
     */
    private void clearLink(ItemStack compass) {
        compass.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.remove(LINKED_WORLD);
            pdc.remove(LINKED_X);
            pdc.remove(LINKED_Y);
            pdc.remove(LINKED_Z);
            pdc.remove(LINKED_NAME);
            meta.lore(List.of(
                Component.text("ウェイストーンに右クリックで紐づけ", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("右クリックでテレポート", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        });
    }
}
