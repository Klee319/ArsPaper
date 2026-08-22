package com.arspaper.loot;

import com.arspaper.enchant.ArsEnchantments;
import com.arspaper.item.ItemCostRef;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 構造物のルートチェストへ追加抽選を差し込む。対象と中身は {@code loot-tables.yml}（{@link LootTableConfig}）。
 *
 * <p>チェストを漁る動機を「厳選スレッド」に置いている。{@code custom:thread_*} を指定すると
 * {@code ThreadItem.createItemStack()} が個体差を振るので、このクラス側に厳選のコードは要らない。
 *
 * <p><b>入口は2つある(2026-08-23、W-187)</b>:
 * <ul>
 *   <li>{@link #onLootGenerate} … 普通のチェスト・樽。{@code LootGenerateEvent}。</li>
 *   <li>{@link #onBlockDispenseLoot} … <b>ヴォールトと試練のスポナー</b>。
 *       これらは {@code LootGenerateEvent} を<b>発火しない</b>(PaperMC #11680 は
 *       「対応しない」でクローズ)ので、{@code BlockDispenseLootEvent} でしか捕まえられない。</li>
 * </ul>
 * どちらも {@link #applyPools} を通す。<b>片方だけ直すと「ヴォールトだけ空」に戻る。</b>
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

        applyPools(event.getLoot(), pools, ThreadLocalRandom.current());
    }

    /**
     * ヴォールトと試練のスポナーが吐く戦利品へ追加抽選を差し込む。
     *
     * <p><b>なぜ {@link LootGenerateEvent} では足りないのか</b>: ヴォールト(vault)と
     * 試練のスポナー(trial spawner)は戦利品を<b>ブロックが直接排出する</b>ので
     * {@code LootGenerateEvent} を発火しない(PaperMC issue #11680 は「対応しない」で
     * クローズ済み)。2026-08-23 のユーザー報告「試練のスポナーにカスタム登録のアイテムが
     * 反映されず、一部チェストが空」はこれが原因で、{@code loot-tables.yml} に表を足すだけでは
     * 永久に当たらない。Paper 1.21.10 で入った {@link BlockDispenseLootEvent} が唯一の入口。
     *
     * <p>試練の間の<b>普通のチェスト</b>({@code chests/trial_chambers/*})は従来どおり
     * {@code LootGenerateEvent} 側で処理される。両方に同じ処理を通すため、中身は
     * {@link #applyPools} に共通化してある ── 片方だけ直すと「ヴォールトだけ空」に戻る。
     *
     * <p>{@code getPlayer()} は試練のスポナーの報酬排出時に null になりうる(誰の報酬でもない)。
     * このクラスはプレイヤーを見ないので分岐は要らない。
     */
    @EventHandler
    public void onBlockDispenseLoot(BlockDispenseLootEvent event) {
        if (!config.isEnabled()) return;
        if (event.isCancelled()) return;
        if (event.getLootTable() == null) return;

        List<LootTableConfig.Pool> pools = config.poolsFor(event.getLootTable().getKey().toString());
        if (pools.isEmpty()) return;

        // getDispensedLoot() が返すリストの可変性は保証されていないので、
        // 必ず自前のリストへ写して setDispensedLoot で戻す。
        List<ItemStack> loot = new ArrayList<>(event.getDispensedLoot());
        applyPools(loot, pools, ThreadLocalRandom.current());
        event.setDispensedLoot(loot);
    }

    /**
     * 戦利品リストへプールを適用する({@code LootGenerateEvent} と
     * {@link BlockDispenseLootEvent} の共通処理)。
     *
     * <p>【順序が仕様】除去 → 増量 → 追加。
     * 除去を先にしないと、あとで足す自前のマナ系エンチャント本まで巻き込んで消える。
     * 増量を追加より先にしないと、足したばかりの厳選スレッドや鍵にまで倍率が掛かって
     * 「レア報酬が2個出る」ことになる(倍率はあくまで既定の戦利品の底上げ)。
     */
    private void applyPools(List<ItemStack> loot, List<LootTableConfig.Pool> pools,
                            ThreadLocalRandom random) {
        if (config.blocksDatapackEnchantBooks()) {
            loot.removeIf(LootTableListener::isDatapackEnchantBook);
        }
        double multiplier = maxQuantityMultiplier(pools);
        if (multiplier > 1.0) {
            for (ItemStack stack : loot) {
                if (stack == null || stack.getType().isAir()) continue;
                stack.setAmount(scaledAmount(stack.getAmount(), multiplier,
                        stack.getMaxStackSize(), random.nextDouble()));
            }
        }

        int added = 0;
        for (LootTableConfig.Pool pool : pools) {
            for (int roll = 0; roll < pool.rolls(); roll++) {
                for (LootTableConfig.Entry entry : pool.entries()) {
                    if (added >= MAX_ADDED_PER_EVENT) return;
                    if (random.nextDouble() >= entry.chance()) continue;
                    ItemStack stack = createStack(entry, random);
                    if (stack == null) continue;
                    loot.add(stack);
                    added++;
                }
            }
        }
    }

    /**
     * 複数プールが同じテーブルに当たったときの個数倍率。<b>掛け合わせず最大値を採る</b>。
     *
     * <p>掛け合わせると、あとからプールを1つ足しただけで既存の全チェストが黙って倍量になる
     * (1.5 × 2.0 = 3.0)。倍率は「この構造物の格付け」を表す値なので、重ねるより
     * 一番手厚い指定に従うほうが意図どおりになる。
     */
    static double maxQuantityMultiplier(List<LootTableConfig.Pool> pools) {
        double max = 1.0;
        for (LootTableConfig.Pool pool : pools) {
            if (pool.scalesQuantity()) {
                max = Math.max(max, pool.quantityMultiplier());
            }
        }
        return max;
    }

    /**
     * 個数へ倍率を掛けて整数化する。整数部は確定・端数はその確率で +1 する
     * (1.5 なら 50% で切り上げ)ので、期待値が倍率どおりになる。
     *
     * <p>四捨五入にすると 1 個のスタックが 1.5 倍で<b>常に</b>2 個になり、実効 2 倍に化ける。
     * 上限はスタック上限。チェストの枠は有限なので、これ以上増やしても溢れて消えるだけ。
     *
     * @param roll 0.0以上1.0未満の乱数。テストのために引数化している。
     */
    static int scaledAmount(int baseAmount, double multiplier, int maxStackSize, double roll) {
        double scaled = Math.max(0, baseAmount) * multiplier;
        int whole = (int) Math.floor(scaled);
        if (scaled - whole > 0.0 && roll < scaled - whole) {
            whole++;
        }
        return Math.min(Math.max(1, maxStackSize), Math.max(1, whole));
    }

    /**
     * 「データパックが独自に足したエンチャントを格納した本」か。
     *
     * <p>判定は<b>格納エンチャントの名前空間</b>で行う。DnT の追加エンチャントは
     * {@code nova_structures:*} なので、{@code minecraft} 以外が 1 つでも入っていれば落とす。
     * エンチャント名を列挙する方式にしないのは、データパックを更新して種類が増えたときに
     * 列挙側が黙って古くなる(＝新エンチャントの本だけすり抜ける)ため。
     *
     * <p>ArsPaper 自前の本はこの判定より<b>後</b>に足されるので巻き込まれない。
     */
    static boolean isDatapackEnchantBook(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }
        if (!(stack.getItemMeta() instanceof EnchantmentStorageMeta storage)) {
            return false;
        }
        List<NamespacedKey> keys = new ArrayList<>();
        for (Enchantment enchant : storage.getStoredEnchants().keySet()) {
            keys.add(enchant.getKey());
        }
        return hasNonVanillaEnchant(keys);
    }

    /**
     * 格納エンチャントのキーに {@code minecraft} 以外の名前空間が混ざっているか。
     *
     * <p>{@link #isDatapackEnchantBook(ItemStack)} から切り出してあるのは、このフォークの
     * テスト環境ではサーバを起こせず {@code ItemStack} を作れないため。判定の中身はここにある。
     */
    static boolean hasNonVanillaEnchant(Collection<NamespacedKey> storedEnchantKeys) {
        for (NamespacedKey key : storedEnchantKeys) {
            if (key != null && !NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
                return true;
            }
        }
        return false;
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
