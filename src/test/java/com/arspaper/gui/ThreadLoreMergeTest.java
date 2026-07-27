package com.arspaper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreadLoreMergeTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void replacesOnlyPreviouslyOwnedSuffix() {
        List<Component> merged = ThreadLoreMerge.merge(
                lines("品質", "防御力", "スレッドスロット: 2", "  1: 魔力"),
                lines("スレッドスロット: 2", "  1: 魔力"),
                lines("スレッドスロット: 2", "  1: 回復"));

        assertEquals(List.of("品質", "防御力", "スレッドスロット: 2", "  1: 回復"), plain(merged));
    }

    @Test
    void migratesLegacySuffixWithoutKeepingFalseManaBonus() {
        List<Component> merged = ThreadLoreMerge.merge(
                lines("品質", "防御力", "セット: DIAMOND_CHESTPLATE", "マナボーナス: +0",
                        "スレッドスロット: 2", "  1: 魔力"),
                List.of(),
                lines("スレッドスロット: 2", "  1: 回復"));

        assertEquals(List.of("品質", "防御力", "スレッドスロット: 2", "  1: 回復"), plain(merged));
    }

    @Test
    void preservesForeignLoreWhenOwnedPayloadDoesNotMatchSuffix() {
        List<Component> merged = ThreadLoreMerge.merge(
                lines("マナボーナス: 特殊説明", "品質"),
                lines("スレッドスロット: 3"),
                lines("スレッドスロット: 2"));

        assertEquals(List.of("マナボーナス: 特殊説明", "品質", "スレッドスロット: 2"), plain(merged));
    }

    private static List<Component> lines(String... values) {
        return java.util.Arrays.stream(values).<Component>map(Component::text).toList();
    }

    private static List<String> plain(List<Component> values) {
        return values.stream().map(PLAIN::serialize).toList();
    }
}
