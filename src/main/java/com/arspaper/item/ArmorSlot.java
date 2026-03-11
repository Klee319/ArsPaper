package com.arspaper.item;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 防具スロット定義。バニラマテリアルとの対応。
 */
public enum ArmorSlot {

    HELMET("helmet", Material.LEATHER_HELMET, EquipmentSlot.HEAD, 200001),
    CHESTPLATE("chestplate", Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST, 200002),
    LEGGINGS("leggings", Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS, 200003),
    BOOTS("boots", Material.LEATHER_BOOTS, EquipmentSlot.FEET, 200004);

    private final String id;
    private final Material material;
    private final EquipmentSlot equipmentSlot;
    private final int baseCustomModelData;

    ArmorSlot(String id, Material material, EquipmentSlot equipmentSlot, int baseCustomModelData) {
        this.id = id;
        this.material = material;
        this.equipmentSlot = equipmentSlot;
        this.baseCustomModelData = baseCustomModelData;
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public EquipmentSlot getEquipmentSlot() { return equipmentSlot; }

    /** CustomModelData = base + (tier - 1) * 10 */
    public int getCustomModelData(int tier) {
        return baseCustomModelData + (tier - 1) * 10;
    }
}
