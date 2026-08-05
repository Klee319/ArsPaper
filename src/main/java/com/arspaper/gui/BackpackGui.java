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
        // BackpackHolderに防具ItemStack参照を持たせ、タイトル文字列ではなくholder型でGUIを判別する。
        BackpackHolder holder = new BackpackHolder(armorItem);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
            Component.text("バックパック", NamedTextColor.DARK_GREEN));
        holder.setInventory(inv);

        // PDCからデータ復元
        loadBackpackContents(armorItem, inv);

        // GUI閉じ時のデータ保存は GuiListener.onInventoryClose（BackpackHolder判別）で処理する。
        player.openInventory(inv);
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

        List<String> serialized;
        try {
            serialized = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
        } catch (Exception e) {
            // JSON自体の解析に失敗した場合のみ全体を諦める（個別スロットは下で個別救済）。
            ArsPaper.getInstance().getLogger().warning(
                "バックパックデータのJSON解析に失敗しました: " + e.getMessage());
            return;
        }
        if (serialized == null) return;

        for (String entry : serialized) {
            int colonIdx = entry.indexOf(':');
            if (colonIdx < 0) continue;
            int slot;
            try {
                slot = Integer.parseInt(entry.substring(0, colonIdx));
            } catch (NumberFormatException e) {
                ArsPaper.getInstance().getLogger().warning(
                    "バックパックのスロット番号解析に失敗しました (entry=" + entry + "): " + e.getMessage());
                continue;
            }
            // スロット単位でデシリアライズ。失敗してもそのスロットのみスキップし、他スロットは保持する
            // （旧実装は1件の失敗で全体を空扱いし、次回保存で全消失していた）。
            try {
                byte[] data = java.util.Base64.getDecoder().decode(entry.substring(colonIdx + 1));
                ItemStack item = ItemStack.deserializeBytes(data);
                if (slot < inv.getSize()) {
                    inv.setItem(slot, item);
                }
            } catch (Exception e) {
                ArsPaper.getInstance().getLogger().warning(
                    "バックパックのスロット " + slot + " の復元に失敗したためスキップします: " + e.getMessage());
            }
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
            appendItemDataLore(meta, lore);
            meta.lore(lore);
        });
    }

    /**
     * バックパックデータを保持しているスレッドにだけ「※ アイテムデータ保持中」行を足す。
     *
     * <p>スレッドの lore を<b>まるごと組み直す</b>経路({@code ThreadItem#fullLore})から呼ばれる:
     * 組み直しでこの行を落とすと、中身は PDC に残っているのに表示だけ消えて
     * 「バックパックの中身が消えた」と誤認される。行の文言/色をここに一本化しておくこと。
     */
    public static void appendItemDataLore(org.bukkit.inventory.meta.ItemMeta meta,
                                          List<net.kyori.adventure.text.Component> lore) {
        if (meta == null || lore == null) {
            return;
        }
        if (!meta.getPersistentDataContainer().has(BACKPACK_THREAD_DATA_KEY, PersistentDataType.STRING)) {
            return;
        }
        lore.add(Component.text("※ アイテムデータ保持中", NamedTextColor.GOLD)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true));
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
