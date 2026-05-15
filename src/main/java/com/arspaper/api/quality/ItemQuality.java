package com.arspaper.api.quality;

import org.bukkit.NamespacedKey;

/**
 * ArsPaperアイテム品質ランク (0=なし, 1-5=Poor/Fine/Superior/Exceptional/Masterwork)。
 * PDC: arspaper:quality (INTEGER 0-5)
 */
public final class ItemQuality {

    private ItemQuality() {}

    public static final NamespacedKey KEY = new NamespacedKey("arspaper", "quality");

    public static final int MIN = 0;
    public static final int MAX = 5;

    public static int clamp(int q) {
        return Math.max(MIN, Math.min(MAX, q));
    }

    public static String labelOf(int q) {
        return switch (clamp(q)) {
            case 1 -> "Poor";
            case 2 -> "Fine";
            case 3 -> "Superior";
            case 4 -> "Exceptional";
            case 5 -> "Masterwork";
            default -> "";
        };
    }
}
