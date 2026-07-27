package com.arspaper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

final class ThreadLoreMerge {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ThreadLoreMerge() {
    }

    static List<Component> merge(List<Component> existing, List<Component> previousOwned,
                                 List<Component> nextOwned) {
        List<Component> result = new ArrayList<>(existing == null ? List.of() : existing);
        if (!previousOwned.isEmpty() && endsWith(result, previousOwned)) {
            result.subList(result.size() - previousOwned.size(), result.size()).clear();
        } else if (previousOwned.isEmpty()) {
            removeLegacySuffix(result);
        }
        result.addAll(nextOwned);
        return result;
    }

    private static boolean endsWith(List<Component> lore, List<Component> suffix) {
        if (suffix.size() > lore.size()) {
            return false;
        }
        int offset = lore.size() - suffix.size();
        for (int i = 0; i < suffix.size(); i++) {
            if (!lore.get(offset + i).equals(suffix.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static void removeLegacySuffix(List<Component> lore) {
        int end = lore.size();
        int cursor = end;
        while (cursor > 0 && PLAIN.serialize(lore.get(cursor - 1)).matches("\\s{2}\\d+: .+")) {
            cursor--;
        }
        if (cursor == 0 || !PLAIN.serialize(lore.get(cursor - 1)).startsWith("スレッドスロット: ")) {
            return;
        }
        cursor--;
        if (cursor > 0 && PLAIN.serialize(lore.get(cursor - 1)).startsWith("マナボーナス: ")) {
            cursor--;
        }
        if (cursor > 0 && PLAIN.serialize(lore.get(cursor - 1)).startsWith("セット: ")) {
            cursor--;
        }
        lore.subList(cursor, end).clear();
    }
}
