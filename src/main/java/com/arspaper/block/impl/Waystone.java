package com.arspaper.block.impl;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import com.arspaper.item.ItemKeys;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ウェイストーン - テレポートコンパスのリンク先となるカスタムブロック。
 * ダイヤモンドブロックの上にのみ設置可能。
 * 右クリックで情報表示、スニーク+右クリックで名前変更。
 */
public class Waystone extends CustomBlock implements Listener {

    public static final NamespacedKey WAYSTONE_NAME = new NamespacedKey("arspaper", "waystone_name");

    /** 名前入力待ちプレイヤー (UUID → ウェイストーンの座標) */
    private static final Map<UUID, Location> pendingNames = new ConcurrentHashMap<>();

    public Waystone(JavaPlugin plugin) {
        super(plugin, "waystone");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.BEACON;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("ウェイストーン", NamedTextColor.DARK_AQUA)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 400001;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.RECOVERY_COMPASS);
        head.editMeta(meta -> meta.setCustomModelData(400001));
        return head;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("テレポートの目印となる魔法石", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("ダイヤモンドブロックの上に設置", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public boolean validatePlacement(Player player, Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.DIAMOND_BLOCK) {
            player.sendMessage(Component.text(
                "ウェイストーンはダイヤモンドブロックの上にのみ設置できます", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        tileState.getPersistentDataContainer().set(
            WAYSTONE_NAME, PersistentDataType.STRING, "無名のウェイストーン");
        tileState.update();

        player.sendMessage(Component.text(
            "ウェイストーンを設置しました！スニーク+右クリックで名前を変更できます", NamedTextColor.DARK_AQUA));
        block.getWorld().spawnParticle(Particle.PORTAL,
            block.getLocation().add(0.5, 1.5, 0.5), 30, 0.3, 0.5, 0.3, 0.5);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        // テレポートコンパスを持っている場合はコンパス側で処理
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.hasItemMeta()) {
            String customId = held.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if ("teleport_compass".equals(customId)) return;
        }

        String name = tileState.getPersistentDataContainer()
            .getOrDefault(WAYSTONE_NAME, PersistentDataType.STRING, "無名のウェイストーン");

        if (player.isSneaking()) {
            // 名前変更モード
            pendingNames.put(player.getUniqueId(), block.getLocation());
            player.sendMessage(Component.text(
                "ウェイストーンの新しい名前をチャットに入力してください（キャンセル: 'cancel'）",
                NamedTextColor.YELLOW));
        } else {
            // 情報表示
            Location loc = block.getLocation();
            player.sendMessage(Component.text("ウェイストーン: ", NamedTextColor.DARK_AQUA)
                .append(Component.text(name, NamedTextColor.WHITE)));
            player.sendMessage(Component.text(
                "座標: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                    + " (" + loc.getWorld().getName() + ")",
                NamedTextColor.GRAY));
        }
    }

    /**
     * チャットイベントで名前入力を受け取る。
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Location waystoneLocation = pendingNames.remove(player.getUniqueId());
        if (waystoneLocation == null) return;

        event.setCancelled(true);
        // Discord連携プラグイン等への漏洩を防止
        event.viewers().clear();

        String inputName = PlainTextComponentSerializer.plainText().serialize(event.message());

        if ("cancel".equalsIgnoreCase(inputName)) {
            player.sendMessage(Component.text("名前変更をキャンセルしました", NamedTextColor.GRAY));
            return;
        }

        // メインスレッドでTileState更新
        org.bukkit.Bukkit.getScheduler().runTask(ArsPaper.getInstance(), () -> {
            Block block = waystoneLocation.getBlock();
            if (!(block.getState() instanceof TileState ts)) return;
            String blockId = ts.getPersistentDataContainer()
                .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
            if (!"waystone".equals(blockId)) return;

            ts.getPersistentDataContainer().set(
                WAYSTONE_NAME, PersistentDataType.STRING, inputName);
            ts.update();

            player.sendMessage(Component.text("ウェイストーンの名前を ", NamedTextColor.GREEN)
                .append(Component.text(inputName, NamedTextColor.WHITE))
                .append(Component.text(" に変更しました", NamedTextColor.GREEN)));
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        });
    }

    /**
     * 名前入力モードのキャンセル（プレイヤー切断時など）。
     */
    public static void cancelPendingName(UUID uuid) {
        pendingNames.remove(uuid);
    }
}
