package com.arspaper.item;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * スペルワンドのティア定義。
 * ティアが上がるほど最大グリフティアが増加。1スロット固定。
 */
public enum WandTier {

    NOVICE(1, "初級", 1, 100010, NamedTextColor.LIGHT_PURPLE),
    APPRENTICE(2, "中級", 2, 100011, NamedTextColor.DARK_PURPLE),
    ARCHMAGE(3, "上級", 3, 100012, NamedTextColor.GOLD);

    private final int tier;
    private final String displayName;
    private final int maxGlyphTier;
    private final int customModelData;
    private final NamedTextColor color;

    WandTier(int tier, String displayName, int maxGlyphTier,
             int customModelData, NamedTextColor color) {
        this.tier = tier;
        this.displayName = displayName;
        this.maxGlyphTier = maxGlyphTier;
        this.customModelData = customModelData;
        this.color = color;
    }

    public int getTier() { return tier; }
    public String getDisplayName() { return displayName; }
    public int getMaxGlyphTier() { return maxGlyphTier; }
    public int getCustomModelData() { return customModelData; }
    public NamedTextColor getColor() { return color; }

    public static WandTier fromTier(int tier) {
        for (WandTier t : values()) {
            if (t.tier == tier) return t;
        }
        return NOVICE;
    }

    public String getItemId() {
        return "wand_" + name().toLowerCase();
    }
}
