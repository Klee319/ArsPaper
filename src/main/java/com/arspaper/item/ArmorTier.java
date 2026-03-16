package com.arspaper.item;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * メイジアーマーのティア定義。
 * ティアが上がるほどマナボーナスが増加。
 */
public enum ArmorTier {

    NOVICE(1, "初級", 5, 1, NamedTextColor.LIGHT_PURPLE),
    APPRENTICE(2, "中級", 15, 2, NamedTextColor.DARK_PURPLE),
    ARCHMAGE(3, "上級", 30, 3, NamedTextColor.GOLD);

    private final int tier;
    private final String displayName;
    private final int manaBonus;
    private final int threadSlots;
    private final NamedTextColor color;

    ArmorTier(int tier, String displayName, int manaBonus, int threadSlots, NamedTextColor color) {
        this.tier = tier;
        this.displayName = displayName;
        this.manaBonus = manaBonus;
        this.threadSlots = threadSlots;
        this.color = color;
    }

    public int getTier() { return tier; }
    public String getDisplayName() { return displayName; }
    public int getManaBonus() { return manaBonus; }
    public int getThreadSlots() { return threadSlots; }
    public NamedTextColor getColor() { return color; }

    public static ArmorTier fromTier(int tier) {
        for (ArmorTier t : values()) {
            if (t.tier == tier) return t;
        }
        return NOVICE;
    }
}
