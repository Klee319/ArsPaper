package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellComponent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scribing Table のGUI。
 * BaseGuiを継承し、GuiListenerで統一管理される（リスナーリーク防止）。
 * Geyser互換のためボタン式（クリックイベントベース）で設計。
 */
public class ScribingTableGui extends BaseGui {

    private static final Gson GSON = new Gson();
    private static final int GLYPHS_PER_PAGE = 36; // slots 9-44
    private static final int BTN_PREV_PAGE = 45;
    private static final int BTN_NEXT_PAGE = 53;

    private final ArsPaper plugin;
    private final Location tableLocation;
    private final List<SpellComponent> displayedGlyphs = new ArrayList<>();
    private int currentPage = 0;
    /** アンロックアニメーション進行中フラグ（同一GUI内での二重クリック防止） */
    private boolean unlocking = false;

    public ScribingTableGui(ArsPaper plugin, Player player, Location tableLocation) {
        super(player, 6, Component.text("筆記台", NamedTextColor.DARK_AQUA));
        this.plugin = plugin;
        this.tableLocation = tableLocation;
    }

    @Override
    public void render() {
        inventory.clear();
        displayedGlyphs.clear();

        Set<String> unlocked = getUnlockedGlyphs();
        // カテゴリ順（形態→効果→増強）、同カテゴリ内はtier順にソート
        List<SpellComponent> allGlyphs = new ArrayList<>(plugin.getSpellRegistry().getAll());
        allGlyphs.sort(java.util.Comparator
            .<SpellComponent, Integer>comparing(c -> c.getType().ordinal())
            .thenComparingInt(SpellComponent::getTier));
        int totalPages = Math.max(1, (int) Math.ceil((double) allGlyphs.size() / GLYPHS_PER_PAGE));
        currentPage = Math.min(currentPage, totalPages - 1);

        // 装飾用ガラスパネルで上下段を埋める
        fillRow(0, Material.GRAY_STAINED_GLASS_PANE);
        fillRow(5, Material.GRAY_STAINED_GLASS_PANE);

        // ページネーション情報を上段中央に表示
        inventory.setItem(4, createButton(Material.PAPER,
            Component.text("ページ " + (currentPage + 1) + " / " + totalPages, NamedTextColor.WHITE)));

        // 現在ページのグリフをボタンとして配置
        int startIndex = currentPage * GLYPHS_PER_PAGE;
        int slot = 9;
        for (int i = startIndex; i < allGlyphs.size() && slot < 45; i++) {
            SpellComponent component = allGlyphs.get(i);
            boolean isUnlocked = unlocked.contains(component.getId().toString());
            inventory.setItem(slot, createGlyphButton(component, isUnlocked));
            displayedGlyphs.add(component);
            slot++;
        }

        // ページネーションボタン
        if (currentPage > 0) {
            inventory.setItem(BTN_PREV_PAGE, createButton(Material.ARROW,
                Component.text("← 前のページ", NamedTextColor.GOLD)));
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(BTN_NEXT_PAGE, createButton(Material.ARROW,
                Component.text("次のページ →", NamedTextColor.GOLD)));
        }
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        // ページネーション
        if (slot == BTN_PREV_PAGE && currentPage > 0) {
            currentPage--;
            render();
            return true;
        }
        if (slot == BTN_NEXT_PAGE) {
            int totalPages = Math.max(1, (int) Math.ceil(
                (double) plugin.getSpellRegistry().getAll().size() / GLYPHS_PER_PAGE));
            if (currentPage < totalPages - 1) {
                currentPage++;
                render();
            }
            return true;
        }

        if (slot < 9 || slot >= 45) return false;

        int glyphIndex = slot - 9;
        if (glyphIndex >= displayedGlyphs.size()) return false;

        SpellComponent component = displayedGlyphs.get(glyphIndex);
        Set<String> unlocked = getUnlockedGlyphs();

        if (unlocked.contains(component.getId().toString())) {
            clicker.sendMessage(Component.text("すでに解放済みです！", NamedTextColor.YELLOW));
            return true;
        }

        // 二重クリック防止（アニメーション進行中は拒否）
        if (unlocking) return true;

        if (!checkUnlockCost(clicker, component)) {
            return true;
        }

        unlocking = true;

        // 経験値レベルはアニメーション完了後に消費（中断時のXPロスを防止）
        GlyphConfig glyphConfig = plugin.getGlyphConfig();
        String glyphKey = component.getId().getKey();
        int levelCost = glyphConfig.getUnlockLevel(glyphKey);
        java.util.Map<Material, Integer> materials = glyphConfig.getUnlockMaterials(glyphKey);

        // アンロックアニメーション開始（GUI閉じ→素材消費→軌道演出→XP消費→アンロック完了）
        GlyphUnlockAnimation.play(
            plugin, clicker, component, tableLocation,
            materials, levelCost, unlocked,
            () -> {
                // アニメーション完了後にXPを再検証（TOCTOU防止）
                if (clicker.getLevel() < levelCost) {
                    clicker.sendMessage(Component.text(
                        "経験値レベルが不足しています！アンロックに失敗しました。", NamedTextColor.RED));
                    // 素材は既に消費済みのため返還（インベントリ満杯時は足元にドロップ）
                    for (var entry : materials.entrySet()) {
                        java.util.Map<Integer, ItemStack> overflow =
                            clicker.getInventory().addItem(new ItemStack(entry.getKey(), entry.getValue()));
                        overflow.values().forEach(item ->
                            clicker.getWorld().dropItemNaturally(clicker.getLocation(), item));
                    }
                    return;
                }
                clicker.setLevel(clicker.getLevel() - levelCost);
                saveUnlockedGlyphs(unlocked);
            }
        );
        return true;
    }

    private ItemStack createGlyphButton(SpellComponent component, boolean unlocked) {
        Material material = switch (component.getType()) {
            case FORM -> unlocked ? Material.DIAMOND : Material.COAL;
            case EFFECT -> unlocked ? Material.EMERALD : Material.COAL;
            case AUGMENT -> unlocked ? Material.AMETHYST_SHARD : Material.COAL;
        };

        NamedTextColor typeColor = switch (component.getType()) {
            case FORM -> NamedTextColor.GREEN;
            case EFFECT -> NamedTextColor.YELLOW;
            case AUGMENT -> NamedTextColor.LIGHT_PURPLE;
        };

        List<Component> lore = new ArrayList<>();
        if (!component.getDescription().isEmpty()) {
            lore.add(Component.text(component.getDescription(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("種類: " + localizeType(component.getType()), typeColor)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("マナコスト: " + component.getManaCost(), NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("ティア: " + component.getTier(), NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        if (!unlocked) {
            lore.add(Component.empty());
            lore.add(Component.text("クリックで解放！", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
            // 解放コストを箇条書きで表示
            String glyphKey = component.getId().getKey();
            GlyphConfig gc = plugin.getGlyphConfig();
            lore.add(Component.text("必要レベル: " + gc.getUnlockLevel(glyphKey), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            for (var entry : gc.getUnlockMaterials(glyphKey).entrySet()) {
                String name = gc.localizeMatNamePublic(entry.getKey());
                lore.add(Component.text("  " + name + " ×" + entry.getValue(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
        }

        return createButton(material,
            Component.text(
                (unlocked ? "[解放済] " : "") + component.getDisplayName(),
                unlocked ? NamedTextColor.GREEN : NamedTextColor.RED
            ),
            lore);
    }

    private String getUnlockCostDescription(SpellComponent component) {
        String glyphKey = component.getId().getKey();
        return plugin.getGlyphConfig().getUnlockCostDescription(glyphKey);
    }

    private String localizeType(SpellComponent.ComponentType type) {
        return switch (type) {
            case FORM -> "形態";
            case EFFECT -> "効果";
            case AUGMENT -> "増強";
        };
    }

    /**
     * アンロックコストが支払い可能かチェックする（消費はしない）。
     * 素材・レベルの消費はアニメーション側で行う。
     */
    private boolean checkUnlockCost(Player player, SpellComponent component) {
        GlyphConfig glyphConfig = plugin.getGlyphConfig();
        String glyphKey = component.getId().getKey();
        int levelCost = glyphConfig.getUnlockLevel(glyphKey);
        java.util.Map<Material, Integer> materials = glyphConfig.getUnlockMaterials(glyphKey);

        if (player.getLevel() < levelCost) {
            player.sendMessage(Component.text(
                "レベルが不足しています！必要: " + levelCost, NamedTextColor.RED
            ));
            return false;
        }

        // 全素材の在庫チェック（カスタムアイテムを除外してカウント）
        for (var entry : materials.entrySet()) {
            int count = countVanillaItems(player, entry.getKey());
            if (count < entry.getValue()) {
                player.sendMessage(Component.text(
                    "素材が不足しています！必要: " + glyphConfig.getUnlockCostDescription(glyphKey),
                    NamedTextColor.RED
                ));
                return false;
            }
        }

        return true;
    }

    private Set<String> getUnlockedGlyphs() {
        String json = viewer.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        if (json == null) return new HashSet<>();

        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        Set<String> result = new HashSet<>();
        arr.forEach(el -> result.add(el.getAsString()));
        return result;
    }

    /**
     * カスタムアイテムを除外して、指定Materialのバニラアイテム数をカウントする。
     */
    private static int countVanillaItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != material) continue;
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                    .has(com.arspaper.item.ItemKeys.CUSTOM_ITEM_ID)) continue;
            count += item.getAmount();
        }
        return count;
    }

    private void saveUnlockedGlyphs(Set<String> glyphs) {
        JsonArray arr = new JsonArray();
        glyphs.forEach(arr::add);
        viewer.getPersistentDataContainer().set(
            ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING, GSON.toJson(arr)
        );
    }
}
