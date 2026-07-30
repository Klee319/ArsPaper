package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.item.*;
import com.arspaper.item.impl.ThreadItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 防具スレッドスロット管理GUI。
 * 防具システム(armors.yml)は撤去済みのため、スレッド枠容量はTrinityForgeのitem-stats
 * (canonicalキー thread_slots)から取得する。
 */
public class ThreadGui extends BaseGui {

    private static final Gson GSON = new Gson();
    private static final int ARMOR_INFO_SLOT = 1;
    private static final int THREAD_SLOT_START = 10;
    private static final int CLOSE_SLOT = 26;

    private final ItemStack armorItem;
    private final int threadSlotCount;
    private final String armorDisplayName;
    private final List<String> threadSlots;
    /**
     * threadSlots と<b>同じ添字</b>で対応する厳選結果の文字列（未厳選は空文字）。
     * 装着したスレッド個体の当たり外れを防具側で保持するために要る ── ID だけを持っていた
     * 従来形式では、厳選した個体を装着した瞬間に個体差が消えていた。
     */
    private final List<String> threadSlotRolls;

    /**
     * レガシーコンストラクタ（後方互換）。
     */
    public ThreadGui(Player viewer, ItemStack armorItem) {
        this(viewer, armorItem, null);
    }

    /**
     * 統合コンストラクタ。pluginパラメータは既存呼び出し箇所との互換のために残しているが未使用。
     */
    public ThreadGui(Player viewer, ItemStack armorItem, JavaPlugin plugin) {
        super(viewer, calculateGuiRows(armorItem, viewer), Component.text("スレッドスロット", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        this.armorItem = armorItem;

        Map<String, Double> tfStats = resolveArmorItemStats(armorItem);
        // 枠数は装備自身のitem-stats(thread_slots)だけで決まる。拡張は「スレッド枠拡張の儀式」
        // ({@link com.arspaper.ritual.effect.ThreadSlotExpandRitualEffect})が装備のPDCを直接書き換えて
        // 行うため、ここで装着者のperk/ステータスを見る必要はない(2026-07-26: TFステータス経由で
        // 枠を増やす thread_slot_cap_bonus は儀式と機能が重複するため廃止)。
        this.threadSlotCount = TrinityForgeBridge.tfEffectiveThreadSlotCap(tfStats, viewer);
        this.armorDisplayName = resolveArmorDisplayName(armorItem);

        this.threadSlots = loadThreadSlots(armorItem);
        this.threadSlotRolls = loadThreadSlotRolls(armorItem, this.threadSlots.size());
    }

    /**
     * 防具アイテムの「実際の」CustomModelDataでTF item-statsを解決する。
     */
    private static Map<String, Double> resolveArmorItemStats(ItemStack armorItem) {
        return TrinityForgeBridge.resolveFullItemStats(armorItem);
    }

    /**
     * 防具の表示名をGUI情報表示用に取得する。表示名未設定時はMaterial名にフォールバックする。
     */
    private static String resolveArmorDisplayName(ItemStack armorItem) {
        if (armorItem != null && armorItem.hasItemMeta() && armorItem.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(armorItem.getItemMeta().displayName());
        }
        return armorItem != null ? armorItem.getType().name() : "不明";
    }

    @Override
    public void render() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        inventory.setItem(ARMOR_INFO_SLOT, createArmorInfoButton());

        for (int i = 0; i < threadSlotCount; i++) {
            int guiSlot = THREAD_SLOT_START + i;
            if (guiSlot >= inventory.getSize()) break; // GUI範囲外防止
            String threadId = (i < threadSlots.size()) ? threadSlots.get(i) : null;
            inventory.setItem(guiSlot, createThreadSlotButton(i, threadId, rollAt(i)));
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
                // 取り外しでは【装着時の厳選値をそのまま返す】。createThreadItemStack は新品を作るので
                // 中で改めて抽選されてしまう ── 上書きしないと「外して付け直すだけで厳選し直せる」
                // 無限リロールになる。
                ItemStack threadItem = createThreadItemStack(threadType);
                restoreRoll(threadItem, rollAt(slotIndex));
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
            setRollAt(slotIndex, "");
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

            // 厳選値は【消費前の】スタックから読む(消費でスタックが空になると読めなくなる)。
            String socketedRoll = ThreadRoll.rawOf(threadStack);

            // アイテム消費
            if (fromCursor) {
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
            } else {
                threadStack.setAmount(threadStack.getAmount() - 1);
            }

            threadSlots.set(slotIndex, threadType.getId());
            setRollAt(slotIndex, socketedRoll);
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
        // displayName() はカスタム名未設定アイテムでは null。コンストラクタで解決済みの
        // Material名フォールバックを使い、バニラ防具でも情報ボタンを安全に生成する。
        return createButton(armorItem.getType(), Component.text(armorDisplayName), lore);
    }

    private ItemStack createThreadSlotButton(int index, String threadId, String encodedRoll) {
        if (threadId == null) {
            return createButton(Material.LIME_STAINED_GLASS_PANE,
                Component.text("空きスロット " + (index + 1), NamedTextColor.GREEN),
                List.of(Component.text("クリックでスレッドをセット", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }

        ThreadType type = ThreadType.fromId(threadId);
        if (type == null) {
            return createButton(Material.BARRIER, Component.text("不明なスレッド", NamedTextColor.RED));
        }

        List<Component> lore = new ArrayList<>(ArsPaper.getInstance().getThreadConfig().getEffectLore(type));
        ThreadRoll.decode(encodedRoll).ifPresent(roll -> lore.addAll(ThreadItem.rollLore(roll)));
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
     * thread-slot-expansion加算後の実効枠数で判定する（コンストラクタのthreadSlotCountと整合）。
     */
    private static int calculateGuiRows(ItemStack armorItem, Player viewer) {
        Map<String, Double> tfStats = resolveArmorItemStats(armorItem);
        int threadSlots = TrinityForgeBridge.tfEffectiveThreadSlotCap(tfStats, viewer);
        return threadSlots > 4 ? 4 : 3;
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

    /** 厳選結果を添字で読む（範囲外/未設定は空文字）。 */
    private String rollAt(int index) {
        return (index >= 0 && index < threadSlotRolls.size() && threadSlotRolls.get(index) != null)
                ? threadSlotRolls.get(index) : "";
    }

    private void setRollAt(int index, String encoded) {
        while (threadSlotRolls.size() <= index) {
            threadSlotRolls.add("");
        }
        threadSlotRolls.set(index, encoded == null ? "" : encoded);
    }

    /**
     * 返却するスレッドへ、装着時の厳選値と lore を書き戻す。
     * {@code createThreadItemStack} が新品として付けた厳選行を先に取り除いてから入れ直す
     * （そうしないと lore に2個体ぶんの数値が並ぶ）。
     */
    private static void restoreRoll(ItemStack threadItem, String encodedRoll) {
        ThreadRoll saved = ThreadRoll.decode(encodedRoll).orElse(null);
        if (saved == null) {
            return;
        }
        List<Component> freshLore = ThreadRoll.decode(ThreadRoll.rawOf(threadItem))
                .map(ThreadItem::rollLore).orElse(List.of());
        threadItem.editMeta(meta -> {
            ThreadRoll.write(meta.getPersistentDataContainer(), saved);
            List<Component> current = meta.lore() == null ? List.<Component>of() : meta.lore();
            List<Component> rebuilt = new ArrayList<>();
            for (Component line : current) {
                if (!freshLore.contains(line)) {
                    rebuilt.add(line);
                }
            }
            rebuilt.addAll(ThreadItem.rollLore(saved));
            meta.lore(rebuilt);
        });
    }

    private static List<String> loadThreadSlotRolls(ItemStack armor, int slotCount) {
        List<String> rolls = new ArrayList<>();
        if (armor != null && armor.hasItemMeta()) {
            String json = armor.getItemMeta().getPersistentDataContainer()
                    .get(ItemKeys.THREAD_SLOT_ROLLS, PersistentDataType.STRING);
            if (json != null) {
                try {
                    List<String> parsed = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
                    if (parsed != null) {
                        rolls.addAll(parsed);
                    }
                } catch (Exception ignored) {
                    // 壊れていれば「厳選なし」として扱う。装着済みスレッド自体は THREAD_SLOTS 側に残る。
                }
            }
        }
        while (rolls.size() < slotCount) {
            rolls.add("");
        }
        return rolls;
    }

    private void saveThreadSlots() {
        armorItem.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            List<Component> previousOwned = loadOwnedThreadLore(pdc);
            List<Component> nextOwned = buildThreadLore();
            meta.lore(ThreadLoreMerge.merge(meta.lore(), previousOwned, nextOwned));
            pdc.set(ItemKeys.THREAD_SLOTS, PersistentDataType.STRING, GSON.toJson(threadSlots));
            if (threadSlotRolls.stream().anyMatch(entry -> entry != null && !entry.isBlank())) {
                pdc.set(ItemKeys.THREAD_SLOT_ROLLS, PersistentDataType.STRING, GSON.toJson(threadSlotRolls));
            } else {
                // 全部空なら書かない ＝ 厳選導入前の防具とまったく同じ PDC 形状に戻す。
                pdc.remove(ItemKeys.THREAD_SLOT_ROLLS);
            }
            pdc.set(ItemKeys.THREAD_LORE, PersistentDataType.STRING, serializeThreadLore(nextOwned));
            pdc.remove(ItemKeys.THREAD_TYPE);
            // エンチャントオーラを明示的に保持（editMetaでオーラが消失する問題の対策）
            if (meta.hasEnchants()) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
    }

    private List<Component> buildThreadLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("スレッドスロット: " + threadSlotCount, NamedTextColor.DARK_AQUA)
            .decoration(TextDecoration.ITALIC, false));

        // スレッド情報
        for (int i = 0; i < threadSlots.size(); i++) {
            String threadId = threadSlots.get(i);
            if (threadId != null) {
                ThreadType type = ThreadType.fromId(threadId);
                if (type != null) {
                    lore.add(Component.text("  " + (i + 1) + ": " + type.getDisplayName(), type.getColor())
                        .decoration(TextDecoration.ITALIC, false));
                    int slotIndex = i;
                    ThreadRoll.decode(rollAt(slotIndex))
                        .ifPresent(roll -> lore.addAll(ThreadItem.rollLore(roll)));
                }
            }
        }
        return lore;
    }

    private static List<Component> loadOwnedThreadLore(PersistentDataContainer pdc) {
        String json = pdc.get(ItemKeys.THREAD_LORE, PersistentDataType.STRING);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> serialized = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
            if (serialized == null) {
                return List.of();
            }
            return serialized.stream()
                .map(GsonComponentSerializer.gson()::deserialize)
                .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static String serializeThreadLore(List<Component> lore) {
        return GSON.toJson(lore.stream()
            .map(GsonComponentSerializer.gson()::serialize)
            .toList());
    }
}
