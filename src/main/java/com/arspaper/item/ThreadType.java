package com.arspaper.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 防具スレッドのタイプ。
 * スレッドアイテムおよび防具PDCのスロットデータとして使用。
 *
 * カテゴリ:
 * - MANA: マナリジェン/最大マナに影響
 * - POTION: 装備中ポーション効果を付与
 * - SPELL: スペルの威力/コストに影響
 */
public enum ThreadType {

    // === 空スレッド ===
    EMPTY("empty", "空のスレッド", 300001, NamedTextColor.GRAY,
        0, 0, null, 0, 0, 0),

    // === マナ系 ===
    MANA_REGEN("mana_regen", "マナリジェンスレッド", 300002, NamedTextColor.AQUA,
        1, 0, null, 0, 0, 0),
    MANA_BOOST("mana_boost", "マナブーストスレッド", 300003, NamedTextColor.BLUE,
        0, 20, null, 0, 0, 0),

    // === ポーション効果系 ===
    SPEED("speed", "迅速のスレッド", 300004, NamedTextColor.WHITE,
        0, 0, PotionEffectType.SPEED, 0, 0, 0),
    JUMP_BOOST("jump_boost", "跳躍のスレッド", 300005, NamedTextColor.GREEN,
        0, 0, PotionEffectType.JUMP_BOOST, 0, 0, 0),
    NIGHT_VISION("night_vision", "暗視のスレッド", 300006, NamedTextColor.DARK_AQUA,
        0, 0, PotionEffectType.NIGHT_VISION, 0, 0, 0),
    FIRE_RESISTANCE("fire_resistance", "耐火のスレッド", 300007, NamedTextColor.RED,
        0, 0, PotionEffectType.FIRE_RESISTANCE, 0, 0, 0),
    WATER_BREATHING("water_breathing", "水中呼吸のスレッド", 300008, NamedTextColor.DARK_BLUE,
        0, 0, PotionEffectType.WATER_BREATHING, 0, 0, 0),

    // === スペル系 ===
    SPELL_POWER("spell_power", "魔力増幅のスレッド", 300009, NamedTextColor.LIGHT_PURPLE,
        0, 0, null, 0, 15, 0),
    SPELL_COST_DOWN("spell_cost_down", "詠唱効率のスレッド", 300010, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 10);

    private final String id;
    private final String displayName;
    private final int customModelData;
    private final NamedTextColor color;
    private final int regenBonus;
    private final int manaBonus;
    private final PotionEffectType potionEffect;
    private final int potionAmplifier;
    private final int spellPowerPercent;   // スペル効果+N%
    private final int costReductionPercent; // マナコスト-N%

    ThreadType(String id, String displayName, int customModelData, NamedTextColor color,
               int regenBonus, int manaBonus,
               PotionEffectType potionEffect, int potionAmplifier,
               int spellPowerPercent, int costReductionPercent) {
        this.id = id;
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.color = color;
        this.regenBonus = regenBonus;
        this.manaBonus = manaBonus;
        this.potionEffect = potionEffect;
        this.potionAmplifier = potionAmplifier;
        this.spellPowerPercent = spellPowerPercent;
        this.costReductionPercent = costReductionPercent;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getRegenBonus() { return regenBonus; }
    public int getManaBonus() { return manaBonus; }
    public int getCustomModelData() { return customModelData; }
    public NamedTextColor getColor() { return color; }
    public PotionEffectType getPotionEffect() { return potionEffect; }
    public int getPotionAmplifier() { return potionAmplifier; }
    public int getSpellPowerPercent() { return spellPowerPercent; }
    public int getCostReductionPercent() { return costReductionPercent; }

    /** 効果を持つスレッドか（空でない） */
    public boolean hasEffect() { return this != EMPTY; }

    /** ポーション効果を付与するスレッドか */
    public boolean hasPotionEffect() { return potionEffect != null; }

    /** スペル関連ボーナスを持つスレッドか */
    public boolean hasSpellBonus() { return spellPowerPercent > 0 || costReductionPercent > 0; }

    /**
     * このスレッドの効果説明をComponent Loreとして返す。
     */
    public List<Component> getEffectLore() {
        List<Component> lore = new ArrayList<>();
        if (regenBonus > 0) {
            lore.add(loreText("マナリジェン +" + regenBonus + "/tick", NamedTextColor.AQUA));
        }
        if (manaBonus > 0) {
            lore.add(loreText("最大マナ +" + manaBonus, NamedTextColor.BLUE));
        }
        if (potionEffect != null) {
            String effectName = switch (id) {
                case "speed" -> "移動速度上昇";
                case "jump_boost" -> "跳躍力上昇";
                case "night_vision" -> "暗視";
                case "fire_resistance" -> "火炎耐性";
                case "water_breathing" -> "水中呼吸";
                default -> "ポーション効果";
            };
            lore.add(loreText(effectName + " (装備中常時)", NamedTextColor.GREEN));
        }
        if (spellPowerPercent > 0) {
            lore.add(loreText("スペル威力 +" + spellPowerPercent + "%", NamedTextColor.LIGHT_PURPLE));
        }
        if (costReductionPercent > 0) {
            lore.add(loreText("マナコスト -" + costReductionPercent + "%", NamedTextColor.YELLOW));
        }
        return lore;
    }

    private static Component loreText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public static ThreadType fromId(String id) {
        if (id == null) return null;
        for (ThreadType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
