package com.arspaper.api.quality;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** ItemQualityのPDC R/W + lore末尾自動更新。 */
public final class QualityHelper {

    private QualityHelper() {}

    private static final String LORE_PREFIX = "Quality: ";

    /** stackから現在の品質を取得 (0=なし)。 */
    public static int get(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return 0;
        Integer q = stack.getItemMeta().getPersistentDataContainer()
            .get(ItemQuality.KEY, PersistentDataType.INTEGER);
        return q == null ? 0 : ItemQuality.clamp(q);
    }

    /** stackに品質を設定。0なら除去。lore末尾を更新する。 */
    public static void set(ItemStack stack, int quality) {
        if (stack == null) return;
        final int q = ItemQuality.clamp(quality);
        stack.editMeta(meta -> {
            if (q == 0) {
                meta.getPersistentDataContainer().remove(ItemQuality.KEY);
            } else {
                meta.getPersistentDataContainer().set(
                    ItemQuality.KEY, PersistentDataType.INTEGER, q);
            }
            updateLore(meta, q);
        });
    }

    private static void updateLore(ItemMeta meta, int quality) {
        List<Component> lore = meta.lore();
        List<Component> newLore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);

        // 既存のQuality行を除去
        newLore.removeIf(line -> {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            return plain.startsWith(LORE_PREFIX);
        });

        if (quality > 0) {
            NamedTextColor color = switch (quality) {
                case 1 -> NamedTextColor.GRAY;
                case 2 -> NamedTextColor.GREEN;
                case 3 -> NamedTextColor.AQUA;
                case 4 -> NamedTextColor.BLUE;
                case 5 -> NamedTextColor.LIGHT_PURPLE;
                default -> NamedTextColor.WHITE;
            };
            newLore.add(Component.text(LORE_PREFIX + ItemQuality.labelOf(quality), color)
                .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(newLore.isEmpty() ? null : newLore);
    }
}
