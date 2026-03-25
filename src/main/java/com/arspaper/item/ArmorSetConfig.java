package com.arspaper.item;

import org.bukkit.Color;
import org.bukkit.Material;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Map;

/**
 * armors.ymlの1セット分の防具定義データ。
 * 不変オブジェクトとしてYAMLからパースされる。
 */
public final class ArmorSetConfig {

    private static final String[] SLOT_NAMES = {"helmet", "chestplate", "leggings", "boots"};
    private static final Map<String, Material> SLOT_MATERIALS = Map.of(
        "helmet", Material.LEATHER_HELMET,
        "chestplate", Material.LEATHER_CHESTPLATE,
        "leggings", Material.LEATHER_LEGGINGS,
        "boots", Material.LEATHER_BOOTS
    );
    private static final Map<String, Map<String, Material>> MATERIAL_MAP = Map.of(
        "LEATHER", Map.of(
            "helmet", Material.LEATHER_HELMET,
            "chestplate", Material.LEATHER_CHESTPLATE,
            "leggings", Material.LEATHER_LEGGINGS,
            "boots", Material.LEATHER_BOOTS
        ),
        "IRON", Map.of(
            "helmet", Material.IRON_HELMET,
            "chestplate", Material.IRON_CHESTPLATE,
            "leggings", Material.IRON_LEGGINGS,
            "boots", Material.IRON_BOOTS
        ),
        "DIAMOND", Map.of(
            "helmet", Material.DIAMOND_HELMET,
            "chestplate", Material.DIAMOND_CHESTPLATE,
            "leggings", Material.DIAMOND_LEGGINGS,
            "boots", Material.DIAMOND_BOOTS
        ),
        "NETHERITE", Map.of(
            "helmet", Material.NETHERITE_HELMET,
            "chestplate", Material.NETHERITE_CHESTPLATE,
            "leggings", Material.NETHERITE_LEGGINGS,
            "boots", Material.NETHERITE_BOOTS
        ),
        "CHAINMAIL", Map.of(
            "helmet", Material.CHAINMAIL_HELMET,
            "chestplate", Material.CHAINMAIL_CHESTPLATE,
            "leggings", Material.CHAINMAIL_LEGGINGS,
            "boots", Material.CHAINMAIL_BOOTS
        ),
        "GOLD", Map.of(
            "helmet", Material.GOLDEN_HELMET,
            "chestplate", Material.GOLDEN_CHESTPLATE,
            "leggings", Material.GOLDEN_LEGGINGS,
            "boots", Material.GOLDEN_BOOTS
        )
    );

    private final String setId;
    private final String displayNamePrefix;
    private final String nameColor;
    private final String colorHex;
    private final String materialType;
    private final int customModelDataBase;
    private final boolean enchantGlow;
    private final int manaBonus;
    private final int manaRegen;
    private final int hitManaRecovery;
    private final int damageManaRecovery;
    private final int threadSlots;
    private final int durability;
    private final boolean enchantable;
    private final Map<String, Integer> defense;
    private final double toughness;
    private final List<String> loreLines;
    private final Map<String, RecipeDefinition> recipes;

    public ArmorSetConfig(
            String setId, String displayNamePrefix, String nameColor,
            String colorHex, String materialType, int customModelDataBase,
            boolean enchantGlow,
            int manaBonus, int manaRegen, int hitManaRecovery, int damageManaRecovery,
            int threadSlots, int durability, boolean enchantable,
            Map<String, Integer> defense, double toughness,
            List<String> loreLines, Map<String, RecipeDefinition> recipes) {
        this.setId = setId;
        this.displayNamePrefix = displayNamePrefix;
        this.nameColor = nameColor;
        this.colorHex = colorHex;
        this.materialType = materialType;
        this.customModelDataBase = customModelDataBase;
        this.enchantGlow = enchantGlow;
        this.manaBonus = manaBonus;
        this.manaRegen = manaRegen;
        this.hitManaRecovery = hitManaRecovery;
        this.damageManaRecovery = damageManaRecovery;
        this.threadSlots = threadSlots;
        this.durability = durability;
        this.enchantable = enchantable;
        this.defense = defense;
        this.toughness = toughness;
        this.loreLines = loreLines;
        this.recipes = recipes;
    }

    public String getSetId() { return setId; }
    public String getDisplayNamePrefix() { return displayNamePrefix; }
    public String getNameColor() { return nameColor; }
    public String getMaterialType() { return materialType; }
    public int getCustomModelDataBase() { return customModelDataBase; }
    public boolean hasEnchantGlow() { return enchantGlow; }
    public int getManaBonus() { return manaBonus; }
    public int getManaRegen() { return manaRegen; }
    public int getHitManaRecovery() { return hitManaRecovery; }
    public int getDamageManaRecovery() { return damageManaRecovery; }
    public int getThreadSlots() { return threadSlots; }
    public int getDurability() { return durability; }
    public boolean isEnchantable() { return enchantable; }
    public Map<String, Integer> getDefense() { return defense; }
    public double getToughness() { return toughness; }
    public List<String> getLoreLines() { return loreLines; }
    public Map<String, RecipeDefinition> getRecipes() { return recipes; }

    /** セットID + スロット名からカスタムアイテムIDを生成 */
    public String getItemId(String slot) {
        return setId + "_" + slot;
    }

    /** スロット別のCustomModelDataを返す */
    public int getCustomModelData(String slot) {
        return customModelDataBase + slotOffset(slot);
    }

    /** スロット別のMaterialを返す */
    public Material getMaterialForSlot(String slot) {
        Map<String, Material> mats = MATERIAL_MAP.get(materialType.toUpperCase());
        if (mats != null) {
            Material mat = mats.get(slot);
            if (mat != null) return mat;
        }
        return SLOT_MATERIALS.getOrDefault(slot, Material.LEATHER_HELMET);
    }

    /** 革防具の色をBukkit Colorに変換 */
    public Color getBukkitColor() {
        if (colorHex == null || colorHex.isEmpty()) return null;
        String hex = colorHex.startsWith("#") ? colorHex.substring(1) : colorHex;
        try {
            int rgb = Integer.parseInt(hex, 16);
            return Color.fromRGB(rgb);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 名前色をTextColorに変換 */
    public TextColor getTextColor() {
        if (nameColor == null || nameColor.isEmpty()) return NamedTextColor.WHITE;
        String code = nameColor.replace("&", "").replace("§", "");
        return switch (code) {
            case "0" -> NamedTextColor.BLACK;
            case "1" -> NamedTextColor.DARK_BLUE;
            case "2" -> NamedTextColor.DARK_GREEN;
            case "3" -> NamedTextColor.DARK_AQUA;
            case "4" -> NamedTextColor.DARK_RED;
            case "5" -> NamedTextColor.DARK_PURPLE;
            case "6" -> NamedTextColor.GOLD;
            case "7" -> NamedTextColor.GRAY;
            case "8" -> NamedTextColor.DARK_GRAY;
            case "9" -> NamedTextColor.BLUE;
            case "a" -> NamedTextColor.GREEN;
            case "b" -> NamedTextColor.AQUA;
            case "c" -> NamedTextColor.RED;
            case "d" -> NamedTextColor.LIGHT_PURPLE;
            case "e" -> NamedTextColor.YELLOW;
            case "f" -> NamedTextColor.WHITE;
            default -> {
                // hex color: #RRGGBB
                if (nameColor.startsWith("#")) {
                    yield TextColor.fromHexString(nameColor);
                }
                yield NamedTextColor.WHITE;
            }
        };
    }

    /** スロット別の防御値を返す */
    public int getDefenseForSlot(String slot) {
        return defense.getOrDefault(slot, 0);
    }

    /** 全スロット名を返す */
    public static String[] getSlotNames() { return SLOT_NAMES; }

    /** 革素材かどうか */
    public boolean isLeather() {
        return "LEATHER".equalsIgnoreCase(materialType);
    }

    private static int slotOffset(String slot) {
        return switch (slot) {
            case "helmet" -> 0;
            case "chestplate" -> 1;
            case "leggings" -> 2;
            case "boots" -> 3;
            default -> 0;
        };
    }
}
