package com.arspaper.item.impl;

import com.arspaper.item.BaseCustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * マジックファイバー - Source Gemから繊維を精製。
 * アーマー素材として使用。
 */
public class MagebloomFiber extends BaseCustomItem {

    public MagebloomFiber(JavaPlugin plugin) {
        super(plugin, "magebloom_fiber");
    }

    @Override
    public Material getBaseMaterial() {
        return Material.STRING;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("マジックファイバー", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 100012;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("魔力を帯びた繊維", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }
}
