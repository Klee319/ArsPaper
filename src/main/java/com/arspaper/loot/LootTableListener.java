package com.arspaper.loot;

import com.arspaper.enchant.ArsEnchantments;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * バニラのルートチェストにカスタムエンチャント本とエンチャント金リンゴを低確率で追加する。
 * 対象はホワイトリストで制限し、村やトライアルチャンバー等の大量チェストは除外。
 */
public class LootTableListener implements Listener {

    private static final Set<String> ALLOWED_LOOT_TABLES = Set.of(
        "abandoned_mineshaft", "desert_pyramid", "jungle_temple",
        "simple_dungeon", "stronghold_corridor", "stronghold_crossing",
        "stronghold_library", "woodland_mansion", "end_city_treasure",
        "bastion_treasure", "bastion_other", "bastion_hoglin_stable",
        "bastion_bridge", "ancient_city", "buried_treasure"
    );

    private static final String[] ENCHANT_IDS = {"mana_regen", "mana_boost", "share"};

    private final JavaPlugin plugin;
    private boolean enabled;
    private double enchantBookChance;
    private double enchantedGoldenAppleChance;

    public LootTableListener(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        enabled = plugin.getConfig().getBoolean("loot.enabled", true);
        enchantBookChance = plugin.getConfig().getDouble("loot.enchant-book-chance", 0.05);
        enchantedGoldenAppleChance = plugin.getConfig().getDouble("loot.enchanted-golden-apple-chance", 0.02);
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (!enabled) return;
        if (event.getLootTable() == null) return;

        String key = event.getLootTable().getKey().getKey();
        String tableName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        if (!ALLOWED_LOOT_TABLES.contains(tableName)) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (random.nextDouble() < enchantBookChance) {
            event.getLoot().add(createRandomEnchantBook(random));
        }

        if (random.nextDouble() < enchantedGoldenAppleChance) {
            event.getLoot().add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        }
    }

    private ItemStack createRandomEnchantBook(ThreadLocalRandom random) {
        String enchantId = ENCHANT_IDS[random.nextInt(ENCHANT_IDS.length)];

        Enchantment enchant = ArsEnchantments.getFromId(enchantId);
        int maxLevel = enchant.getMaxLevel();
        int level = maxLevel <= 1 ? 1 : random.nextInt(1, maxLevel + 1);

        String displayName = ArsEnchantments.getDisplayName(enchantId);
        String roman = ArsEnchantments.toRoman(level);

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        book.editMeta(meta -> {
            meta.displayName(Component.text(displayName + " " + roman, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(
                ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING, "enchant_book"
            );

            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchant, level, true);
            }

            meta.lore(List.of(
                Component.empty(),
                Component.text("金床でメイジアーマーに適用", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        });

        return book;
    }
}
