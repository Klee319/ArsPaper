package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * スペル組み立てGUI。
 * 上段: 現在の構成表示（固定8スロット、nullで空き）
 * 中段: 利用可能なForm/Effect/Augmentをタブ切り替え
 * 下段: 操作ボタン（保存・クリア・タブ切替）
 *
 * グリフ除去時はスロットを空けたまま保持し、
 * 新規追加時は最初の空きスロットに配置する。
 *
 * Geyser互換: 全てクリックイベントベースで操作。
 */
public class SpellCraftingGui extends BaseGui {

    private static final int MAX_GLYPHS = 8;
    private static final int COMPOSITION_START = 1;
    private static final int COMPOSITION_END = 8;
    private static final int GLYPH_START = 19;
    private static final int GLYPH_END = 43;

    // ページ送りボタン位置（セパレータ行）
    private static final int BTN_PREV_PAGE = 9;
    // 下段ボタン位置
    private static final int BTN_FORMS = 46;
    private static final int BTN_EFFECTS = 47;
    private static final int BTN_AUGMENTS = 48;
    private static final int BTN_CLEAR = 50;
    private static final int BTN_UNDO = 51;
    private static final int BTN_SAVE = 52;
    private static final int BTN_NEXT_PAGE = 17;  // セパレータ行右端

    private final ArsPaper plugin;
    private final int spellSlot;
    private final ItemStack spellBookItem;
    private final int maxGlyphTier;

    /** 固定8スロットの構成配列。nullは空きスロットを表す。 */
    private final SpellComponent[] composition = new SpellComponent[MAX_GLYPHS];
    private SpellComponent.ComponentType currentTab = SpellComponent.ComponentType.FORM;
    private int currentPage = 0;
    private int glyphsPerPage = 0;
    private final Set<String> unlockedGlyphs;

    public SpellCraftingGui(ArsPaper plugin, Player player, ItemStack spellBookItem, int spellSlot, int maxGlyphTier) {
        super(player, 6, Component.text("スペル作成 - スロット" + (spellSlot + 1), NamedTextColor.DARK_PURPLE));
        this.plugin = plugin;
        this.spellSlot = spellSlot;
        this.spellBookItem = spellBookItem;
        this.maxGlyphTier = maxGlyphTier;
        this.unlockedGlyphs = loadUnlockedGlyphs(player);

        loadExistingSpell();
    }

    private void loadExistingSpell() {
        if (!spellBookItem.hasItemMeta()) return;
        String slotsJson = spellBookItem.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) return;

        List<SpellRecipe> slots = SpellSerializer.deserializeSlots(slotsJson, plugin.getSpellRegistry());
        if (spellSlot < slots.size() && slots.get(spellSlot) != null) {
            List<SpellComponent> comps = slots.get(spellSlot).getComponents();
            for (int i = 0; i < comps.size() && i < MAX_GLYPHS; i++) {
                composition[i] = comps.get(i);
            }
        }
    }

    /** 構成内の非nullグリフ数を返す。 */
    private int compositionCount() {
        int count = 0;
        for (SpellComponent c : composition) {
            if (c != null) count++;
        }
        return count;
    }

    /** 構成が空（全スロットnull）かどうか。 */
    private boolean isCompositionEmpty() {
        for (SpellComponent c : composition) {
            if (c != null) return false;
        }
        return true;
    }

    /** 構成内のnullを除いたリストを返す（順序保持）。 */
    private List<SpellComponent> compactComposition() {
        List<SpellComponent> result = new ArrayList<>();
        for (SpellComponent c : composition) {
            if (c != null) result.add(c);
        }
        return result;
    }

    /** 最初の空きスロットのインデックスを返す。満杯なら-1。 */
    private int firstEmptySlot() {
        for (int i = 0; i < MAX_GLYPHS; i++) {
            if (composition[i] == null) return i;
        }
        return -1;
    }

    /** 最後の非nullスロットのインデックスを返す。空なら-1。 */
    private int lastOccupiedSlot() {
        for (int i = MAX_GLYPHS - 1; i >= 0; i--) {
            if (composition[i] != null) return i;
        }
        return -1;
    }

    @Override
    public void render() {
        inventory.clear();

        fillRow(0, Material.BLACK_STAINED_GLASS_PANE);
        renderComposition();

        fillRow(1, Material.GRAY_STAINED_GLASS_PANE);
        renderGlyphPalette();

        fillRow(5, Material.BLACK_STAINED_GLASS_PANE);
        renderControls();
    }

    private void renderComposition() {
        for (int i = 0; i < MAX_GLYPHS; i++) {
            int slot = COMPOSITION_START + i;
            SpellComponent comp = composition[i];
            if (comp != null) {
                NamedTextColor color = getTypeColor(comp.getType());
                inventory.setItem(slot, createButton(
                    getTypeMaterial(comp.getType()),
                    Component.text(comp.getDisplayName(), color),
                    List.of(
                        Component.text("種類: " + localizeType(comp.getType()), NamedTextColor.GRAY),
                        Component.text("マナ: " + comp.getManaCost(), NamedTextColor.AQUA),
                        Component.text("クリックで除去", NamedTextColor.RED)
                    )
                ));
            } else {
                inventory.setItem(slot, createButton(
                    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    Component.text("空", NamedTextColor.DARK_GRAY)
                ));
            }
        }
    }

    private void renderGlyphPalette() {
        List<SpellComponent> available = plugin.getSpellRegistry().getByType(currentTab);

        List<Integer> paletteSlots = new ArrayList<>();
        for (int s = GLYPH_START; s <= GLYPH_END; s++) {
            if (s % 9 != 0 && s % 9 != 8) {
                paletteSlots.add(s);
            }
        }
        glyphsPerPage = paletteSlots.size();

        int totalPages = Math.max(1, (int) Math.ceil((double) available.size() / glyphsPerPage));
        currentPage = Math.min(currentPage, totalPages - 1);

        int startIndex = currentPage * glyphsPerPage;
        int paletteIdx = 0;

        for (int i = startIndex; i < available.size() && paletteIdx < paletteSlots.size(); i++) {
            SpellComponent comp = available.get(i);
            int slot = paletteSlots.get(paletteIdx);
            paletteIdx++;

            boolean isUnlocked = unlockedGlyphs.contains(comp.getId().toString());
            String disableReason = isUnlocked ? getDisableReason(comp) : null;
            boolean usable = isUnlocked && disableReason == null;

            NamedTextColor nameColor = usable ? getTypeColor(comp.getType())
                : isUnlocked ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY;
            Material mat = !isUnlocked ? Material.BARRIER
                : !usable ? Material.GRAY_DYE
                : getTypeMaterial(comp.getType());

            List<Component> lore = new ArrayList<>();
            if (!comp.getDescription().isEmpty()) {
                lore.add(Component.text(comp.getDescription(), NamedTextColor.GRAY));
            }
            lore.add(Component.text("マナ: " + comp.getManaCost(), NamedTextColor.AQUA));
            if (!isUnlocked) {
                lore.add(Component.text("未解放 - 筆記台で解放してください", NamedTextColor.RED));
            } else if (disableReason != null) {
                lore.add(Component.text(disableReason, NamedTextColor.RED));
            } else {
                lore.add(Component.text("クリックで追加", NamedTextColor.GREEN));
            }

            inventory.setItem(slot, createButton(mat,
                Component.text(comp.getDisplayName(), nameColor), lore));
        }

        if (totalPages > 1) {
            inventory.setItem(13, createButton(Material.PAPER,
                Component.text("ページ " + (currentPage + 1) + " / " + totalPages, NamedTextColor.WHITE)));
            if (currentPage > 0) {
                inventory.setItem(BTN_PREV_PAGE, createButton(Material.ARROW,
                    Component.text("← 前のページ", NamedTextColor.GOLD)));
            }
            if (currentPage < totalPages - 1) {
                inventory.setItem(BTN_NEXT_PAGE, createButton(Material.ARROW,
                    Component.text("次のページ →", NamedTextColor.GOLD)));
            }
        }
    }

    /**
     * 現在の構成に対してグリフが追加不可能な理由を返す。
     * 追加可能ならnullを返す。
     */
    private String getDisableReason(SpellComponent comp) {
        if (firstEmptySlot() < 0) {
            return "スペルが満杯です";
        }

        if (comp.getTier() > maxGlyphTier) {
            return "ティア" + comp.getTier() + " - このブックでは使用不可";
        }

        List<SpellComponent> compacted = compactComposition();

        // Form: 先頭以外で追加不可 / 既にFormがある
        if (comp.getType() == SpellComponent.ComponentType.FORM) {
            boolean hasForm = compacted.stream()
                .anyMatch(c -> c.getType() == SpellComponent.ComponentType.FORM);
            if (hasForm) {
                return "形態は1つまでです";
            }
            // Formは先頭スロット(index 0)が空いている必要がある
            if (composition[0] != null) {
                return "先頭スロットが使用中です";
            }
            return null;
        }

        // Effect/Augment: Formが必要
        boolean hasForm = compacted.stream()
            .anyMatch(c -> c.getType() == SpellComponent.ComponentType.FORM);
        if (!hasForm) {
            return "最初に形態(Form)を選択してください";
        }

        // Effect: 重複チェック + Form互換性チェック
        if (comp.getType() == SpellComponent.ComponentType.EFFECT) {
            boolean duplicate = compacted.stream()
                .filter(c -> c.getType() == SpellComponent.ComponentType.EFFECT)
                .anyMatch(c -> c.getId().equals(comp.getId()));
            if (duplicate) {
                return "同じ効果は重複できません";
            }

            // Form-Effect互換性チェック（glyphs.ymlのeffectsリスト）
            SpellComponent form = compacted.stream()
                .filter(c -> c.getType() == SpellComponent.ComponentType.FORM)
                .findFirst().orElse(null);
            if (form != null) {
                String formKey = form.getId().getKey();
                String effectKey = comp.getId().getKey();
                if (!plugin.getGlyphConfig().isEffectCompatibleWithForm(formKey, effectKey)) {
                    return "この形態では使用できません";
                }
            }
        }

        // Augment: 互換性 + 上限チェック
        if (comp.getType() == SpellComponent.ComponentType.AUGMENT) {
            if (!isAugmentCompatibleWithContext(comp)) {
                // 上限到達かどうかで表示メッセージを分ける
                String augKey = comp.getId().getKey();
                SpellComponent lastTarget = findLastTargetForAugment();
                if (lastTarget != null) {
                    String tKey = lastTarget.getId().getKey();
                    if (plugin.getGlyphConfig().isAugmentCompatible(tKey, augKey)) {
                        int max = plugin.getGlyphConfig().getMaxAugmentStack(tKey, augKey);
                        if (max < Integer.MAX_VALUE) {
                            return "上限に達しています (最大" + max + ")";
                        }
                    }
                }
                return "直前の効果に対応していません";
            }
        }

        return null;
    }

    private void renderControls() {
        inventory.setItem(BTN_FORMS, createButton(
            currentTab == SpellComponent.ComponentType.FORM ? Material.DIAMOND_BLOCK : Material.DIAMOND,
            Component.text("形態", NamedTextColor.AQUA)
        ));
        inventory.setItem(BTN_EFFECTS, createButton(
            currentTab == SpellComponent.ComponentType.EFFECT ? Material.EMERALD_BLOCK : Material.EMERALD,
            Component.text("効果", NamedTextColor.GREEN)
        ));
        inventory.setItem(BTN_AUGMENTS, createButton(
            currentTab == SpellComponent.ComponentType.AUGMENT ? Material.AMETHYST_BLOCK : Material.AMETHYST_SHARD,
            Component.text("増強", NamedTextColor.LIGHT_PURPLE)
        ));

        int totalCost = compactComposition().stream().mapToInt(SpellComponent::getManaCost).sum();
        inventory.setItem(BTN_CLEAR, createButton(
            Material.TNT, Component.text("全消去", NamedTextColor.RED)
        ));
        inventory.setItem(BTN_UNDO, createButton(
            Material.CARROT_ON_A_STICK, Component.text("元に戻す", NamedTextColor.GOLD)
        ));
        inventory.setItem(BTN_SAVE, createButton(
            Material.WRITABLE_BOOK,
            Component.text("スペル保存", NamedTextColor.GREEN),
            List.of(Component.text("合計マナコスト: " + totalCost, NamedTextColor.AQUA))
        ));
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        // 構成エリアクリック → グリフ除去（スロットを空けたまま保持）
        if (slot >= COMPOSITION_START && slot <= COMPOSITION_END) {
            int index = slot - COMPOSITION_START;
            if (index < MAX_GLYPHS && composition[index] != null) {
                composition[index] = null;
                render();
            }
            return true;
        }

        // グリフパレットクリック → グリフ追加
        if (slot >= GLYPH_START && slot <= GLYPH_END) {
            handleGlyphClick(slot, clicker);
            return true;
        }

        // ページ切替
        if (slot == BTN_PREV_PAGE && currentPage > 0) {
            currentPage--;
            render();
            return true;
        }
        if (slot == BTN_NEXT_PAGE) {
            List<SpellComponent> available = plugin.getSpellRegistry().getByType(currentTab);
            int totalPages = Math.max(1, (int) Math.ceil((double) available.size() / Math.max(1, glyphsPerPage)));
            if (currentPage < totalPages - 1) {
                currentPage++;
                render();
            }
            return true;
        }

        // タブ切替
        if (slot == BTN_FORMS) {
            currentTab = SpellComponent.ComponentType.FORM;
            currentPage = 0;
            render();
            return true;
        }
        if (slot == BTN_EFFECTS) {
            currentTab = SpellComponent.ComponentType.EFFECT;
            currentPage = 0;
            render();
            return true;
        }
        if (slot == BTN_AUGMENTS) {
            currentTab = SpellComponent.ComponentType.AUGMENT;
            currentPage = 0;
            render();
            return true;
        }

        // クリア
        if (slot == BTN_CLEAR) {
            Arrays.fill(composition, null);
            render();
            return true;
        }

        // Undo（最後の非null要素を除去）
        if (slot == BTN_UNDO) {
            int last = lastOccupiedSlot();
            if (last >= 0) {
                composition[last] = null;
                render();
            }
            return true;
        }

        // 保存
        if (slot == BTN_SAVE) {
            saveSpell(clicker);
            return true;
        }

        return false;
    }

    private void handleGlyphClick(int slot, Player clicker) {
        if (slot % 9 == 0 || slot % 9 == 8) return;

        List<SpellComponent> available = plugin.getSpellRegistry().getByType(currentTab);

        int paletteIndex = 0;
        for (int s = GLYPH_START; s <= slot; s++) {
            if (s % 9 == 0 || s % 9 == 8) continue;
            if (s == slot) break;
            paletteIndex++;
        }

        int absoluteIndex = currentPage * glyphsPerPage + paletteIndex;
        if (absoluteIndex >= available.size()) return;

        SpellComponent comp = available.get(absoluteIndex);
        if (!unlockedGlyphs.contains(comp.getId().toString())) {
            clicker.sendMessage(Component.text("このグリフは未解放です！", NamedTextColor.RED));
            return;
        }

        String disableReason = getDisableReason(comp);
        if (disableReason != null) {
            clicker.sendMessage(Component.text(disableReason, NamedTextColor.RED));
            return;
        }

        // Formは必ずスロット0に配置
        if (comp.getType() == SpellComponent.ComponentType.FORM) {
            composition[0] = comp;
        } else {
            int emptySlot = firstEmptySlot();
            if (emptySlot >= 0) {
                composition[emptySlot] = comp;
            }
        }
        render();
    }

    private void saveSpell(Player clicker) {
        List<SpellComponent> compacted = compactComposition();
        if (compacted.isEmpty()) {
            clicker.sendMessage(Component.text("スペルが空です！", NamedTextColor.RED));
            return;
        }

        SpellRecipe recipe = new SpellRecipe("スペル" + (spellSlot + 1), compacted);
        if (!recipe.isValid()) {
            clicker.sendMessage(Component.text("無効なスペルです！形態(Form)で始まる必要があります", NamedTextColor.RED));
            return;
        }

        SpellRegistry registry = plugin.getSpellRegistry();
        String existingJson = spellBookItem.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);

        List<SpellRecipe> slots;
        if (existingJson != null) {
            slots = new ArrayList<>(SpellSerializer.deserializeSlots(existingJson, registry));
        } else {
            slots = new ArrayList<>();
        }

        while (slots.size() <= spellSlot) {
            slots.add(null);
        }
        slots.set(spellSlot, recipe);

        String newJson = SpellSerializer.serializeSlots(slots);
        spellBookItem.editMeta(meta ->
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOTS, PersistentDataType.STRING, newJson
            )
        );

        clicker.sendMessage(Component.text(
            "スロット" + (spellSlot + 1) + "に保存しました！（コスト: " + recipe.getTotalManaCost() + "マナ）",
            NamedTextColor.GREEN
        ));
        clicker.closeInventory();
    }

    private Set<String> loadUnlockedGlyphs(Player player) {
        String json = player.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        if (json == null) return new HashSet<>();

        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        Set<String> result = new HashSet<>();
        arr.forEach(el -> result.add(el.getAsString()));
        return result;
    }

    /**
     * Augmentタブ表示時、直前のEffect/Formとの互換性をチェック。
     * 構成配列上で、追加予定位置の直前にあるEffect/Formを基準にする。
     */
    private boolean isAugmentCompatibleWithContext(SpellComponent comp) {
        if (comp.getType() != SpellComponent.ComponentType.AUGMENT) return true;

        // 追加先スロットの直前にあるEffect/Formを探す
        int insertSlot = firstEmptySlot();
        if (insertSlot < 0) return false;

        SpellComponent lastTarget = null;
        for (int i = insertSlot - 1; i >= 0; i--) {
            SpellComponent c = composition[i];
            if (c != null && (c.getType() == SpellComponent.ComponentType.EFFECT
                || c.getType() == SpellComponent.ComponentType.FORM)) {
                lastTarget = c;
                break;
            }
        }
        if (lastTarget == null) return false;

        String targetKey = lastTarget.getId().getKey();
        String augmentKey = comp.getId().getKey();
        if (!plugin.getGlyphConfig().isAugmentCompatible(targetKey, augmentKey)) {
            return false;
        }

        // max-augmentsの上限チェック: 直前のEffect/Formに対して同じAugmentが既に上限に達しているか
        int maxStack = plugin.getGlyphConfig().getMaxAugmentStack(targetKey, augmentKey);
        if (maxStack < Integer.MAX_VALUE) {
            int currentCount = 0;
            for (int j = insertSlot - 1; j >= 0; j--) {
                SpellComponent c = composition[j];
                if (c == null) continue;
                if (c.getType() == SpellComponent.ComponentType.EFFECT
                    || c.getType() == SpellComponent.ComponentType.FORM) break;
                if (c.getType() == SpellComponent.ComponentType.AUGMENT
                    && c.getId().getKey().equals(augmentKey)) {
                    currentCount++;
                }
            }
            if (currentCount >= maxStack) {
                return false;
            }
        }

        return true;
    }

    /**
     * 増強の挿入先となる直前のEffect/Formを探す。
     */
    private SpellComponent findLastTargetForAugment() {
        int insertSlot = firstEmptySlot();
        if (insertSlot < 0) return null;
        for (int i = insertSlot - 1; i >= 0; i--) {
            SpellComponent c = composition[i];
            if (c != null && (c.getType() == SpellComponent.ComponentType.EFFECT
                || c.getType() == SpellComponent.ComponentType.FORM)) {
                return c;
            }
        }
        return null;
    }

    private NamedTextColor getTypeColor(SpellComponent.ComponentType type) {
        return switch (type) {
            case FORM -> NamedTextColor.GREEN;
            case EFFECT -> NamedTextColor.YELLOW;
            case AUGMENT -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private String localizeType(SpellComponent.ComponentType type) {
        return switch (type) {
            case FORM -> "形態";
            case EFFECT -> "効果";
            case AUGMENT -> "増強";
        };
    }

    private Material getTypeMaterial(SpellComponent.ComponentType type) {
        return switch (type) {
            case FORM -> Material.DIAMOND;
            case EFFECT -> Material.EMERALD;
            case AUGMENT -> Material.AMETHYST_SHARD;
        };
    }
}
