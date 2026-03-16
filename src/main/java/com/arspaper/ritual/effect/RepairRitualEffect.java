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
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) return;

        PersistentDataContainer corePdc = tileState.getPersistentDataContainer();
        String materialName = corePdc.get(
            new org.bukkit.NamespacedKey("arspaper", "core_item"), PersistentDataType.STRING
        );
        String customId = corePdc.get(
            new org.bukkit.NamespacedKey("arspaper", "core_custom_id"), PersistentDataType.STRING
        );

        if (materialName == null) {
            player.sendMessage(Component.text("コアにアイテムを置いてください！", NamedTextColor.RED));
            return;
        }

        // アイテムを復元
        ItemStack item;
        if (customId != null) {
            item = com.arspaper.ArsPaper.getInstance().getItemRegistry()
                .get(customId)
                .map(ci -> ci.createItemStack())
                .orElse(null);
        } else {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(materialName);
            if (mat == null) {
                player.sendMessage(Component.text("アイテムの復元に失敗しました！", NamedTextColor.RED));
                return;
            }
            item = new ItemStack(mat);
        }

        if (item == null) {
            player.sendMessage(Component.text("アイテムの復元に失敗しました！", NamedTextColor.RED));
            return;
        }

        // 耐久値を回復
        if (!(item.getItemMeta() instanceof Damageable)) {
            player.sendMessage(Component.text("このアイテムには耐久値がありません！", NamedTextColor.RED));
            return;
        }

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
