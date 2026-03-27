package com.arspaper.ritual.effect;

import com.arspaper.enchant.ArsEnchantments;
import com.arspaper.item.ItemKeys;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * エンチャント本クラフトの儀式 - コアに置いた本をカスタムエンチャント本に変換する。
 * Paper Registry APIで登録されたエンチャントを使用し、EnchantmentStorageMetaに格納する。
 */
public class EnchantBookRitualEffect implements RitualEffect {

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        String enchantId = recipe.effectParams().get("enchantment");
        String levelStr = recipe.effectParams().getOrDefault("level", "1");

        if (enchantId == null) {
            player.sendMessage(Component.text("エンチャントタイプが指定されていません！", NamedTextColor.RED));
            return;
        }

        Enchantment enchant = ArsEnchantments.getFromId(enchantId);
        if (enchant == null) {
            player.sendMessage(Component.text("不明なエンチャント: " + enchantId, NamedTextColor.RED));
            return;
        }

        int parsedLevel;
        try {
            parsedLevel = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            parsedLevel = 1;
        }
        final int level = Math.min(parsedLevel, ArsEnchantments.MAX_LEVEL);

        String displayName = ArsEnchantments.getDisplayName(enchantId);
        String roman = ArsEnchantments.toRoman(level);

        // Bukkit APIベースのエンチャント本を生成
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
        enchantedBook.editMeta(meta -> {
            meta.displayName(Component.text(displayName + " " + roman, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(
                ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING, "enchant_book"
            );

            // EnchantmentStorageMetaにエンチャントを格納
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchant, level, true);
            }

            meta.lore(List.of(
                Component.text(displayName + " " + roman, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("金床でメイジアーマーに適用", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        });

        // コアアイテムはRitualManagerが既に消費済み
        coreLocation.getWorld().dropItemNaturally(
            coreLocation.clone().add(0.5, 1.5, 0.5), enchantedBook
        );

        // エフェクト
        Location effectLoc = coreLocation.clone().add(0.5, 1.5, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 100, 0.5, 1, 0.5, 2.0);
        coreLocation.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 30, 0.3, 0.5, 0.3, 0.1);
        coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

        player.sendMessage(Component.text(
            displayName + " " + roman + " のエンチャント本を精製しました！", NamedTextColor.GREEN
        ));
    }
}
