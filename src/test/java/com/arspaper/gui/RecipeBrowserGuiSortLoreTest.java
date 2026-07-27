package com.arspaper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 並べ替えボタンの lore 生成 ({@link RecipeBrowserGui#sortLore})。
 *
 * <p>TF図鑑の {@code CollectionGui#sortButton} と同じ「全モード列挙 + 選択中だけ ▶ 緑」の
 * 形式になっているかを検証する。GUI描画そのものはBukkitに依存するためテストしない
 * ({@link RecipeBrowserFilterTest} と同じ方針で、純粋関数だけを切り出してテストする)。
 */
class RecipeBrowserGuiSortLoreTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    @DisplayName("全モードが列挙される")
    void listsAllModes() {
        List<Component> lore = RecipeBrowserGui.sortLore(RecipeBrowserFilter.SortMode.DEFAULT);
        for (RecipeBrowserFilter.SortMode mode : RecipeBrowserFilter.SortMode.values()) {
            assertTrue(lore.stream().anyMatch(c -> plain(c).contains(mode.label())),
                "lore should mention " + mode.label());
        }
    }

    @Test
    @DisplayName("現在のモードだけが ▶ 付きで GREEN、他は DARK_GRAY")
    void marksCurrentModeOnly() {
        RecipeBrowserFilter.SortMode current = RecipeBrowserFilter.SortMode.KIND;
        List<Component> lore = RecipeBrowserGui.sortLore(current);

        for (RecipeBrowserFilter.SortMode mode : RecipeBrowserFilter.SortMode.values()) {
            Component line = lore.stream()
                .filter(c -> plain(c).contains(mode.label()))
                .findFirst()
                .orElseThrow();
            if (mode == current) {
                assertTrue(plain(line).startsWith("▶ "), "current mode line should start with ▶ ");
                assertEquals(NamedTextColor.GREEN, line.color());
            } else {
                assertTrue(plain(line).startsWith("  "), "non-current mode line should start with two spaces");
                assertEquals(NamedTextColor.DARK_GRAY, line.color());
            }
        }
    }

    @Test
    @DisplayName("最初と最後の補足行は維持される")
    void keepsGuidanceLines() {
        List<Component> lore = RecipeBrowserGui.sortLore(RecipeBrowserFilter.SortMode.DEFAULT);
        assertEquals("クリックで次の並び順へ", plain(lore.get(0)));
        assertEquals("種別=item-stats の使用スキル(無ければ素材)", plain(lore.get(lore.size() - 1)));
    }
}
