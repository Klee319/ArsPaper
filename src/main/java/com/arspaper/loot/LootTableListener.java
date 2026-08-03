package com.arspaper.loot;

import com.arspaper.enchant.ArsEnchantments;
import com.arspaper.item.ItemCostRef;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 構造物のルートチェストへ追加抽選を差し込む。対象と中身は {@code loot-tables.yml}（{@link LootTableConfig}）。
 *
 * <p>チェストを漁る動機を「厳選スレッド」に置いている。{@code custom:thread_*} を指定すると
 * {@code ThreadItem.createItemStack()} が個体差を振るので、このクラス側に厳選のコードは要らない。
 */
public class LootTableListener implements Listener {

    /**
     * 1回の生成で追加できるスタック数の上限。yml の桁を間違えても
     * バニラのルートを押し出してチェストから溢れさせないための保険。
     */
    private static final int MAX_ADDED_PER_EVENT = 12;

    private static final String[] ENCHANT_IDS = {"mana_regen", "mana_boost", "share"};

    private final JavaPlugin plugin;
    private final LootTableConfig config;
    /** 解決できなかった item の警告を1回だけ出すための記録。毎チェストでログが溢れるのを防ぐ。 */
    private final Set<String> warnedItems = new HashSet<>();
    private boolean wardenEchoShard;
    private int wardenEchoShardMin;
    private int wardenEchoShardMax;

    public LootTableListener(JavaPlugin plugin) {
        this.plugin = plugin;
        // LootTableConfig のコンストラクタが読み込むので、ここで reload は呼ばない(二重ロードになる)。
        this.config = new LootTableConfig(plugin);
        loadWardenSettings();
    }

    public void reloadConfig() {
        config.reload();
        warnedItems.clear();
        loadWardenSettings();
    }

    private void loadWardenSettings() {
        wardenEchoShard = plugin.getConfig().getBoolean("mob-drops.warden-echo-shard", true);
        wardenEchoShardMin = plugin.getConfig().getInt("mob-drops.warden-echo-shard-min", 1);
        wardenEchoShardMax = plugin.getConfig().getInt("mob-drops.warden-echo-shard-max", 3);
    }

    public LootTableConfig getConfig() {
        return config;
    }

    /**
     * ウォーデン討伐時に残響の欠片をドロップする。
     * ドロップ増加（Looting）エンチャントで個数が増加する。
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!wardenEchoShard) return;
        if (!(event.getEntity() instanceof Warden warden)) return;

        int base = ThreadLocalRandom.current().nextInt(wardenEchoShardMin, wardenEchoShardMax + 1);

        // ドロップ増加エンチャント（Looting）のレベル分を加算
        var killer = warden.getKiller();
        if (killer != null) {
            var weapon = killer.getInventory().getItemInMainHand();
            int lootingLevel = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOTING);
            if (lootingLevel > 0) {
                base += ThreadLocalRandom.current().nextInt(lootingLevel + 1);
            }
        }

        if (base > 0) {
            event.getDrops().add(new ItemStack(Material.ECHO_SHARD, base));
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (!config.isEnabled()) return;
        if (event.getLootTable() == null) return;

        // getKey().getKey() はパスだけ(namespace が落ちる)なので、namespace を保った文字列で判定する。
        // データパックの構造物は minecraft 以外の namespace を使うため、ここが落ちると当たらない。
        String tableKey = event.getLootTable().getKey().toString();
        List<LootTableConfig.Pool> pools = config.poolsFor(tableKey);
        if (pools.isEmpty()) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int added = 0;
        for (LootTableConfig.Pool pool : pools) {
            for (int roll = 0; roll < pool.rolls(); roll++) {
                for (LootTableConfig.Entry entry : pool.entries()) {
                    if (added >= MAX_ADDED_PER_EVENT) return;
                    if (random.nextDouble() >= entry.chance()) continue;
                    ItemStack stack = createStack(entry, random);
                    if (stack == null) continue;
                    event.getLoot().add(stack);
                    added++;
                }
            }
        }
    }

    /** 抽選に当たった候補を実体化する。解決できなければ null（ログは item ごとに1回だけ）。 */
    private ItemStack createStack(LootTableConfig.Entry entry, ThreadLocalRandom random) {
        if (entry.enchantBook()) {
            return createRandomEnchantBook(random);
        }
        int amount = entry.min() >= entry.max() ? entry.min() : random.nextInt(entry.min(), entry.max() + 1);
        ItemCostRef ref;
        try {
            ref = ItemCostRef.parse(entry.item());
        } catch (IllegalArgumentException malformed) {
            warnOnce(entry.item(), "アイテム指定が壊れています: " + malformed.getMessage());
            return null;
        }
        // TrinityForge の items/catalog.yml で draft: true(準備中)と宣言されたIDは配らない。
        // Ars は自前のレジストリから実体を作れてしまうので、TF 側の CrossPluginItemResolver の
        // draft ゲートを一度も通らない ―― この経路だけが「準備中なのに構造物チェストから出る」
        // 抜け穴になっていた(2026-08-03)。判定は TF の1箇所(ItemCatalogConfig#isDraft)に委ねる。
        if (ref.custom() && com.arspaper.integration.TrinityForgeBridge.isCatalogDraft(ref.id())) {
            return null;
        }
        // ItemCostRef#createStack は未登録のカスタムIDに対して PAPER を返す仕様なので、
        // ここで存在を確かめる。確かめないと「チェストから紙が出る」だけで原因が分からない。
        if (ref.custom() && !isKnownCustom(ref.id())) {
            warnOnce(entry.item(), "カスタムアイテムが未登録のためスキップします");
            return null;
        }
        ItemStack stack = ref.createStack(amount);
        if (stack.getType().isAir()) {
            warnOnce(entry.item(), "Material として解決できないためスキップします");
            return null;
        }
        return stack;
    }

    private boolean isKnownCustom(String id) {
        com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
        if (ars != null && ars.getItemRegistry() != null && ars.getItemRegistry().has(id)) {
            return true;
        }
        return com.arspaper.integration.TrinityForgeBridge.createCatalogIdentity(id) != null;
    }

    private void warnOnce(String item, String message) {
        if (warnedItems.add(item)) {
            plugin.getLogger().warning("[" + LootTableConfig.FILE_NAME + "] " + item + ": " + message);
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
