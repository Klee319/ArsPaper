package com.arspaper.ritual.effect;

import com.arspaper.block.impl.RitualCore;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 修復の儀式 - コアに置いたアイテムの耐久値を全回復する。
 */
public class RepairRitualEffect implements RitualEffect {

    @Override
    public boolean validate(Location coreLocation, Player player, RitualRecipe recipe) {
        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) return false;

        // コアにアイテムがあり、かつ耐久値を持つアイテムであることを検証
        byte[] serialized = tileState.getPersistentDataContainer()
            .get(new org.bukkit.NamespacedKey("arspaper", "core_item_data"), PersistentDataType.BYTE_ARRAY);
        if (serialized == null) {
            player.sendMessage(Component.text("コアに修復対象のアイテムを置いてください！", NamedTextColor.RED));
            return false;
        }

        ItemStack item = ItemStack.deserializeBytes(serialized);
        if (!(item.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            player.sendMessage(Component.text("このアイテムは修復の必要がありません！", NamedTextColor.RED));
            return false;
        }

        return true;
    }

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) return;

        // コアからシリアライズ済みアイテムを復元
        byte[] serialized = tileState.getPersistentDataContainer()
            .get(new org.bukkit.NamespacedKey("arspaper", "core_item_data"), PersistentDataType.BYTE_ARRAY);
        if (serialized == null) {
            player.sendMessage(Component.text("コアにアイテムを置いてください！", NamedTextColor.RED));
            return;
        }

        ItemStack item = ItemStack.deserializeBytes(serialized);

        // 耐久値を回復
        item.editMeta(Damageable.class, damageable -> damageable.setDamage(0));

        // コアをクリアして修復済みアイテムを返却
        RitualCore.clearCoreItem(tileState);
        coreLocation.getWorld().dropItemNaturally(
            coreLocation.clone().add(0.5, 1.5, 0.5), item
        );

        // エフェクト
        Location effectLoc = coreLocation.clone().add(0.5, 1.5, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 80, 0.5, 0.5, 0.5, 1.0);
        coreLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLoc, 30, 0.5, 0.5, 0.5, 0);
        coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);

        player.sendMessage(Component.text(
            "アイテムを完全に修復しました！", NamedTextColor.GREEN
        ));
    }
}
