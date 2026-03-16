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
 * ソースジェム - 本家Ars NouveauのSource Gemに対応。
 * 全レシピの中核素材。アメジストにSourceを注入して作成。
 */
public class SourceGem extends BaseCustomItem {

    public SourceGem(JavaPlugin plugin) {
        super(plugin, "source_gem");
    }

    @Override
    public Material getBaseMaterial() {
        return Material.PRISMARINE_SHARD;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("ソースジェム", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 100011;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("魔力を帯びた結晶", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }
}
