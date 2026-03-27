package com.arspaper.enchant;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * ArsPaperのブートストラップ。サーバー起動時にカスタムエンチャントを登録する。
 * Registry freezeより前に実行されるため、Bukkit Enchantment APIで正式に扱える。
 */
@SuppressWarnings("UnstableApiUsage")
public class ArsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
            RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
                event.registry().register(
                    TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("arspaper", "mana_regen")),
                    b -> b.description(Component.text("マナ再生"))
                        .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_ARMOR))
                        .weight(1)        // エンチャント台には出ない（weight最小）
                        .maxLevel(3)
                        .anvilCost(1)
                        .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(100, 0))
                        .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(200, 0))
                        .activeSlots(EquipmentSlotGroup.ARMOR)
                );

                event.registry().register(
                    TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("arspaper", "mana_boost")),
                    b -> b.description(Component.text("マナ上昇"))
                        .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_ARMOR))
                        .weight(1)
                        .maxLevel(3)
                        .anvilCost(1)
                        .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(100, 0))
                        .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(200, 0))
                        .activeSlots(EquipmentSlotGroup.ARMOR)
                );

                event.registry().register(
                    TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("arspaper", "share")),
                    b -> b.description(Component.text("共有"))
                        .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_VANISHING))
                        .weight(1)
                        .maxLevel(1)
                        .anvilCost(1)
                        .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(100, 0))
                        .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(200, 0))
                        .activeSlots(EquipmentSlotGroup.ANY)
                );
            })
        );
    }
}
