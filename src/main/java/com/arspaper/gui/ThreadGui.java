package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.item.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 防具スレッドスロット管理GUI。
 * メイジアーマー（レガシー）および設定ベース防具のスレッドスロットを管理する。
 */
public class ThreadGui extends BaseGui {

    private static final Gson GSON = new Gson();
    private static final int ARMOR_INFO_SLOT = 1;
    private static final int THREAD_SLOT_START = 10;
    private static final int CLOSE_SLOT = 26;

    private final ItemStack armorItem;
    private final int threadSlotCount;
    private final String armorDisplayName;
    private final int manaBonus;
    private final List<String> loreTemplate;
    private final List<String> threadSlots;

    /**
     * レガシーコンストラクタ（MageArmor用、後方互換）。
     */
    public ThreadGui(Player viewer, ItemStack armorItem) {
        this(viewer, armorItem, null);
    }

    /**
     * 統合コンストラクタ。pluginがnullの場合はレガシーモードで動作。
     */
    public ThreadGui(Player viewer, ItemStack armorItem, JavaPlugin plugin) {
        super(viewer, calculateGuiRows(armorItem), Component.text("スレッドスロット", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        this.armorItem = armorItem;

        PersistentDataContainer pdc = armorItem.getItemMeta().getPersistentDataContainer();

        // 設定ベース防具を優先チェック
        String armorSetId = pdc.get(ItemKeys.ARMOR_SET_ID, PersistentDataType.STRING);
        if (armorSetId != null) {
            ArmorSetConfig config = ArsPaper.getInstance().getArmorConfigManager().getSetById(armorSetId);
            if (config != null) {
                this.threadSlotCount = config.getThreadSlots();
                this.armorDisplayName = config.getDisplayNamePrefix();
                this.manaBonus = config.getManaBonus();
                this.loreTemplate = config.getLoreLines();
            } else {
                this.threadSlotCount = 1;
                this.armorDisplayName = "不明";
                this.manaBonus = 0;
                this.loreTemplate = List.of();
            }
        } else {
            // レガシーフォールバック: ArmorTier enum
            int tierValue = pdc.getOrDefault(ItemKeys.ARMOR_TIER, PersistentDataType.INTEGER, 1);
            ArmorTier tier = ArmorTier.fromTier(tierValue);
            this.threadSlotCount = tier.getThreadSlots();
            this.armorDisplayName = tier.getDisplayName();
            this.manaBonus = tier.getManaBonus();
            this.loreTemplate = List.of();
        }

        this.threadSlots = loadThreadSlots(armorItem);
    }

    @Override
    public void render() {
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        inventory.setItem(ARMOR_INFO_SLOT, createArmorInfoButton());

        for (int i = 0; i < threadSlotCount; i++) {
            int guiSlot = THREAD_SLOT_START + i;
            if (guiSlot >= inventory.getSize()) break; // GUI範囲外防止
            String threadId = (i < threadSlots.size()) ? threadSlots.get(i) : null;
            inventory.setItem(guiSlot, createThreadSlotButton(i, threadId));
        }

        // 閉じるボタン: 最終行の右端
        int closeSlot = inventory.getSize() - 1;
        inventory.setItem(closeSlot, createButton(Material.DARK_OAK_DOOR,
            Component.text("閉じる", NamedTextColor.RED)));
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        int closeSlot = inventory.getSize() - 1;
        if (slot == closeSlot) {
            clicker.closeInventory();
            return true;
        }

        for (int i = 0; i < threadSlotCount; i++) {
            if (slot == THREAD_SLOT_START + i) {
                handleThreadSlotClick(clicker, i, event);
                return true;
            }
        }

        // プレイヤーインベントリ側のスレッドアイテムをクリック → カーソルに載せる扱い
        // GUIのBaseGuiがキャンセルするため、ここでは何もしない
        return true;
    }

    @Override
    public void onClose(Player player) {
        ArmorManaListener.recalculateArmorBonus(player);
    }

    private void handleThreadSlotClick(Player player, int slotIndex, InventoryClickEvent event) {
        while (threadSlots.size() <= slotIndex) {
            threadSlots.add(null);
        }

        String currentThread = threadSlots.get(slotIndex);

        if (currentThread != null) {
            // スロットにスレッドがある → 取り外し
            ThreadType threadType = ThreadType.fromId(currentThread);
            if (threadType != null && threadType.hasEffect()) {
                ItemStack threadItem = createThreadItemStack(threadType);
                if (threadType.isBackpackThread()) {
                    BackpackGui.transferDataToThread(armorItem, threadItem);
                }
                if (player.getInventory().firstEmpty() == -1) {
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
            // スロットが空 → スレッドを挿入
            // 1. カーソル上のアイテムを優先
            // 2. カーソルが空ならインベントリから自動検索
            ItemStack threadStack = null;
            int threadItemSlot = -1;
            boolean fromCursor = false;

            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && isEffectThread(cursor)) {
                threadStack = cursor;
                fromCursor = true;
            } else {
                threadItemSlot = findThreadItemInInventory(player);
                if (threadItemSlot != -1) {
                    threadStack = player.getInventory().getItem(threadItemSlot);
                }
            }

            if (threadStack == null) {
                player.sendMessage(Component.text("スレッドアイテムがインベントリにありません！", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }

            String threadTypeId = threadStack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING);
            ThreadType threadType = ThreadType.fromId(threadTypeId);

            if (threadType == null || !threadType.hasEffect()) {
                player.sendMessage(Component.text("効果付きスレッドをセットしてください！", NamedTextColor.RED));
                return;
            }

            // 重複チェック + 最大積載量チェック
            ThreadConfig threadCfg = ArsPaper.getInstance().getThreadConfig();
            if (!threadCfg.isStackable(threadType.getId())) {
                boolean alreadyExists = threadSlots.stream()
                    .anyMatch(id -> threadType.getId().equals(id));
                if (alreadyExists) {
                    player.sendMessage(Component.text("このスレッドは重複セットできません！", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
            } else {
                // スタック可能でもmax上限チェック
                int maxCount = threadCfg.getMaxStack(threadType.getId());
                long currentCount = threadSlots.stream()
                    .filter(id -> threadType.getId().equals(id))
                    .count();
                if (currentCount >= maxCount) {
                    player.sendMessage(Component.text("このスレッドの最大積載量に達しています！(最大" + maxCount + "個)", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
            }

            if (threadType.isBackpackThread()) {
                BackpackGui.transferDataFromThread(threadStack, armorItem);
            }

            // アイテム消費
            if (fromCursor) {
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
            } else {
                threadStack.setAmount(threadStack.getAmount() - 1);
            }

            threadSlots.set(slotIndex, threadType.getId());
            saveThreadSlots();
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);
            render();
        }
    }

    private int findThreadItemInInventory(Player player) {
        for (int i = 8; i >= 0; i--) {
            if (isEffectThread(player.getInventory().getItem(i))) return i;
        }
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
            Component.text("セット: " + armorDisplayName, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("スレッドスロット: " + threadSlotCount, NamedTextColor.AQUA)
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

        List<Component> lore = new ArrayList<>(ArsPaper.getInstance().getThreadConfig().getEffectLore(type));
        lore.add(Component.text("クリックで取り外し", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));

        return createButton(type.getBaseMaterial(),
            Component.text(type.getDisplayName(), type.getColor()), lore);
    }

    private ItemStack createThreadItemStack(ThreadType type) {
        return ArsPaper.getInstance().getItemRegistry()
            .get("thread_" + type.getId())
            .map(item -> item.createItemStack())
            .orElse(new ItemStack(type.getBaseMaterial()));
    }

    /**
     * 防具のスレッドスロット数に応じてGUIの行数を決定する。
     * 5スロット以上は4行、それ以外は3行。
     */
    private static int calculateGuiRows(ItemStack armorItem) {
        if (armorItem == null || !armorItem.hasItemMeta()) return 3;
        PersistentDataContainer pdc = armorItem.getItemMeta().getPersistentDataContainer();
        String armorSetId = pdc.get(ItemKeys.ARMOR_SET_ID, PersistentDataType.STRING);
        if (armorSetId != null) {
            ArmorSetConfig config = ArsPaper.getInstance().getArmorConfigManager().getSetById(armorSetId);
            if (config != null && config.getThreadSlots() > 4) return 4;
        }
        return 3;
    }

    // === PDCデータ管理 ===

    private List<String> loadThreadSlots(ItemStack armor) {
        if (!armor.hasItemMeta()) return new ArrayList<>();
        PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();

        String json = pdc.get(ItemKeys.THREAD_SLOTS, PersistentDataType.STRING);
        if (json != null) {
            try {
                List<String> slots = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
                return slots != null ? new ArrayList<>(slots) : new ArrayList<>();
            } catch (Exception ignored) {}
        }

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
            pdc.set(ItemKeys.THREAD_SLOTS, PersistentDataType.STRING, GSON.toJson(threadSlots));
            pdc.remove(ItemKeys.THREAD_TYPE);
            meta.lore(buildArmorLore());
        });
    }

    private List<Component> buildArmorLore() {
        List<Component> lore = new ArrayList<>();

        // 設定ベース防具: YAMLのloreを使用
        if (!loreTemplate.isEmpty()) {
            for (String line : loreTemplate) {
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            // レガシー: ハードコードlore
            lore.add(Component.text("セット: " + armorDisplayName, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("マナボーナス: +" + manaBonus, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("スレッドスロット: " + threadSlotCount, NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        }

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
