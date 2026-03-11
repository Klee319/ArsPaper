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
 * 上段: 現在の構成表示（最大8グリフ）
 * 中段: 利用可能なForm/Effect/Augmentをタブ切り替え
 * 下段: 操作ボタン（保存・クリア・タブ切替）
 *
 * Geyser互換: 全てクリックイベントベースで操作。
 */
public class SpellCraftingGui extends BaseGui {

    private static final int COMPOSITION_START = 1;
    private static final int COMPOSITION_END = 8;
    private static final int GLYPH_START = 19;
    private static final int GLYPH_END = 43;

    // 下段ボタン位置
    private static final int BTN_FORMS = 46;
    private static final int BTN_EFFECTS = 47;
    private static final int BTN_AUGMENTS = 48;
    private static final int BTN_CLEAR = 50;
    private static final int BTN_SAVE = 52;
    private static final int BTN_UNDO = 51;

    private final ArsPaper plugin;
    private final int spellSlot;
    private final ItemStack spellBookItem;
    private final int maxGlyphTier;

    private final List<SpellComponent> composition = new ArrayList<>();
    private SpellComponent.ComponentType currentTab = SpellComponent.ComponentType.FORM;
    private final Set<String> unlockedGlyphs;

    public SpellCraftingGui(ArsPaper plugin, Player player, ItemStack spellBookItem, int spellSlot, int maxGlyphTier) {
        super(player, 6, Component.text("スペル作成 - スロット" + (spellSlot + 1), NamedTextColor.DARK_PURPLE));
        this.plugin = plugin;
        this.spellSlot = spellSlot;
        this.spellBookItem = spellBookItem;
        this.maxGlyphTier = maxGlyphTier;
        this.unlockedGlyphs = loadUnlockedGlyphs(player);

        // 既存スペルをロード
        loadExistingSpell();
    }

    private void loadExistingSpell() {
        if (!spellBookItem.hasItemMeta()) return;
        String slotsJson = spellBookItem.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) return;

        List<SpellRecipe> slots = SpellSerializer.deserializeSlots(slotsJson, plugin.getSpellRegistry());
        if (spellSlot < slots.size() && slots.get(spellSlot) != null) {
            composition.addAll(slots.get(spellSlot).getComponents());
        }
    }

    @Override
    public void render() {
        inventory.clear();

        // 上段: 構成表示エリアの枠
        fillRow(0, Material.BLACK_STAINED_GLASS_PANE);
        renderComposition();

        // 中段の仕切り
        fillRow(1, Material.GRAY_STAINED_GLASS_PANE);

        // 中段: グリフパレット
        renderGlyphPalette();

        // 下段: 操作ボタン
        fillRow(5, Material.BLACK_STAINED_GLASS_PANE);
        renderControls();
    }

    private void renderComposition() {
        for (int i = 0; i < 8; i++) {
            int slot = COMPOSITION_START + i;
            if (i < composition.size()) {
                SpellComponent comp = composition.get(i);
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

        int slot = GLYPH_START;
        for (SpellComponent comp : available) {
            if (slot > GLYPH_END) break;
            // 枠のスロット（左右端）をスキップ
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }

            boolean unlocked = unlockedGlyphs.contains(comp.getId().toString());
            NamedTextColor nameColor = unlocked ? getTypeColor(comp.getType()) : NamedTextColor.DARK_GRAY;
            Material mat = unlocked ? getTypeMaterial(comp.getType()) : Material.BARRIER;

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("マナ: " + comp.getManaCost(), NamedTextColor.AQUA));
            if (unlocked) {
                lore.add(Component.text("クリックで追加", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("未解放 - 筆記台で解放してください", NamedTextColor.RED));
            }

            inventory.setItem(slot, createButton(mat,
                Component.text(comp.getDisplayName(), nameColor), lore));
            slot++;
        }
    }

    private void renderControls() {
        // タブ切り替えボタン
        inventory.setItem(BTN_FORMS, createButton(
            currentTab == SpellComponent.ComponentType.FORM ? Material.LIME_DYE : Material.GREEN_DYE,
            Component.text("形態", NamedTextColor.GREEN)
        ));
        inventory.setItem(BTN_EFFECTS, createButton(
            currentTab == SpellComponent.ComponentType.EFFECT ? Material.LIME_DYE : Material.YELLOW_DYE,
            Component.text("効果", NamedTextColor.YELLOW)
        ));
        inventory.setItem(BTN_AUGMENTS, createButton(
            currentTab == SpellComponent.ComponentType.AUGMENT ? Material.LIME_DYE : Material.PURPLE_DYE,
            Component.text("増強", NamedTextColor.LIGHT_PURPLE)
        ));

        // 操作ボタン
        int totalCost = composition.stream().mapToInt(SpellComponent::getManaCost).sum();
        inventory.setItem(BTN_CLEAR, createButton(
            Material.TNT, Component.text("全消去", NamedTextColor.RED)
        ));
        inventory.setItem(BTN_UNDO, createButton(
            Material.ARROW, Component.text("元に戻す", NamedTextColor.GOLD)
        ));
        inventory.setItem(BTN_SAVE, createButton(
            Material.WRITABLE_BOOK,
            Component.text("スペル保存", NamedTextColor.GREEN),
            List.of(Component.text("合計マナコスト: " + totalCost, NamedTextColor.AQUA))
        ));
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        // 構成エリアクリック → グリフ除去
        if (slot >= COMPOSITION_START && slot <= COMPOSITION_END) {
            int index = slot - COMPOSITION_START;
            if (index < composition.size()) {
                composition.remove(index);
                render();
            }
            return true;
        }

        // グリフパレットクリック → グリフ追加
        if (slot >= GLYPH_START && slot <= GLYPH_END) {
            handleGlyphClick(slot, clicker);
            return true;
        }

        // タブ切替
        if (slot == BTN_FORMS) {
            currentTab = SpellComponent.ComponentType.FORM;
            render();
            return true;
        }
        if (slot == BTN_EFFECTS) {
            currentTab = SpellComponent.ComponentType.EFFECT;
            render();
            return true;
        }
        if (slot == BTN_AUGMENTS) {
            currentTab = SpellComponent.ComponentType.AUGMENT;
            render();
            return true;
        }

        // クリア
        if (slot == BTN_CLEAR) {
            composition.clear();
            render();
            return true;
        }

        // Undo
        if (slot == BTN_UNDO) {
            if (!composition.isEmpty()) {
                composition.remove(composition.size() - 1);
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
        if (composition.size() >= 8) {
            clicker.sendMessage(Component.text("スペルが満杯です！（最大8グリフ）", NamedTextColor.RED));
            return;
        }

        List<SpellComponent> available = plugin.getSpellRegistry().getByType(currentTab);

        // スロットからグリフインデックスを計算（枠スキップ考慮）
        int paletteIndex = 0;
        for (int s = GLYPH_START; s <= slot; s++) {
            if (s % 9 == 0 || s % 9 == 8) continue;
            if (s == slot) break;
            paletteIndex++;
        }

        if (paletteIndex >= available.size()) return;

        SpellComponent comp = available.get(paletteIndex);
        if (!unlockedGlyphs.contains(comp.getId().toString())) {
            clicker.sendMessage(Component.text("このグリフは未解放です！", NamedTextColor.RED));
            return;
        }

        // ティア制限チェック
        if (comp.getTier() > maxGlyphTier) {
            clicker.sendMessage(Component.text(
                "このスペルブックはティア" + maxGlyphTier + "以下のグリフのみ使用可能です！",
                NamedTextColor.RED));
            return;
        }

        // バリデーション: 先頭はFormであること
        if (composition.isEmpty() && comp.getType() != SpellComponent.ComponentType.FORM) {
            clicker.sendMessage(Component.text("最初のグリフは形態(Form)である必要があります！", NamedTextColor.RED));
            return;
        }

        composition.add(comp);
        render();
    }

    private void saveSpell(Player clicker) {
        if (composition.isEmpty()) {
            clicker.sendMessage(Component.text("スペルが空です！", NamedTextColor.RED));
            return;
        }

        SpellRecipe recipe = new SpellRecipe("スペル" + (spellSlot + 1), composition);
        if (!recipe.isValid()) {
            clicker.sendMessage(Component.text("無効なスペルです！形態(Form)で始まる必要があります", NamedTextColor.RED));
            return;
        }

        // 既存スロットデータを取得
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
