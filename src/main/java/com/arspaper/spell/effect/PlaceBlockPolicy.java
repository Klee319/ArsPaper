package com.arspaper.spell.effect;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Set;

/**
 * 設置魔法が「別プラグインのカスタムコンテナ」をバニラの空樽にしないための判定。
 *
 * <p>2026-08-29 実サーバ報告: ドロワー（基材 BARREL）を {@code Block#setType} すると
 * TileEntity が消えて中身が失われる。Ars はドロワーの NBT を復元できないので、
 * 他プラグイン名前空間の PDC を持つコンテナは設置自体を拒否する。
 */
public final class PlaceBlockPolicy {

    private static final Set<String> OWNED_NAMESPACES = Set.of(
            "minecraft", "bukkit", "paper", "spigot", "arspaper", "trinityforge");

    private PlaceBlockPolicy() {
    }

    public static boolean isContainerMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return material == Material.BARREL
                || material == Material.CHEST
                || material == Material.TRAPPED_CHEST
                || material == Material.HOPPER
                || material == Material.DROPPER
                || material == Material.DISPENSER
                || material == Material.FURNACE
                || material == Material.BLAST_FURNACE
                || material == Material.SMOKER
                || material == Material.BREWING_STAND
                || name.endsWith("SHULKER_BOX");
    }

    public static boolean hasForeignPluginIdentity(Iterable<String> namespaces) {
        if (namespaces == null) {
            return false;
        }
        for (String namespace : namespaces) {
            if (namespace == null || namespace.isBlank()) {
                continue;
            }
            if (!OWNED_NAMESPACES.contains(namespace.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean refuseVanillaPlaceholder(Material material, Iterable<String> namespaces) {
        return isContainerMaterial(material) && hasForeignPluginIdentity(namespaces);
    }
}
