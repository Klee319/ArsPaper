package com.arspaper.item.impl;

import com.arspaper.gui.ThreadGui;
import com.arspaper.item.ArmorSlot;
import com.arspaper.item.ArmorTier;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * メイジアーマー。ティアごとにマナボーナスが異なる革防具。
 * 装備時のマナボーナスはArmorManaListenerで管理。
 */
public class MageArmor extends BaseCustomItem {

    private final ArmorTier armorTier;
    private final ArmorSlot armorSlot;

    public MageArmor(JavaPlugin plugin, ArmorTier armorTier, ArmorSlot armorSlot) {
        super(plugin, buildItemId(armorTier, armorSlot));
        this.armorTier = armorTier;
        this.armorSlot = armorSlot;
    }

    private static String buildItemId(ArmorTier tier, ArmorSlot slot) {
        return "mage_" + tier.name().toLowerCase() + "_" + slot.getId();
    }

    @Override
    public Material getBaseMaterial() {
        return armorSlot.getMaterial();
    }

    @Override
    public Component getDisplayName() {
        return Component.text(armorTier.getDisplayName() + "メイジ" + localizeSlot(armorSlot),
                armorTier.getColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return armorSlot.getCustomModelData(armorTier.getTier());
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                ItemKeys.ARMOR_TIER, PersistentDataType.INTEGER, armorTier.getTier()
            );
            // 革防具の色をティアに応じて変更
            if (meta instanceof LeatherArmorMeta leatherMeta) {
                leatherMeta.setColor(getTierColor());
            }
            meta.lore(List.of(
                Component.text("ティア: " + armorTier.getDisplayName(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("マナボーナス: +" + armorTier.getManaBonus(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("スレッドスロット: " + armorTier.getThreadSlots(), NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        });
        return item;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            ItemStack held = event.getItem();
            if (held == null) return;
            new ThreadGui(event.getPlayer(), held).open();
        }
    }

    public ArmorTier getArmorTier() {
        return armorTier;
    }

    public ArmorSlot getArmorSlot() {
        return armorSlot;
    }

    private Color getTierColor() {
        return switch (armorTier) {
            case NOVICE -> Color.fromRGB(150, 100, 200);
            case APPRENTICE -> Color.fromRGB(100, 50, 180);
            case ARCHMAGE -> Color.fromRGB(255, 200, 50);
        };
    }

    private static String localizeSlot(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> "ヘルメット";
            case CHESTPLATE -> "チェストプレート";
            case LEGGINGS -> "レギンス";
            case BOOTS -> "ブーツ";
        };
    }
}
