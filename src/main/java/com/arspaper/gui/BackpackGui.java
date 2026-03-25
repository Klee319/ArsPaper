package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * バックパックスレッドのインベントリGUI。
 * 防具PDCにバックパックデータを保存し、スレッド取り外し時にスレッドアイテムに転写する。
 *
 * バックパック1つ=27スロット、2つ=54スロット。
 * 全データは防具PDCのJSON配列（Base64エンコード済みItemStack）に保存。
 */
public class BackpackGui {

    private static final NamespacedKey BACKPACK_DATA_KEY = new NamespacedKey("arspaper", "backpack_data");
    private static final NamespacedKey BACKPACK_THREAD_DATA_KEY = new NamespacedKey("arspaper", "backpack_thread_data");
    private static final Gson GSON = new Gson();

    /**
     * バックパックGUIを開く。
     * 防具PDCからデータを読み込み、閉じた時にPDCに書き戻す。
     */
    public static void open(Player player, ItemStack armorItem) {
        int backpackCount = countBackpackThreads(armorItem);
        if (backpackCount <= 0) {
            player.sendMessage(Component.text("バックパックスレッドが装着されていません", NamedTextColor.RED));
            return;
        }

        int slots = Math.min(backpackCount, 2); // 最大2段(54スロット)
        int rows = slots * 3; // 1バックパック=3行
        Inventory inv = Bukkit.createInventory(null, rows * 9,
            Component.text("バックパック", NamedTextColor.DARK_GREEN));

        // PDCからデータ復元
        loadBackpackContents(armorItem, inv);

        // GUI閉じ時にデータ保存するためのリスナーは GuiListener で処理
        // armorItem参照を保持するためにPDCマーカーを使用
        player.openInventory(inv);

        // 遅延保存タスク: GUIが閉じられた時にデータを保存
        Bukkit.getScheduler().runTaskLater(ArsPaper.getInstance(), () -> {
            // プレイヤーがまだこのGUIを開いていたら、閉じた時に保存される
        }, 1L);
    }

    /**
     * バックパックの内容を防具PDCに保存する。
     */
    public static void saveBackpackContents(ItemStack armorItem, Inventory backpackInv) {
        List<String> serialized = new ArrayList<>();
        for (int i = 0; i < backpackInv.getSize(); i++) {
            ItemStack item = backpackInv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                serialized.add(i + ":" + java.util.Base64.getEncoder().encodeToString(
                    item.serializeAsBytes()));
            }
        }

        armorItem.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                BACKPACK_DATA_KEY, PersistentDataType.STRING, GSON.toJson(serialized));
        });
    }

    /**
     * 防具PDCからバックパック内容を復元する。
     */
    public static void loadBackpackContents(ItemStack armorItem, Inventory inv) {
        if (!armorItem.hasItemMeta()) return;
        String json = armorItem.getItemMeta().getPersistentDataContainer()
            .get(BACKPACK_DATA_KEY, PersistentDataType.STRING);
        if (json == null) return;

        try {
            List<String> serialized = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
            if (serialized == null) return;

            for (String entry : serialized) {
                int colonIdx = entry.indexOf(':');
                if (colonIdx < 0) continue;
                int slot = Integer.parseInt(entry.substring(0, colonIdx));
                byte[] data = java.util.Base64.getDecoder().decode(entry.substring(colonIdx + 1));
                ItemStack item = ItemStack.deserializeBytes(data);
                if (slot < inv.getSize()) {
                    inv.setItem(slot, item);
                }
            }
        } catch (Exception e) {
            // デシリアライズ失敗時は空のバックパックとして扱う
        }
    }

    /**
     * スレッド取り外し時: バックパックデータを防具からスレッドアイテムに転写する。
     */
    public static void transferDataToThread(ItemStack armorItem, ItemStack threadItem) {
        if (!armorItem.hasItemMeta()) return;
        String json = armorItem.getItemMeta().getPersistentDataContainer()
            .get(BACKPACK_DATA_KEY, PersistentDataType.STRING);
        if (json == null || "[]".equals(json)) return;

        threadItem.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                BACKPACK_THREAD_DATA_KEY, PersistentDataType.STRING, json);
            // Loreにデータありの表示追加
            List<net.kyori.adventure.text.Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            else lore = new ArrayList<>(lore);
            lore.add(Component.text("※ アイテムデータ保持中", NamedTextColor.GOLD)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true));
            meta.lore(lore);
        });
    }

    /**
     * スレッドセット時: スレッドアイテムから防具にバックパックデータを転写する。
     */
    public static void transferDataFromThread(ItemStack threadItem, ItemStack armorItem) {
        if (!threadItem.hasItemMeta()) return;
        String json = threadItem.getItemMeta().getPersistentDataContainer()
            .get(BACKPACK_THREAD_DATA_KEY, PersistentDataType.STRING);
        if (json == null) return;

        armorItem.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                BACKPACK_DATA_KEY, PersistentDataType.STRING, json);
        });
    }

    /**
     * 防具のバックパックスレッド数をカウントする。
     */
    public static int countBackpackThreads(ItemStack armorItem) {
        if (!armorItem.hasItemMeta()) return 0;
        String json = armorItem.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.THREAD_SLOTS, PersistentDataType.STRING);
        if (json == null) return 0;

        int count = 0;
        try {
            List<String> slots = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
            if (slots != null) {
                for (String id : slots) {
                    if ("backpack".equals(id)) count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    /**
     * バックパック内にアイテムが残っているかチェック。
     */
    public static boolean hasContents(ItemStack armorItem) {
        if (!armorItem.hasItemMeta()) return false;
        String json = armorItem.getItemMeta().getPersistentDataContainer()
            .get(BACKPACK_DATA_KEY, PersistentDataType.STRING);
        return json != null && !"[]".equals(json) && !json.isEmpty();
    }
}
