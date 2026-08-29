package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadConfig;
import com.arspaper.item.ThreadType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * バックパックスレッドのインベントリGUI。
 * 防具PDCにバックパックデータを保存し、スレッド取り外し時にスレッドアイテムに転写する。
 *
 * <p>容量は threads.yml の {@code slots:}(1本あたり) × その防具に挿さっている本数を
 * {@code max-inventory-slots:} で頭打ちした値。54 を超えるときは最下段でページ送りする。
 * 全データは防具PDCのJSON配列（スロット番号 + Base64エンコード済み ItemStack）に保存。
 */
public class BackpackGui {

    private static final NamespacedKey BACKPACK_DATA_KEY = new NamespacedKey("arspaper", "backpack_data");
    private static final NamespacedKey BACKPACK_THREAD_DATA_KEY = new NamespacedKey("arspaper", "backpack_thread_data");
    static final NamespacedKey LOCKED_KEY = new NamespacedKey("arspaper", "backpack_locked");
    private static final Gson GSON = new Gson();

    static final int CHEST_MAX = 54;
    static final int PAGE_CONTENT = 45;
    static final int PREV_SLOT = 45;
    static final int INFO_SLOT = 49;
    static final int NEXT_SLOT = 53;

    /**
     * バックパックGUIを開く。
     * 防具PDCからデータを読み込み、閉じた時にPDCに書き戻す。
     */
    public static void open(Player player, ItemStack armorItem) {
        open(player, armorItem, 0);
    }

    public static void open(Player player, ItemStack armorItem, int page) {
        int backpackCount = countBackpackThreads(armorItem);
        if (backpackCount <= 0) {
            player.sendMessage(Component.text("バックパックスレッドが装着されていません", NamedTextColor.RED));
            return;
        }

        int capacity = capacityFor(armorItem, backpackCount);
        if (capacity <= 0) {
            player.sendMessage(Component.text("バックパックの容量が 0 です", NamedTextColor.RED));
            return;
        }

        boolean paginated = needsPagination(capacity);
        int pageCount = pageCount(capacity);
        int clamped = Math.max(0, Math.min(page, pageCount - 1));
        int size = chestSize(capacity);
        BackpackHolder holder = new BackpackHolder(armorItem, clamped, capacity, paginated);
        Inventory inv = Bukkit.createInventory(holder, size,
            Component.text("バックパック", NamedTextColor.DARK_GREEN));
        holder.setInventory(inv);

        loadPage(armorItem, inv, holder);
        decorateControls(inv, holder, pageCount);

        player.openInventory(inv);
    }

    /**
     * 装備中のバックパック付き防具。{@code getArmorContents()} は靴→レギンス→チェスト→ヘルメット。
     */
    public static List<ItemStack> wornBackpacks(Player player) {
        List<ItemStack> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && countBackpackThreads(armor) > 0) {
                out.add(armor);
            }
        }
        return out;
    }

    /**
     * 複数部位にバックパックがあるとき、どれを開くかを選ぶ。
     */
    public static void openSelector(Player player, List<ItemStack> pieces) {
        if (player == null || pieces == null || pieces.isEmpty()) {
            return;
        }
        if (pieces.size() == 1) {
            open(player, pieces.get(0));
            return;
        }
        BackpackSelectHolder holder = new BackpackSelectHolder(pieces);
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("バックパックを選ぶ", NamedTextColor.DARK_GREEN));
        holder.setInventory(inv);
        int n = Math.min(pieces.size(), 9);
        for (int i = 0; i < n; i++) {
            ItemStack preview = pieces.get(i).clone();
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.text("クリックで開く", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                preview.setItemMeta(meta);
            }
            inv.setItem(i, preview);
        }
        player.openInventory(inv);
    }

    static int capacityFor(ItemStack armorItem, int backpackCount) {
        int slotsPer = 27;
        int maxSlots = 54;
        ArsPaper plugin = ArsPaper.getInstance();
        if (plugin != null && plugin.getThreadConfig() != null) {
            ThreadConfig config = plugin.getThreadConfig();
            slotsPer = Math.max(1, config.getBackpackSlots(ThreadType.BACKPACK));
            maxSlots = Math.max(1, config.getBackpackMaxInventorySlots(ThreadType.BACKPACK));
        }
        return capacity(backpackCount, slotsPer, maxSlots);
    }

    /**
     * 1本あたり × 本数を {@code max-inventory-slots} で切る。Bukkit 非依存なのでテストから直接叩ける。
     * 上限は 1本あたり枠より小さくてよい（設定ミスで 27 に床上げしない）。
     */
    static int capacity(int backpackCount, int slotsPer, int maxSlots) {
        if (backpackCount <= 0 || slotsPer <= 0) {
            return 0;
        }
        int raw;
        try {
            raw = Math.multiplyExact(backpackCount, slotsPer);
        } catch (ArithmeticException ex) {
            raw = Integer.MAX_VALUE;
        }
        return Math.min(raw, Math.max(1, maxSlots));
    }

    static boolean needsPagination(int capacity) {
        return capacity > CHEST_MAX;
    }

    static int pageCount(int capacity) {
        if (!needsPagination(capacity)) {
            return 1;
        }
        return Math.max(1, (capacity + PAGE_CONTENT - 1) / PAGE_CONTENT);
    }

    static int chestSize(int capacity) {
        if (needsPagination(capacity)) {
            return CHEST_MAX;
        }
        int rounded = ((Math.max(1, capacity) + 8) / 9) * 9;
        return Math.min(CHEST_MAX, Math.max(9, rounded));
    }

    static int contentOnPage(int capacity, int page, boolean paginated) {
        if (!paginated) {
            return capacity;
        }
        int remaining = capacity - page * PAGE_CONTENT;
        return Math.max(0, Math.min(PAGE_CONTENT, remaining));
    }

    static int absoluteIndex(int page, int visibleSlot, boolean paginated) {
        return paginated ? page * PAGE_CONTENT + visibleSlot : visibleSlot;
    }

    /**
     * ページ送りボタンと余白のガラスはアイテムを置けない。
     * {@code rawSlot} は上段インベントリ基準。プレイヤーインベントリ(54+)は対象外。
     */
    public static boolean isLockedSlot(BackpackHolder holder, int rawSlot) {
        if (holder == null || rawSlot < 0) {
            return false;
        }
        int size = chestSize(holder.getCapacity());
        if (rawSlot >= size) {
            return false;
        }
        int content = contentOnPage(holder.getCapacity(), holder.getPage(), holder.isPaginated());
        if (holder.isPaginated()) {
            return rawSlot >= content;
        }
        return rawSlot >= content;
    }

    public static void handleLockedClick(BackpackHolder holder, Player player, int slot) {
        if (holder == null || player == null || !holder.isPaginated()) {
            return;
        }
        int pageCount = pageCount(holder.getCapacity());
        if (slot == PREV_SLOT && holder.getPage() > 0) {
            saveFromHolder(holder, player);
            holder.suppressCloseSave();
            open(player, holder.getArmorItem(), holder.getPage() - 1);
        } else if (slot == NEXT_SLOT && holder.getPage() < pageCount - 1) {
            saveFromHolder(holder, player);
            holder.suppressCloseSave();
            open(player, holder.getArmorItem(), holder.getPage() + 1);
        }
    }

    public static void saveFromHolder(BackpackHolder holder) {
        saveFromHolder(holder, null);
    }

    public static void saveFromHolder(BackpackHolder holder, Player recipient) {
        if (holder == null || holder.getArmorItem() == null || holder.getInventory() == null) {
            return;
        }
        saveBackpackContents(holder.getArmorItem(), holder.getInventory(),
                holder.getPage(), holder.getCapacity(), holder.isPaginated(), recipient);
    }

    /**
     * バックパックの内容を防具PDCに保存する（非ページング互換。閉じたインベントリ全体を 0 始まりで書く）。
     */
    public static void saveBackpackContents(ItemStack armorItem, Inventory backpackInv) {
        int size = backpackInv.getSize();
        saveBackpackContents(armorItem, backpackInv, 0, size, false, null);
    }

    static void saveBackpackContents(ItemStack armorItem, Inventory backpackInv,
                                     int page, int capacity, boolean paginated, Player recipient) {
        Map<Integer, String> stored = storedPayload(armorItem);
        int content = contentOnPage(capacity, page, paginated);
        for (int i = 0; i < content; i++) {
            int absolute = absoluteIndex(page, i, paginated);
            ItemStack item = backpackInv.getItem(i);
            if (item != null && !item.getType().isAir() && !isLockedItem(item)) {
                stored.put(absolute, encode(item));
            } else {
                stored.remove(absolute);
            }
        }
        reclaimItemsInLockedSlots(backpackInv, content, recipient);

        List<String> serialized = new ArrayList<>();
        stored.forEach((slot, payload) -> serialized.add(slot + ":" + payload));
        armorItem.editMeta(meta -> meta.getPersistentDataContainer().set(
                BACKPACK_DATA_KEY, PersistentDataType.STRING, GSON.toJson(serialized)));
    }

    /**
     * ガラス／ページ送り枠に紛れ込んだ実アイテムは PDC に書かない。持ち主へ返す。
     */
    static void reclaimItemsInLockedSlots(Inventory backpackInv, int content, Player recipient) {
        if (backpackInv == null) {
            return;
        }
        int size = backpackInv.getSize();
        for (int i = content; i < size; i++) {
            ItemStack item = backpackInv.getItem(i);
            if (item == null || item.getType().isAir() || isLockedItem(item)) {
                continue;
            }
            backpackInv.setItem(i, null);
            if (recipient != null) {
                giveOrDrop(recipient, item);
            }
        }
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        var leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(left ->
                player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /**
     * 防具PDCからバックパック内容を復元する（非ページング互換）。
     */
    public static void loadBackpackContents(ItemStack armorItem, Inventory inv) {
        loadPage(armorItem, inv, new BackpackHolder(armorItem, 0, inv.getSize(), false));
    }

    static void loadPage(ItemStack armorItem, Inventory inv, BackpackHolder holder) {
        Map<Integer, ItemStack> stored = storedItems(armorItem);
        int content = contentOnPage(holder.getCapacity(), holder.getPage(), holder.isPaginated());
        for (int i = 0; i < content; i++) {
            int absolute = absoluteIndex(holder.getPage(), i, holder.isPaginated());
            ItemStack item = stored.get(absolute);
            if (item != null) {
                inv.setItem(i, item);
            }
        }
    }

    private static void decorateControls(Inventory inv, BackpackHolder holder, int pageCount) {
        int content = contentOnPage(holder.getCapacity(), holder.getPage(), holder.isPaginated());
        int size = inv.getSize();
        for (int i = content; i < size; i++) {
            if (holder.isPaginated() && (i == PREV_SLOT || i == INFO_SLOT || i == NEXT_SLOT)) {
                continue;
            }
            inv.setItem(i, lockedPane(" "));
        }
        if (!holder.isPaginated()) {
            return;
        }
        if (holder.getPage() > 0) {
            inv.setItem(PREV_SLOT, navButton("前のページ", Material.ARROW));
        } else {
            inv.setItem(PREV_SLOT, lockedPane(" "));
        }
        inv.setItem(INFO_SLOT, navButton(
                "ページ " + (holder.getPage() + 1) + "/" + pageCount
                        + "  (" + holder.getCapacity() + " 枠)",
                Material.BOOK));
        if (holder.getPage() < pageCount - 1) {
            inv.setItem(NEXT_SLOT, navButton("次のページ", Material.SPECTRAL_ARROW));
        } else {
            inv.setItem(NEXT_SLOT, lockedPane(" "));
        }
    }

    private static ItemStack lockedPane(String name) {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(LOCKED_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack navButton(String label, Material icon) {
        ItemStack stack = new ItemStack(icon);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(LOCKED_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    static boolean isLockedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte locked = item.getItemMeta().getPersistentDataContainer()
                .get(LOCKED_KEY, PersistentDataType.BYTE);
        return locked != null && locked != 0;
    }

    private static Map<Integer, String> storedPayload(ItemStack armorItem) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (armorItem == null || !armorItem.hasItemMeta()) {
            return out;
        }
        String json = armorItem.getItemMeta().getPersistentDataContainer()
                .get(BACKPACK_DATA_KEY, PersistentDataType.STRING);
        if (json == null) {
            return out;
        }
        List<String> serialized;
        try {
            serialized = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
        } catch (Exception e) {
            ArsPaper plugin = ArsPaper.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("バックパックデータのJSON解析に失敗しました: " + e.getMessage());
            }
            return out;
        }
        if (serialized == null) {
            return out;
        }
        for (String entry : serialized) {
            int colonIdx = entry.indexOf(':');
            if (colonIdx < 0) {
                continue;
            }
            try {
                int slot = Integer.parseInt(entry.substring(0, colonIdx));
                out.put(slot, entry.substring(colonIdx + 1));
            } catch (NumberFormatException e) {
                ArsPaper plugin = ArsPaper.getInstance();
                if (plugin != null) {
                    plugin.getLogger().warning(
                            "バックパックのスロット番号解析に失敗しました (entry=" + entry + "): " + e.getMessage());
                }
            }
        }
        return out;
    }

    private static Map<Integer, ItemStack> storedItems(ItemStack armorItem) {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        storedPayload(armorItem).forEach((slot, payload) -> {
            try {
                byte[] data = java.util.Base64.getDecoder().decode(payload);
                out.put(slot, ItemStack.deserializeBytes(data));
            } catch (Exception e) {
                ArsPaper plugin = ArsPaper.getInstance();
                if (plugin != null) {
                    plugin.getLogger().warning(
                            "バックパックのスロット " + slot + " の復元に失敗したためスキップします: " + e.getMessage());
                }
            }
        });
        return out;
    }

    private static String encode(ItemStack item) {
        return java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes());
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
            List<Component> lore = meta.lore();
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
                                          List<Component> lore) {
        if (meta == null || lore == null) {
            return;
        }
        if (!meta.getPersistentDataContainer().has(BACKPACK_THREAD_DATA_KEY, PersistentDataType.STRING)) {
            return;
        }
        lore.add(Component.text("※ アイテムデータ保持中", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, true));
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
