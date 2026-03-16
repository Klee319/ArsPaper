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
 * ソースベリー - グロウベリーベースのカスタムアイテム。
 * 食べるとマナを25回復する。
 * 儀式でグロウベリー + アメジストから作成。
 */
public class SourceBerry extends BaseCustomItem {

    public static final int MANA_RESTORE = 25;

    public SourceBerry(JavaPlugin plugin) {
        super(plugin, "source_berry");
    }

    @Override
    public Material getBaseMaterial() {
        return Material.GLOW_BERRIES;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("ソースベリー", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 100010;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("魔力を帯びた不思議なベリー", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("食べるとマナを" + MANA_RESTORE + "回復", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }
}
