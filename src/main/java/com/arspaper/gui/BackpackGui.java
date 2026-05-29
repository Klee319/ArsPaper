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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * バックパックスレッドのインベントリGUI。
 * 防具PDCにバックパックデータを保存し、スレッド取り外し時にスレッドアイテムに転写する。
 *
 * 1防具部位=独立した1収納。スレッド数に応じて1つ=27スロット、2つ=54スロット。
 * 全データは防具PDCのJSON配列（Base64エンコード済みItemStack）に保存。
 *
 * 識別は{@link BackpackHolder}で行い、対象防具は装備スロットで一意に保持する。
 */
public class BackpackGui {

    private static final NamespacedKey BACKPACK_DATA_KEY = new NamespacedKey("arspaper", "backpack_data");
    private static final NamespacedKey BACKPACK_THREAD_DATA_KEY = new NamespacedKey("arspaper", "backpack_thread_data");
    private static final Gson GSON = new Gson();

    /** バックパック対象となる防具スロット。 */
    static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /**
     * プレイヤーのバックパックを開く。
     * backpackスレッドを持つ装備部位が1つなら直接開き、複数なら部位選択GUIを表示する。
     */
    public static void openForPlayer(Player player) {
        PlayerInventory pinv = player.getInventory();
        List<EquipmentSlot> slots = new ArrayList<>();
        for (EquipmentSlot s : ARMOR_SLOTS) {
            ItemStack armor = pinv.getItem(s);
            if (armor != null && countBackpackThreads(armor) > 0) {
                slots.add(s);
            }
        }
        if (slots.isEmpty()) {
            player.sendMessage(Component.text("バックパックスレッドが装備されていません", NamedTextColor.RED));
            return;
        }
        if (slots.size() == 1) {
            EquipmentSlot s = slots.get(0);
            open(player, pinv.getItem(s), s);
            return;
        }
        new BackpackSelectionGui(player, slots).open();
    }

    /**
     * 指定した装備スロットの防具のバックパックGUIを開く。
     * 防具PDCからデータを読み込み、閉じた時に同一スロットの防具へ書き戻す。
     */
    public static void open(Player player, ItemStack armorItem, EquipmentSlot armorSlot) {
        if (armorItem == null) return;
        int backpackCount = countBackpackThreads(armorItem);
        if (backpackCount <= 0) {
            player.sendMessage(Component.text("バックパックスレッドが装着されていません", NamedTextColor.RED));
            return;
        }

        int sections = Math.min(backpackCount, 2); // 最大2段(54スロット)
        int rows = sections * 3; // 1バックパック=3行
        BackpackHolder holder = new BackpackHolder(armorSlot);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
            Component.text("バックパック", NamedTextColor.DARK_GREEN));
        holder.setInventory(inv);

        // PDCからデータ復元
        loadBackpackContents(armorItem, inv);

        player.openInventory(inv);
    }

    /**
     * バックパックを閉じる/切断/サーバー停止時に、対象スロットの防具へ無条件で保存する。
     * 対象防具が見つからない（外された等）場合は中身をプレイヤーへ返却してロストを防ぐ。
     */
    public static void saveOnClose(Player player, BackpackHolder holder, Inventory inv) {
        EquipmentSlot slot = holder.getArmorSlot();
        PlayerInventory pinv = player.getInventory();
        ItemStack armor = pinv.getItem(slot);
        if (armor != null && countBackpackThreads(armor) > 0) {
            saveBackpackContents(armor, inv);
            // editMetaで変更されたItemStackを装備スロットに書き戻す
            pinv.setItem(slot, armor);
        } else {
            // 対象防具が見つからない → 中身を返却（ロスト防止）
            returnContents(player, inv);
        }
    }

    /**
     * プレイヤーがバックパックを開いている場合、その内容を保存する（onDisable用）。
     */
    public static void saveIfOpen(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder(false) instanceof BackpackHolder holder) {
            saveOnClose(player, holder, top);
        }
    }

    /**
     * インベントリの中身をプレイヤーへ返却する。満杯時は足元にドロップする。
     */
    private static void returnContents(Player player, Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            inv.setItem(i, null);
        }
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
            // データ保持スレッドはスタック不可にして複製を防止
            meta.setMaxStackSize(1);
            // Loreにデータありの表示追加
            List<net.kyori.adventure.text.Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            else lore = new ArrayList<>(lore);
            lore.add(Component.text("※ アイテムデータ保持中", NamedTextColor.GOLD)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true));
            meta.lore(lore);
        });

        // 移動セマンティクス: 防具側のデータを削除する。
        // 削除しないとデータが防具とスレッドの2か所に残り、別防具へ移植して複製できる。
        armorItem.editMeta(meta ->
            meta.getPersistentDataContainer().remove(BACKPACK_DATA_KEY));
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

        // 移動セマンティクス: スレッド側のデータを削除する（2か所に残ることによる複製を防止）。
        threadItem.editMeta(meta ->
            meta.getPersistentDataContainer().remove(BACKPACK_THREAD_DATA_KEY));
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
