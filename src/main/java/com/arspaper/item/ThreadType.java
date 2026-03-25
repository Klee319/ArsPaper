package com.arspaper.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 防具スレッドのタイプ。
 * スレッドアイテムおよび防具PDCのスロットデータとして使用。
 * 効果量と重複設定はconfig.ymlのthreadsセクションで上書き可能。
 */
public enum ThreadType {

    // === 空スレッド ===
    EMPTY("empty", "空のスレッド", 300001, NamedTextColor.GRAY,
        0, 0, null, 0, 0, 0, 0, Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE),

    // === マナ系 ===
    MANA_REGEN("mana_regen", "マナ回復速度上昇のスレッド", 300002, NamedTextColor.AQUA,
        1, 0, null, 0, 0, 0, 0, Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE),
    MANA_BOOST("mana_boost", "マナ最大値上昇のスレッド", 300003, NamedTextColor.BLUE,
        0, 20, null, 0, 0, 0, 0, Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE),

    // === ポーション効果系 ===
    SPEED("speed", "迅速のスレッド", 300004, NamedTextColor.WHITE,
        0, 0, PotionEffectType.SPEED, 0, 0, 0, 0, Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE),
    JUMP_BOOST("jump_boost", "跳躍のスレッド", 300005, NamedTextColor.GREEN,
        0, 0, PotionEffectType.JUMP_BOOST, 0, 0, 0, 0, Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE),
    NIGHT_VISION("night_vision", "暗視のスレッド", 300006, NamedTextColor.DARK_AQUA,
        0, 0, PotionEffectType.NIGHT_VISION, 0, 0, 0, 0, Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE),
    FIRE_RESISTANCE("fire_resistance", "耐火のスレッド", 300007, NamedTextColor.RED,
        0, 0, PotionEffectType.FIRE_RESISTANCE, 0, 0, 0, 0, Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
    DOLPHINS_GRACE("dolphins_grace", "イルカの好意のスレッド", 300008, NamedTextColor.DARK_AQUA,
        0, 0, PotionEffectType.DOLPHINS_GRACE, 0, 0, 0, 0, Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
    CONDUIT_POWER("conduit_power", "コンジットパワーのスレッド", 300009, NamedTextColor.AQUA,
        0, 0, PotionEffectType.CONDUIT_POWER, 0, 0, 0, 0, Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE),
    HERO_OF_THE_VILLAGE("hero_of_the_village", "村の英雄のスレッド", 300010, NamedTextColor.GREEN,
        0, 0, PotionEffectType.HERO_OF_THE_VILLAGE, 0, 0, 0, 0, Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE),
    HEALTH_BOOST("health_boost", "体力増強のスレッド", 300011, NamedTextColor.RED,
        0, 0, PotionEffectType.HEALTH_BOOST, 0, 0, 0, 0, Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE),

    // === マナ回復系 ===
    HIT_MANA_RECOVERY("hit_mana_recovery", "被弾マナ回復のスレッド", 300012, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 3, 0, Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE),
    DAMAGE_MANA_RECOVERY("damage_mana_recovery", "攻撃マナ回復のスレッド", 300013, NamedTextColor.DARK_RED,
        0, 0, null, 0, 0, 0, 2, Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE),

    // === 特殊系 ===
    SPELL_COST_DOWN("spell_cost_down", "詠唱効率のスレッド", 300014, NamedTextColor.YELLOW,
        0, 0, null, 0, 10, 0, 0, Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE),
    FLIGHT("flight", "飛行のスレッド", 300015, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE),
    BACKPACK("backpack", "バックパックのスレッド", 300016, NamedTextColor.DARK_GREEN,
        0, 0, null, 0, 0, 0, 0, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);

    private final String id;
    private final String displayName;
    private final int customModelData;
    private final NamedTextColor color;
    private final int regenBonus;
    private final int manaBonus;
    private final PotionEffectType potionEffect;
    private final int potionAmplifier;
    private final int costReductionPercent;
    private final int hitManaRecovery;
    private final int damageManaRecovery;
    private final Material baseMaterial;

    ThreadType(String id, String displayName, int customModelData, NamedTextColor color,
               int regenBonus, int manaBonus,
               PotionEffectType potionEffect, int potionAmplifier,
               int costReductionPercent,
               int hitManaRecovery, int damageManaRecovery,
               Material baseMaterial) {
        this.id = id;
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.color = color;
        this.regenBonus = regenBonus;
        this.manaBonus = manaBonus;
        this.potionEffect = potionEffect;
        this.potionAmplifier = potionAmplifier;
        this.costReductionPercent = costReductionPercent;
        this.hitManaRecovery = hitManaRecovery;
        this.damageManaRecovery = damageManaRecovery;
        this.baseMaterial = baseMaterial;
    }

    public Material getBaseMaterial() { return baseMaterial; }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getRegenBonus() { return regenBonus; }
    public int getManaBonus() { return manaBonus; }
    public int getCustomModelData() { return customModelData; }
    public NamedTextColor getColor() { return color; }
    public PotionEffectType getPotionEffect() { return potionEffect; }
    public int getPotionAmplifier() { return potionAmplifier; }
    public int getCostReductionPercent() { return costReductionPercent; }
    public int getHitManaRecovery() { return hitManaRecovery; }
    public int getDamageManaRecovery() { return damageManaRecovery; }

    /** @deprecated spell_power は削除済み。互換性のため0を返す */
    @Deprecated
    public int getSpellPowerPercent() { return 0; }

    /** 効果を持つスレッドか（空でない） */
    public boolean hasEffect() { return this != EMPTY; }

    /** ポーション効果を付与するスレッドか */
    public boolean hasPotionEffect() { return potionEffect != null; }

    /** 飛行スレッドか */
    public boolean isFlightThread() { return this == FLIGHT; }

    /** バックパックスレッドか */
    public boolean isBackpackThread() { return this == BACKPACK; }

    /**
     * このスレッドの効果説明をComponent Loreとして返す。
     */
    public List<Component> getEffectLore() {
        List<Component> lore = new ArrayList<>();
        if (regenBonus > 0) {
            lore.add(loreText("マナ回復速度 +" + regenBonus + "/tick", NamedTextColor.AQUA));
        }
        if (manaBonus > 0) {
            lore.add(loreText("マナ最大値 +" + manaBonus, NamedTextColor.BLUE));
        }
        if (potionEffect != null) {
            String effectName = switch (id) {
                case "speed" -> "移動速度上昇";
                case "jump_boost" -> "跳躍力上昇";
                case "night_vision" -> "暗視";
                case "fire_resistance" -> "火炎耐性";
                case "dolphins_grace" -> "イルカの好意";
                case "conduit_power" -> "コンジットパワー";
                case "hero_of_the_village" -> "村の英雄";
                case "health_boost" -> "体力増強";
                default -> "ポーション効果";
            };
            lore.add(loreText(effectName + " (装備中常時)", NamedTextColor.GREEN));
        }
        if (hitManaRecovery > 0) {
            lore.add(loreText("被弾時マナ回復 +" + hitManaRecovery, NamedTextColor.GOLD));
        }
        if (damageManaRecovery > 0) {
            lore.add(loreText("攻撃時マナ回復 +" + damageManaRecovery, NamedTextColor.DARK_RED));
        }
        if (costReductionPercent > 0) {
            lore.add(loreText("マナコスト -" + costReductionPercent + "%", NamedTextColor.YELLOW));
        }
        if (this == FLIGHT) {
            lore.add(loreText("エリトラ飛行 (装備中常時)", NamedTextColor.WHITE));
        }
        if (this == BACKPACK) {
            lore.add(loreText("追加インベントリ 27スロット", NamedTextColor.DARK_GREEN));
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
        // 後方互換: 削除されたスレッドは無視
        if ("water_breathing".equals(id) || "spell_power".equals(id)) return null;
        return null;
    }
}
