package com.arspaper.gui;

import com.arspaper.item.ArmorManaListener;
import com.arspaper.item.ArmorTier;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 防具スレッドスロット管理GUI。
 * メイジアーマーのスレッドスロットにスレッドアイテムをセット/取り外しする。
 */
public class ThreadGui extends BaseGui {

    private static final Gson GSON = new Gson();
    private static final int ARMOR_INFO_SLOT = 1;
    private static final int THREAD_SLOT_START = 10; // 行1のスロット1,2,3
    private static final int CLOSE_SLOT = 26; // 行2の最終スロット
    private static final int MAX_THREAD_SLOTS = 3;

    private final ItemStack armorItem;
    private final ArmorTier armorTier;
    private final List<String> threadSlots; // null = 空スロット

    public ThreadGui(Player viewer, ItemStack armorItem) {
        super(viewer, 3, Component.text("スレッドスロット", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        this.armorItem = armorItem;

        // ティア取得
        int tierValue = armorItem.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.ARMOR_TIER, PersistentDataType.INTEGER, 1);
        this.armorTier = ArmorTier.fromTier(tierValue);

        // 既存スレッドデータ読み込み
        this.threadSlots = loadThreadSlots(armorItem);
    }

    @Override
    public void render() {
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        // 防具情報表示
        inventory.setItem(ARMOR_INFO_SLOT, createArmorInfoButton());

        // スレッドスロット表示
        int slotCount = armorTier.getThreadSlots();
        for (int i = 0; i < MAX_THREAD_SLOTS; i++) {
            int guiSlot = THREAD_SLOT_START + i;
            if (i < slotCount) {
                String threadId = (i < threadSlots.size()) ? threadSlots.get(i) : null;
                inventory.setItem(guiSlot, createThreadSlotButton(i, threadId));
            } else {
                // ロックされたスロット
                inventory.setItem(guiSlot, createButton(Material.BARRIER,
                    Component.text("ロック", NamedTextColor.DARK_GRAY),
                    List.of(Component.text("上位ティアの防具が必要", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false))));
            }
        }

        // 閉じるボタン
        inventory.setItem(CLOSE_SLOT, createButton(Material.DARK_OAK_DOOR,
            Component.text("閉じる", NamedTextColor.RED)));
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        if (slot == CLOSE_SLOT) {
            clicker.closeInventory();
            return true;
        }

        // スレッドスロットのクリック処理
        int slotCount = armorTier.getThreadSlots();
        for (int i = 0; i < slotCount; i++) {
            if (slot == THREAD_SLOT_START + i) {
                handleThreadSlotClick(clicker, i);
                return true;
            }
        }
        return true;
    }

    @Override
    public void onClose(Player player) {
        // GUI閉じ時にマナボーナスを再計算
        ArmorManaListener.recalculateArmorBonus(player);
    }

    private void handleThreadSlotClick(Player player, int slotIndex) {
        // スロットサイズを調整
        while (threadSlots.size() <= slotIndex) {
            threadSlots.add(null);
        }

        String currentThread = threadSlots.get(slotIndex);

        if (currentThread != null) {
            // スレッドを取り外し → インベントリに返却
            ThreadType threadType = ThreadType.fromId(currentThread);
            if (threadType != null && threadType.hasEffect()) {
                ItemStack threadItem = createThreadItemStack(threadType);
                if (player.getInventory().firstEmpty() == -1) {
                    // インベントリ満杯 → 地面にドロップ
                    player.getWorld().dropItemNaturally(player.getLocation(), threadItem);
                } else {
                    player.getInventory().addItem(threadItem);
                }
            }
            threadSlots.set(slotIndex, null);
            saveThreadSlots();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            render();
        } else {
            // 空スロット → インベントリからスレッドアイテムを検索してセット
            int threadItemSlot = findThreadItemInInventory(player);
            if (threadItemSlot == -1) {
                player.sendMessage(Component.text("スレッドアイテムがインベントリにありません！", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }

            ItemStack threadStack = player.getInventory().getItem(threadItemSlot);
            String threadTypeId = threadStack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING);
            ThreadType threadType = ThreadType.fromId(threadTypeId);

            if (threadType == null || !threadType.hasEffect()) {
                player.sendMessage(Component.text("効果付きスレッドをセットしてください！", NamedTextColor.RED));
                return;
            }

            // アイテム1個消費
            threadStack.setAmount(threadStack.getAmount() - 1);
            threadSlots.set(slotIndex, threadType.getId());
            saveThreadSlots();
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);
            render();
        }
    }

    /**
     * プレイヤーインベントリから効果付きスレッドアイテムを検索する。
     * ホットバー右側(スロット8)から左側(スロット0)、次にインベントリ(9-35)の順。
     */
    private int findThreadItemInInventory(Player player) {
        // ホットバー右側優先
        for (int i = 8; i >= 0; i--) {
            if (isEffectThread(player.getInventory().getItem(i))) return i;
        }
        // メインインベントリ
        for (int i = 9; i < 36; i++) {
            if (isEffectThread(player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private boolean isEffectThread(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String customId = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        if (customId == null || !customId.startsWith("thread_")) return false;

        String threadTypeId = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING);
        ThreadType type = ThreadType.fromId(threadTypeId);
        return type != null && type.hasEffect();
    }

    private ItemStack createArmorInfoButton() {
        List<Component> lore = List.of(
            Component.text("ティア: " + armorTier.getDisplayName(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("スレッドスロット: " + armorTier.getThreadSlots(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("スロットをクリックしてスレッドを", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("セット/取り外しできます", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        );
        return createButton(armorItem.getType(), armorItem.getItemMeta().displayName(), lore);
    }

    private ItemStack createThreadSlotButton(int index, String threadId) {
        if (threadId == null) {
            return createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Component.text("空きスロット " + (index + 1), NamedTextColor.GRAY),
                List.of(Component.text("クリックでスレッドをセット", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }

        ThreadType type = ThreadType.fromId(threadId);
        if (type == null) {
            return createButton(Material.BARRIER, Component.text("不明なスレッド", NamedTextColor.RED));
        }

        List<Component> lore = new ArrayList<>(type.getEffectLore());
        lore.add(Component.text("クリックで取り外し", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));

        return createButton(Material.STRING,
            Component.text(type.getDisplayName(), type.getColor()), lore);
    }

    private ItemStack createThreadItemStack(ThreadType type) {
        return com.arspaper.ArsPaper.getInstance().getItemRegistry()
            .get("thread_" + type.getId())
            .map(item -> item.createItemStack())
            .orElse(new ItemStack(Material.STRING));
    }

    // === PDCデータ管理 ===

    private List<String> loadThreadSlots(ItemStack armor) {
        if (!armor.hasItemMeta()) return new ArrayList<>();
        PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();

        // 新形式: THREAD_SLOTS (JSON配列)
        String json = pdc.get(ItemKeys.THREAD_SLOTS, PersistentDataType.STRING);
        if (json != null) {
            try {
                List<String> slots = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
                return slots != null ? new ArrayList<>(slots) : new ArrayList<>();
            } catch (Exception ignored) {}
        }

        // 旧形式: THREAD_TYPE (単一文字列) からのマイグレーション
        String oldThread = pdc.get(ItemKeys.THREAD_TYPE, PersistentDataType.STRING);
        List<String> migrated = new ArrayList<>();
        if (oldThread != null) {
            migrated.add(oldThread);
        }
        return migrated;
    }

    private void saveThreadSlots() {
        armorItem.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            // 新形式で保存
            pdc.set(ItemKeys.THREAD_SLOTS, PersistentDataType.STRING, GSON.toJson(threadSlots));

            // 旧形式を削除
            pdc.remove(ItemKeys.THREAD_TYPE);

            // loreを再構築
            meta.lore(buildArmorLore());
        });
    }

    private List<Component> buildArmorLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("ティア: " + armorTier.getDisplayName(), NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("マナボーナス: +" + armorTier.getManaBonus(), NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("スレッドスロット: " + armorTier.getThreadSlots(), NamedTextColor.DARK_AQUA)
            .decoration(TextDecoration.ITALIC, false));

        // スレッド情報
        for (int i = 0; i < threadSlots.size(); i++) {
            String threadId = threadSlots.get(i);
            if (threadId != null) {
                ThreadType type = ThreadType.fromId(threadId);
                if (type != null) {
                    lore.add(Component.text("  " + (i + 1) + ": " + type.getDisplayName(), type.getColor())
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
        }
        return lore;
    }
}
