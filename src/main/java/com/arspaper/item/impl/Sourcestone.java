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
 * ソースストーン - 石にSourceを注入して作成した魔力石材。
 * 建築・インフラ素材として使用。
 */
public class Sourcestone extends BaseCustomItem {

    public Sourcestone(JavaPlugin plugin) {
        super(plugin, "sourcestone");
    }

    @Override
    public Material getBaseMaterial() {
        return Material.SMOOTH_STONE;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("ソースストーン", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 100013;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("魔力を帯びた石材", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }
}
