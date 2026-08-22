package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemCostRef;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.GlyphNames;
import com.arspaper.spell.SpellComponent;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * グリフ解放素材の閲覧GUI({@code /tf glyphs}、2026-07-28 新設)。
 *
 * <p>解放コストは筆記台({@link ScribingTableGui})の lore にしか出ておらず、
 * 「何を集めればどのグリフが解放できるのか」を筆記台の前に立たずに確認する手段が無かった。
 * この画面は {@link RecipeBrowserGui} と同じ操作感(枠付き一覧 → クリックで詳細 → 実アイテムで素材表示)で
 * 解放コストだけを<b>読み取り専用</b>に見せる。実際の解放は従来どおり筆記台で行う
 * (ここから解放できてしまうと、演出・TOCTOU再検証を持つ筆記台側の手順を二重に持つことになるため)。
 *
 * <p>TrinityForge 側は {@code com.trinityforge.integration.ars.ArsGlyphBrowserBridge} から
 * リフレクションでこのクラスを生成し {@code open()} を呼ぶ —
 * <b>コンストラクタ {@code (Player)} と {@code open()} のシグネチャを変えると TF 側が無言で
 * fail-soft に落ちる</b>ので注意({@link RecipeBrowserGui} と同じ約束)。
 */
public class GlyphBrowserGui extends BaseGui {

    private static final int ITEMS_PER_PAGE = 28; // 4行×7列
    private static final int ITEM_START = 10;
    private static final int BTN_PREV = 45;
    private static final int BTN_FILTER = 47;
    private static final int BTN_CLOSE = 49;
    private static final int BTN_NEXT = 53;

    /** 詳細画面: 素材を並べる3×3グリッド(レシピ詳細と同じ位置)。 */
    private static final int[] MATERIAL_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    /** 詳細画面: 必要経験値レベルの表示スロット。 */
    private static final int DETAIL_LEVEL_SLOT = 15;
    /** 詳細画面: コストまとめの表示スロット。 */
    private static final int DETAIL_SUMMARY_SLOT = 24;
    /** 詳細画面: 戻るボタン。 */
    private static final int DETAIL_BACK_SLOT = 49;

    /** 表示絞り込み。並べ替えは「種別→ティア」固定(筆記台の並びと揃える)。 */
    private enum FilterMode {
        ALL("すべて"),
        LOCKED("未解放のみ"),
        UNLOCKED("解放済みのみ");

        private final String label;

        FilterMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        FilterMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final List<SpellComponent> allGlyphs;
    private List<SpellComponent> visible;
    private int currentPage = 0;
    private FilterMode filterMode = FilterMode.ALL;

    private boolean detailMode = false;
    private SpellComponent detailGlyph = null;

    public GlyphBrowserGui(Player viewer) {
        super(viewer, 6, Component.text("グリフ解放素材", NamedTextColor.DARK_AQUA)
            .decoration(TextDecoration.ITALIC, false));
        this.allGlyphs = collectGlyphs();
        this.visible = this.allGlyphs;
        refresh();
    }

    private static List<SpellComponent> collectGlyphs() {
        // 並びは GlyphOrder が唯一の定義（3画面共通）。
        // 以前はここだけ ID のアルファベット順で、筆記台・呪文編集と食い違っていた。
        return com.arspaper.spell.GlyphOrder.canonical(
            ArsPaper.getInstance().getSpellRegistry().getAll());
    }

    private void refresh() {
        Set<String> unlocked = unlockedIds();
        this.visible = allGlyphs.stream()
            .filter(glyph -> switch (filterMode) {
                case ALL -> true;
                case LOCKED -> !unlocked.contains(glyph.getId().toString());
                case UNLOCKED -> unlocked.contains(glyph.getId().toString());
            })
            .toList();
        int totalPages = totalPages();
        if (currentPage > totalPages - 1) {
            currentPage = totalPages - 1;
        }
        if (currentPage < 0) {
            currentPage = 0;
        }
    }

    private int totalPages() {
        return Math.max(1, (int) Math.ceil((double) visible.size() / ITEMS_PER_PAGE));
    }

    @Override
    public void render() {
        if (detailMode && detailGlyph != null) {
            renderDetail();
            return;
        }
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        int totalPages = totalPages();
        currentPage = Math.min(currentPage, totalPages - 1);

        Set<String> unlocked = unlockedIds();
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int slot = ITEM_START;
        for (int i = startIndex; i < visible.size() && slot < 44; i++) {
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                i--;
                continue;
            }
            SpellComponent glyph = visible.get(i);
            inventory.setItem(slot, glyphButton(glyph, unlocked.contains(glyph.getId().toString())));
            slot++;
        }

        inventory.setItem(4, createButton(Material.PAPER,
            Component.text("ページ " + (currentPage + 1) + " / " + totalPages, NamedTextColor.WHITE),
            List.of(detailText("表示 " + visible.size() + " 件 / 全 " + allGlyphs.size() + " 件",
                    NamedTextColor.GRAY),
                detailText("解放は筆記台(Scribing Table)で行えます", NamedTextColor.DARK_GRAY))));
        inventory.setItem(BTN_FILTER, createButton(
            filterMode == FilterMode.LOCKED ? Material.IRON_BARS : Material.LIME_DYE,
            Component.text("表示: " + filterMode.label(), NamedTextColor.AQUA),
            List.of(detailText("クリックで切り替え", NamedTextColor.DARK_GRAY))));
        inventory.setItem(BTN_CLOSE, createButton(Material.DARK_OAK_DOOR,
            Component.text("閉じる", NamedTextColor.RED)));
        inventory.setItem(BTN_PREV, currentPage > 0
            ? createButton(Material.ARROW, Component.text("前のページ", NamedTextColor.WHITE))
            : createButton(Material.GRAY_STAINED_GLASS_PANE, Component.text("")));
        inventory.setItem(BTN_NEXT, currentPage < totalPages - 1
            ? createButton(Material.ARROW, Component.text("次のページ", NamedTextColor.WHITE))
            : createButton(Material.GRAY_STAINED_GLASS_PANE, Component.text("")));
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        if (detailMode) {
            if (slot == DETAIL_BACK_SLOT) {
                detailMode = false;
                detailGlyph = null;
                render();
            }
            return true;
        }
        if (slot == BTN_CLOSE) {
            clicker.closeInventory();
            return true;
        }
        if (slot == BTN_FILTER) {
            filterMode = filterMode.next();
            currentPage = 0;
            refresh();
            render();
            return true;
        }
        if (slot == BTN_PREV && currentPage > 0) {
            currentPage--;
            render();
            return true;
        }
        if (slot == BTN_NEXT && currentPage < totalPages() - 1) {
            currentPage++;
            render();
            return true;
        }
        SpellComponent clicked = glyphAtSlot(slot);
        if (clicked != null) {
            detailMode = true;
            detailGlyph = clicked;
            render();
        }
        return true;
    }

    /** 現在ページのスロット位置から SpellComponent を逆算する(枠を考慮)。 */
    private SpellComponent glyphAtSlot(int slot) {
        if (slot < ITEM_START || slot >= 44) return null;
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int s = ITEM_START;
        for (int i = startIndex; i < visible.size() && s < 44; i++) {
            if (s % 9 == 0 || s % 9 == 8) { s++; i--; continue; }
            if (s == slot) return visible.get(i);
            s++;
        }
        return null;
    }

    private ItemStack glyphButton(SpellComponent glyph, boolean unlocked) {
        GlyphConfig config = ArsPaper.getInstance().getGlyphConfig();
        String glyphKey = glyph.getId().getKey();

        List<Component> lore = new ArrayList<>();
        if (!glyph.getDescription().isEmpty()) {
            lore.add(detailText(glyph.getDescription(), NamedTextColor.GRAY));
        }
        lore.add(detailText("種類: " + localizeType(glyph.getType()), typeColor(glyph.getType())));
        lore.add(detailText("ティア: " + glyph.getTier() + " / マナコスト: " + glyph.getManaCost(),
            NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (unlocked) {
            lore.add(detailText("✔ 解放済み", NamedTextColor.GREEN));
        } else {
            lore.add(detailText("必要レベル: " + config.getUnlockLevel(glyphKey), NamedTextColor.YELLOW));
            for (Map.Entry<ItemCostRef, Integer> cost : config.getUnlockMaterials(glyphKey).entrySet()) {
                lore.add(detailText("  " + cost.getKey().displayName() + " ×" + cost.getValue(),
                    NamedTextColor.GRAY));
            }
        }
        lore.add(Component.empty());
        lore.add(detailText("クリックで必要素材を表示", NamedTextColor.DARK_GRAY));

        return createButton(iconOf(glyph),
            Component.text((unlocked ? "[解放済] " : "") + GlyphNames.display(glyph),
                unlocked ? NamedTextColor.GREEN : NamedTextColor.WHITE),
            lore);
    }

    /**
     * 1グリフの詳細: 解放素材を実アイテムで並べ、それぞれに「所持数 / 必要数」を添える。
     * 素材が9種を超える場合はまとめ(book)側にだけ載せる(既定の glyphs.yml は最大3種)。
     */
    private void renderDetail() {
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        SpellComponent glyph = detailGlyph;
        GlyphConfig config = ArsPaper.getInstance().getGlyphConfig();
        String glyphKey = glyph.getId().getKey();
        boolean unlocked = unlockedIds().contains(glyph.getId().toString());
        Map<ItemCostRef, Integer> materials = config.getUnlockMaterials(glyphKey);
        int levelCost = config.getUnlockLevel(glyphKey);

        List<Component> titleLore = new ArrayList<>();
        if (!glyph.getDescription().isEmpty()) {
            titleLore.add(detailText(glyph.getDescription(), NamedTextColor.GRAY));
        }
        titleLore.add(detailText("種類: " + localizeType(glyph.getType()), typeColor(glyph.getType())));
        titleLore.add(detailText("ティア: " + glyph.getTier() + " / マナコスト: " + glyph.getManaCost(),
            NamedTextColor.GRAY));
        titleLore.add(unlocked
            ? detailText("✔ 解放済み", NamedTextColor.GREEN)
            : detailText("未解放 — 筆記台で解放できます", NamedTextColor.YELLOW));
        inventory.setItem(4, createButton(iconOf(glyph),
            Component.text(GlyphNames.display(glyph),
                unlocked ? NamedTextColor.GREEN : NamedTextColor.WHITE),
            titleLore));

        int index = 0;
        int overflow = 0;
        for (Map.Entry<ItemCostRef, Integer> cost : materials.entrySet()) {
            if (index >= MATERIAL_SLOTS.length) {
                overflow++;
                continue;
            }
            int required = cost.getValue();
            int held = safeCount(cost.getKey());
            ItemStack display = cost.getKey().createStack(Math.max(1, Math.min(64, required)));
            if (display == null || display.getType().isAir()) {
                display = createButton(Material.BARRIER,
                    Component.text(cost.getKey().configKey(), NamedTextColor.RED));
            }
            final int heldFinal = held;
            display.editMeta(meta -> {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(detailText("必要: " + required + " 個", NamedTextColor.AQUA));
                lore.add(detailText("所持: " + heldFinal + " 個",
                    heldFinal >= required ? NamedTextColor.GREEN : NamedTextColor.RED));
                meta.lore(lore);
            });
            inventory.setItem(MATERIAL_SLOTS[index], display);
            index++;
        }
        if (materials.isEmpty()) {
            inventory.setItem(MATERIAL_SLOTS[0], createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Component.text("素材不要", NamedTextColor.GRAY)));
        }

        inventory.setItem(DETAIL_LEVEL_SLOT, createButton(Material.EXPERIENCE_BOTTLE,
            Component.text("必要経験値レベル: " + levelCost, NamedTextColor.YELLOW),
            List.of(detailText("現在: " + viewer.getLevel() + " レベル",
                viewer.getLevel() >= levelCost ? NamedTextColor.GREEN : NamedTextColor.RED))));

        List<Component> summary = new ArrayList<>();
        summary.add(detailText("必要レベル: " + levelCost, NamedTextColor.GRAY));
        for (Map.Entry<ItemCostRef, Integer> cost : materials.entrySet()) {
            summary.add(detailText(cost.getKey().displayName() + " ×" + cost.getValue(), NamedTextColor.GRAY));
        }
        if (overflow > 0) {
            summary.add(detailText("※ 種類が多いため、盤面には9種類までしか表示できません",
                NamedTextColor.DARK_GRAY));
        }
        summary.add(Component.empty());
        summary.add(detailText("解放は筆記台(Scribing Table)で行います", NamedTextColor.DARK_GRAY));
        inventory.setItem(DETAIL_SUMMARY_SLOT, createButton(Material.BOOK,
            Component.text("解放コスト", NamedTextColor.WHITE), summary));

        inventory.setItem(DETAIL_BACK_SLOT, createButton(Material.DARK_OAK_DOOR,
            Component.text("← 一覧に戻る", NamedTextColor.YELLOW)));
    }

    /** 所持数カウント。素材解決に失敗しても画面を落とさない(0扱い)。 */
    private int safeCount(ItemCostRef ref) {
        try {
            return ref.countIn(viewer);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /**
     * 筆記台({@link ScribingTableGui})が書き込む解放済み集合を読む。値は
     * {@code SpellComponent#getId().toString()} のJSON配列。壊れたJSONでも画面は開けるように
     * 空集合へ倒す(表示専用なので誤って「未解放」と出る以上の害はない)。
     */
    private Set<String> unlockedIds() {
        String json = viewer.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        Set<String> result = new HashSet<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            arr.forEach(el -> result.add(el.getAsString()));
        } catch (RuntimeException ignored) {
            return new HashSet<>();
        }
        return result;
    }

    /**
     * アイコンはグリフごと（{@link com.arspaper.spell.GlyphIcons} が唯一の定義）。
     * 未解放でも石炭に潰さない —— 素材を調べる画面なので、どのグリフの話かが分からないと意味がない。
     * 解放状態は名前の色と lore（✔解放済み / 必要レベル・素材）で示す。
     */
    private static Material iconOf(SpellComponent glyph) {
        return com.arspaper.spell.GlyphIcons.iconFor(glyph, ArsPaper.getInstance().getGlyphConfig());
    }

    private static NamedTextColor typeColor(SpellComponent.ComponentType type) {
        return switch (type) {
            case FORM -> NamedTextColor.GREEN;
            case EFFECT -> NamedTextColor.YELLOW;
            case AUGMENT -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private static String localizeType(SpellComponent.ComponentType type) {
        return switch (type) {
            case FORM -> "形態";
            case EFFECT -> "効果";
            case AUGMENT -> "増強";
        };
    }

    private static Component detailText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
