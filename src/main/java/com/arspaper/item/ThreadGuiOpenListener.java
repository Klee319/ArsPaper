package com.arspaper.item;

import com.arspaper.gui.ThreadGui;
import com.arspaper.integration.TrinityForgeBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * TF catalog 防具向けスレッドGUI開放。
 * armors.yml / ConfigurableArmor 撤去後も、スニーク+右クリックで {@link ThreadGui} を開けるようにする。
 * 枠数は item-stats の {@code thread_slots} が正のときのみ(枠の拡張は
 * {@link com.arspaper.ritual.effect.ThreadSlotExpandRitualEffect} が装備自身へ書き込む)。
 */
public final class ThreadGuiOpenListener implements Listener {

    private final JavaPlugin plugin;

    public ThreadGuiOpenListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (!isArmorPiece(item)) {
            return;
        }
        int slots = effectiveThreadSlots(item, player);
        if (slots <= 0) {
            return;
        }
        event.setCancelled(true);
        new ThreadGui(player, item, plugin).open();
    }

    private static boolean isArmorPiece(ItemStack item) {
        try {
            return switch (com.trinityforge.stats.EquipmentSlotResolver.resolve(item.getType())) {
                case HEAD, CHEST, LEGS, FEET -> true;
                default -> false;
            };
        } catch (Throwable ignored) {
            String name = item.getType().name();
            return name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")
                    || name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")
                    || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
        }
    }

    private static int effectiveThreadSlots(ItemStack item, Player player) {
        try {
            Map<String, Double> stats = TrinityForgeBridge.resolveFullItemStats(item);
            return TrinityForgeBridge.tfEffectiveThreadSlotCap(stats, player);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
