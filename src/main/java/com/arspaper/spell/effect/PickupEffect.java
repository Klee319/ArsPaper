package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;

/**
 * 周囲のアイテムを回収するEffect。
 * 対象地点の 2 + aoeLevel ブロック範囲内のドロップアイテムをキャスターのインベントリに追加する。
 * インベントリが満杯の場合は元の場所にドロップしたまま残す。
 */
public class PickupEffect implements SpellEffect {

    private static final double BASE_RADIUS = 2.0;

    private final NamespacedKey id;
    private final GlyphConfig config;

    public PickupEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "pickup");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティ対象の場合は足元を中心として回収
        collectItems(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        collectItems(context, blockLocation);
    }

    private void collectItems(SpellContext context, Location center) {
        Player caster = context.getCaster();
        if (caster == null) return;

        double radius = BASE_RADIUS + context.getAoeRadiusLevel();
        Collection<Item> items = center.getWorld().getNearbyEntitiesByType(
            Item.class, center, radius
        );

        for (Item item : items) {
            ItemStack stack = item.getItemStack();
            HashMap<Integer, ItemStack> leftover = caster.getInventory().addItem(stack);

            if (leftover.isEmpty()) {
                // 全て回収できた
                item.remove();
            } else {
                // 一部しか入らなかった場合は残量をアイテムに戻す
                ItemStack remaining = leftover.get(0);
                if (remaining != null) {
                    item.setItemStack(remaining);
                }
            }
        }

        if (!items.isEmpty()) {
            // 回収エフェクト
            center.getWorld().spawnParticle(
                org.bukkit.Particle.HAPPY_VILLAGER, center, 8, 0.5, 0.5, 0.5, 0
            );
            center.getWorld().playSound(center,
                org.bukkit.Sound.ENTITY_ITEM_PICKUP,
                org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "回収"; }

    @Override
    public String getDescription() { return "周囲のアイテムを回収する"; }

    @Override
    public int getManaCost() { return config.getManaCost("pickup"); }

    @Override
    public int getTier() { return config.getTier("pickup"); }
}
