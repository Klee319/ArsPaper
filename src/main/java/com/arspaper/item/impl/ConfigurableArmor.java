package com.arspaper.item.impl;

import com.arspaper.ArsPaper;
import com.arspaper.gui.ThreadGui;
import com.arspaper.item.ArmorSetConfig;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * armors.ymlから定義される設定ベースの防具アイテム。
 * BaseCustomItemを拡張し、ArmorSetConfigのデータに基づいてアイテムを生成する。
 */
public class ConfigurableArmor extends BaseCustomItem {

    private final ArmorSetConfig setConfig;
    private final String slotName;

    public ConfigurableArmor(JavaPlugin plugin, ArmorSetConfig setConfig, String slotName) {
        super(plugin, setConfig.getItemId(slotName));
        this.setConfig = setConfig;
        this.slotName = slotName;
    }

    @Override
    public Material getBaseMaterial() {
        return setConfig.getMaterialForSlot(slotName);
    }

    @Override
    public Component getDisplayName() {
        String localizedSlot = localizeSlot(slotName);
        return Component.text(setConfig.getDisplayNamePrefix() + localizedSlot, setConfig.getTextColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return setConfig.getCustomModelData(slotName);
    }

    @Override
    public boolean hasEnchantGlow() {
        return setConfig.hasEnchantGlow();
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta -> {
            // PDC: 防具セットID
            meta.getPersistentDataContainer().set(
                ItemKeys.ARMOR_SET_ID, PersistentDataType.STRING, setConfig.getSetId());

            // 革防具の色
            if (setConfig.isLeather() && meta instanceof LeatherArmorMeta leatherMeta) {
                Color color = setConfig.getBukkitColor();
                if (color != null) {
                    leatherMeta.setColor(color);
                }
            }

            // Lore
            List<Component> lore = new ArrayList<>();
            for (String line : setConfig.getLoreLines()) {
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (setConfig.getThreadSlots() > 0) {
                lore.add(Component.text("スニーク+右クリックでスレッド設定",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            // 防御力属性
            int defense = setConfig.getDefenseForSlot(slotName);
            if (defense > 0) {
                EquipmentSlotGroup slotGroup = getEquipmentSlotGroup();
                meta.addAttributeModifier(Attribute.ARMOR,
                    new AttributeModifier(
                        new org.bukkit.NamespacedKey("arspaper", setConfig.getSetId() + "_" + slotName + "_armor"),
                        defense,
                        AttributeModifier.Operation.ADD_NUMBER,
                        slotGroup
                    ));
            }

            // 防具強度
            if (setConfig.getToughness() > 0) {
                EquipmentSlotGroup slotGroup = getEquipmentSlotGroup();
                meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                    new AttributeModifier(
                        new org.bukkit.NamespacedKey("arspaper", setConfig.getSetId() + "_" + slotName + "_toughness"),
                        setConfig.getToughness(),
                        AttributeModifier.Operation.ADD_NUMBER,
                        slotGroup
                    ));
            }

            // エンチャント不可の場合
            if (!setConfig.isEnchantable()) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });

        // 耐久値オーバーライド
        if (setConfig.getDurability() > 0 && item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            item.editMeta(meta -> {
                if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    damageable.setMaxDamage(setConfig.getDurability());
                }
            });
        }

        return item;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (setConfig.getThreadSlots() <= 0) return;

        event.setCancelled(true);
        ItemStack item = event.getItem();
        if (item == null) return;

        ThreadGui gui = new ThreadGui(player, item, plugin);
        gui.open();
    }

    public ArmorSetConfig getSetConfig() { return setConfig; }
    public String getSlotName() { return slotName; }

    private EquipmentSlotGroup getEquipmentSlotGroup() {
        return switch (slotName) {
            case "helmet" -> EquipmentSlotGroup.HEAD;
            case "chestplate" -> EquipmentSlotGroup.CHEST;
            case "leggings" -> EquipmentSlotGroup.LEGS;
            case "boots" -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.ARMOR;
        };
    }

    private static String localizeSlot(String slot) {
        return switch (slot) {
            case "helmet" -> "ヘルメット";
            case "chestplate" -> "チェストプレート";
            case "leggings" -> "レギンス";
            case "boots" -> "ブーツ";
            default -> slot;
        };
    }
}
