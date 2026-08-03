package com.arspaper.item.impl;

import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.MaterialConfig;
import net.kyori.adventure.text.Component;
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
        // 書式解釈は DisplayText 1本へ(materials.yml はレガシー &記法だが、
        // MiniMessage で書かれても壊れないようにしておく)。
        return com.arspaper.util.DisplayText.component(config.nameColor() + config.displayName());
    }

    @Override
    public int getCustomModelData() {
        return config.customModelData();
    }

    /** エンチャント光沢(キラキラ)の付与可否。materials.yml の enchant_glow で制御(既定 true)。 */
    @Override
    public boolean hasEnchantGlow() {
        return config.enchantGlow();
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        if (!config.lore().isEmpty()) {
            item.editMeta(meta -> meta.lore(
                config.lore().stream()
                    .map(com.arspaper.util.DisplayText::component)
                    .collect(Collectors.toList())
            ));
        }
        return item;
    }
}
