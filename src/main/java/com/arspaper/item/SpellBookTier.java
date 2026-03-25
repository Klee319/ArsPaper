package com.arspaper.item;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * スペルブックのティア定義。
 * ティアが上がるほどスロット数・使用可能グリフティアが増加。
 */
public enum SpellBookTier {

    NOVICE(1, "見習いの魔法書", 3, 1, 100001, NamedTextColor.LIGHT_PURPLE),
    APPRENTICE(2, "魔術師の魔術書", 6, 2, 100002, NamedTextColor.DARK_PURPLE),
    ARCHMAGE(3, "大魔導士の魔導書", 10, 3, 100003, NamedTextColor.GOLD);

    private final int tier;
    private final String displayName;
    private final int maxSlots;
    private final int maxGlyphTier;
    private final int customModelData;
    private final NamedTextColor color;

    SpellBookTier(int tier, String displayName, int maxSlots, int maxGlyphTier,
                  int customModelData, NamedTextColor color) {
        this.tier = tier;
        this.displayName = displayName;
        this.maxSlots = maxSlots;
        this.maxGlyphTier = maxGlyphTier;
        this.customModelData = customModelData;
        this.color = color;
    }

    public int getTier() { return tier; }
    public String getDisplayName() { return displayName; }
    public int getMaxSlots() { return maxSlots; }
    public int getMaxGlyphTier() { return maxGlyphTier; }
    public int getCustomModelData() { return customModelData; }
    public NamedTextColor getColor() { return color; }

    public static SpellBookTier fromTier(int tier) {
        for (SpellBookTier t : values()) {
            if (t.tier == tier) return t;
        }
        return NOVICE;
    }

    public String getItemId() {
        return "spell_book_" + name().toLowerCase();
    }
}
