package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.arspaper.mana.ManaManager;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * メイジアーマーの装備状態を監視し、マナボーナスを計算・更新する。
 * Paper固有のPlayerArmorChangeEventを使用して効率的に検知。
 * ボーナス変更時にBossBarとcurrentManaも同期する。
 */
public class ArmorManaListener implements Listener {

    private final JavaPlugin plugin;

    public ArmorManaListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        scheduleRecalc(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
            || event.isShiftClick()) {
            scheduleRecalc(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        recalculateArmorBonus(event.getPlayer());
    }

    private void scheduleRecalc(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    recalculateArmorBonus(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * プレイヤーの装備中メイジアーマーのマナボーナス合計を再計算。
     * BossBarとcurrentManaも同期する。
     */
    public static void recalculateArmorBonus(Player player) {
        int totalBonus = 0;

        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) continue;

            PersistentDataContainer pdc = armorPiece.getItemMeta().getPersistentDataContainer();
            String itemId = pdc.get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if (itemId == null || !itemId.startsWith("mage_")) continue;

            Integer tier = pdc.get(ItemKeys.ARMOR_TIER, PersistentDataType.INTEGER);
            if (tier != null) {
                totalBonus += ArmorTier.fromTier(tier).getManaBonus();
            }
        }

        player.getPersistentDataContainer().set(
            ManaKeys.ARMOR_MANA_BONUS, PersistentDataType.INTEGER, totalBonus
        );

        // BossBar再描画 & currentManaが新maxManaを超えていたらクランプ
        ManaManager manaManager = ArsPaper.getInstance().getManaManager();
        if (manaManager != null) {
            int currentMana = manaManager.getCurrentMana(player);
            int newMax = manaManager.getMaxMana(player);
            if (currentMana > newMax) {
                currentMana = newMax;
            }
            manaManager.setCurrentMana(player, currentMana);
        }
    }
}
