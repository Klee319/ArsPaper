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
 * スレッドスロット管理GUI。
 * 防具システム(armors.yml)は撤去済みのため、スレッド枠容量はTrinityForgeのitem-stats
 * (canonicalキー thread_slots)から取得する。
 *
 * <p><b>2026-07-31 (F2)</b>: 対象は防具に限らない ── 武器・触媒・ツールも
 * {@code thread_slots} を持つので、{@code /ars thread} から同じGUIを開く。
 * 語彙を「防具」から中立(「対象」)へ直したのはそのため。手持ち装備では常時効果
 * (ポーション/飛行)が出ない・バックパックは装着できないという線引きを
 * {@link com.arspaper.item.ThreadApplicationPolicy} が持ち、ここはその表示と入口ゲートを担う。
 *
 * <p><b>2026-07-31 (F3 指摘5)</b>: 対象スロット番号を受け取り、装着/取り外しの直前に
 * <b>そのスロットの中身が今も同じ品か</b>を {@link com.arspaper.item.ThreadTargetIdentity} で
 * 再確認する。GUI 下段には対象そのものが描画されていてクリックできるため、対象を手から
 * 離したまま空き枠を押すと「在庫のスレッドが 1 個消えるのに装備には書き込まれない」
 * (=データ喪失)が成立していた。不一致なら<b>スレッドを消費せず</b>中断して閉じる。
 */
public class ThreadGui extends BaseGui {

    private static final Gson GSON = new Gson();
    private static final int INFO_SLOT = 1;
    private static final int THREAD_SLOT_START = 10;
    private static final int CLOSE_SLOT = 26;
    /** 対象スロットが分からない(呼び出し側が渡していない)ことを表す値。 */
    public static final int UNKNOWN_TARGET_SLOT = -1;

    /**
     * 対象装備。同一性確認が通るたびに<b>スロットのライブスタックへ差し替える</b> ──
     * Bukkit のスタックは移動や {@code editMeta} で別インスタンスに化けるため、
     * 開いたときの参照を握り続けると書き込みがスロットへ乗らないことがある。
     */
    private ItemStack targetItem;
    private final int threadSlotCount;
    private final String targetDisplayName;
    /** 対象が「着用スロットへ入る防具」か。常時効果の可否とバックパック装着可否を分ける。 */
    private final boolean targetIsArmor;
    /** 対象が入っているプレイヤーインベントリのスロット番号({@link #UNKNOWN_TARGET_SLOT} = 不明)。 */
    private final int targetSlot;
    /** 開いたときの対象の同一性。装着直前にスロットの中身と突き合わせる。 */
    private final com.arspaper.item.ThreadTargetIdentity targetIdentity;
    private final List<String> threadSlots;
    /**
     * threadSlots と<b>同じ添字</b>で対応する厳選結果の文字列（未厳選は空文字）。
     * 装着したスレッド個体の当たり外れを装備側で保持するために要る ── ID だけを持っていた
     * 従来形式では、厳選した個体を装着した瞬間に個体差が消えていた。
     */
    private final List<String> threadSlotRolls;

    /**
     * レガシーコンストラクタ（後方互換）。
     */
    public ThreadGui(Player viewer, ItemStack targetItem) {
        this(viewer, targetItem, null);
    }

    /**
     * 統合コンストラクタ。pluginパラメータは既存呼び出し箇所との互換のために残しているが未使用。
     *
     * <p>対象スロットを渡さない形。装着直前の同一性確認は<b>行われない</b>ので、
     * プレイヤー操作から開く経路では
     * {@link #ThreadGui(Player, ItemStack, JavaPlugin, int)} を使うこと。
     */
    public ThreadGui(Player viewer, ItemStack targetItem, JavaPlugin plugin) {
        this(viewer, targetItem, plugin, UNKNOWN_TARGET_SLOT);
    }

    /**
     * 対象スロット付きコンストラクタ。
     *
     * @param targetSlot 対象が入っているプレイヤーインベントリのスロット番号
     *                   (ホットバーなら 0-8)。装着/取り外しの直前にこのスロットの中身と
     *                   対象の同一性を突き合わせ、離れていたら中断する(F3 指摘5)。
     */
    public ThreadGui(Player viewer, ItemStack targetItem, JavaPlugin plugin, int targetSlot) {
        super(viewer, calculateGuiRows(targetItem, viewer), Component.text("スレッドスロット", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        this.targetItem = targetItem;
        this.targetSlot = targetSlot;
        this.targetIdentity = com.arspaper.item.ThreadTargetIdentity.of(targetItem);

        Map<String, Double> tfStats = resolveTargetItemStats(targetItem);
        // 枠数は装備自身のitem-stats(thread_slots)だけで決まる。拡張は「スレッド枠拡張の儀式」
        // ({@link com.arspaper.ritual.effect.ThreadSlotExpandRitualEffect})が装備のPDCを直接書き換えて
        // 行うため、ここで装着者のperk/ステータスを見る必要はない(2026-07-26: TFステータス経由で
        // 枠を増やす thread_slot_cap_bonus は儀式と機能が重複するため廃止)。
        this.threadSlotCount = TrinityForgeBridge.tfEffectiveThreadSlotCap(tfStats, viewer);
        this.targetDisplayName = resolveTargetDisplayName(targetItem);
        this.targetIsArmor = targetItem != null
                && ThreadApplicationPolicy.isArmorSlotMaterial(targetItem.getType());

        this.threadSlots = loadThreadSlots(targetItem);
        this.threadSlotRolls = loadThreadSlotRolls(targetItem, this.threadSlots.size());
    }

    /**
     * 対象アイテムの「実際の」CustomModelDataでTF item-statsを解決する。
     */
    private static Map<String, Double> resolveTargetItemStats(ItemStack targetItem) {
        return TrinityForgeBridge.resolveFullItemStats(targetItem);
    }

    /**
     * 対象の表示名をGUI情報表示用に取得する。表示名未設定時はMaterial名にフォールバックする。
     */
    private static String resolveTargetDisplayName(ItemStack targetItem) {
        if (targetItem != null && targetItem.hasItemMeta() && targetItem.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(targetItem.getItemMeta().displayName());
        }
        return targetItem != null ? targetItem.getType().name() : "不明";
    }

    @Override
    public void render() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        inventory.setItem(INFO_SLOT, createTargetInfoButton());

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

    /**
     * 対象がまだ同じスロットに居るかを確認し、居るなら参照をライブスタックへ更新する。
     *
     * <p>不一致(拾ってカーソルへ載せた／捨てた／別の品と入れ替えた)なら<b>何も消費せずに</b>
     * 中断して GUI を閉じる ── 消費だけ通って書き込みが乗らないと、プレイヤーは成功したと
     * 思ってスレッドを失う(F3 指摘5)。
     *
     * @return 続行してよいか
     */
    private boolean refreshTargetFromSlot(Player player) {
        if (targetSlot == UNKNOWN_TARGET_SLOT) {
            return true;
        }
        ItemStack live = player.getInventory().getItem(targetSlot);
        if (!targetIdentity.matches(com.arspaper.item.ThreadTargetIdentity.of(live))) {
            player.sendMessage(Component.text(
                "対象の装備が手から離れたため中断しました（スレッドは消費していません）。",
                NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            player.closeInventory();
            return false;
        }
        this.targetItem = live;
        return true;
    }

    /**
     * 対象が入っているプレイヤーインベントリのスロット番号
     * ({@link #UNKNOWN_TARGET_SLOT} = 不明)。{@code GuiListener} がこのスロットへのクリックを
     * 通さないために要る(同一性確認は最後の砦で、そもそも動かせない方が事故が少ない)。
     */
    public int getTargetSlot() {
        return targetSlot;
    }

    private void handleThreadSlotClick(Player player, int slotIndex, InventoryClickEvent event) {
        if (!refreshTargetFromSlot(player)) {
            return;
        }
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
                    BackpackGui.transferDataToThread(targetItem, threadItem);
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

            // バックパックは着用中の防具にしか装着させない。収納データは装着先アイテムのPDCへ入るが、
            // 取り出し口の /ars backpack は着用防具しか走査しないため、武器へ入れると中身へ
            // 二度と辿り着けなくなる(データ喪失)。入口で止めるのが唯一安全な形。
            if (!targetIsArmor && !ThreadApplicationPolicy.canSocketOutsideArmor(threadType)) {
                player.sendMessage(Component.text(
                    "バックパックのスレッドは防具にしか装着できません（収納の取り出しが"
                        + "着用中の防具からしか行えないため）", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
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
                BackpackGui.transferDataFromThread(threadStack, targetItem);
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

    private ItemStack createTargetInfoButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("対象: " + targetDisplayName, NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("スレッドスロット: " + threadSlotCount, NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("スロットをクリックしてスレッドを", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("セット/取り外しできます", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        if (!targetIsArmor) {
            // 手持ち装備で「効くもの/効かないもの」をここで先に見せる。
            // 装着してから気づく形にすると「枠だけあって効かない」という F2 と同じ体験になる。
            lore.add(Component.empty());
            lore.add(Component.text("手持ち装備(武器・触媒・ツール)です", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  効く: ステータス・セット効果", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  効かない: 常時ポーション効果・飛行", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  装着不可: バックパック", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        // displayName() はカスタム名未設定アイテムでは null。コンストラクタで解決済みの
        // Material名フォールバックを使い、バニラ装備でも情報ボタンを安全に生成する。
        return createButton(targetItem.getType(), Component.text(targetDisplayName), lore);
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
        if (!targetIsArmor && ThreadApplicationPolicy.isAmbientOnlyEffect(type)) {
            // 効果lore(「移動速度上昇 (装備中常時)」等)は防具前提の文言なので、手持ちでは嘘になる。
            lore.add(Component.text("※この装備では常時効果は発動しません", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        }
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
     * 対象装備のスレッドスロット数に応じてGUIの行数を決定する。
     * 5スロット以上は4行、それ以外は3行。
     * thread-slot-expansion加算後の実効枠数で判定する（コンストラクタのthreadSlotCountと整合）。
     */
    private static int calculateGuiRows(ItemStack targetItem, Player viewer) {
        Map<String, Double> tfStats = resolveTargetItemStats(targetItem);
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
        targetItem.editMeta(meta -> {
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
