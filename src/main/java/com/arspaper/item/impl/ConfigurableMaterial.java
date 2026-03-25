package com.arspaper.item.impl;

import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.MaterialConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * materials.ymlから定義されたカスタム中間素材アイテム。
 * ハードコードされたSourceGem, MagebloomFiber, Sourcestone を置き換える。
 */
public class ConfigurableMaterial extends BaseCustomItem {

    private final MaterialConfig config;

    public ConfigurableMaterial(JavaPlugin plugin, MaterialConfig config) {
        super(plugin, config.id());
        this.config = config;
    }

    @Override
    public Material getBaseMaterial() {
        return config.baseMaterial();
    }

    @Override
    public Component getDisplayName() {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize(config.nameColor() + config.displayName())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return config.customModelData();
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        if (!config.lore().isEmpty()) {
            item.editMeta(meta -> meta.lore(
                config.lore().stream()
                    .map(line -> LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(line)
                        .decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList())
            ));
        }
        return item;
    }
}
