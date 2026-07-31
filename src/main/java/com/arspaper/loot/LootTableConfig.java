package com.arspaper.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code loot-tables.yml} を読む（2026-07-31）。
 *
 * <p><b>なぜ作ったか</b>: それまで {@code LootTableListener} は対象ルートテーブル 15 件と
 * 追加する 2 品（カスタムエンチャント本 / エンチャント金リンゴ）を<b>Java にハードコード</b>していた。
 * 本番の資源サーバには構造物生成のデータパック（Dungeons and Taverns 等）を入れる予定で、
 * そのルートテーブルは {@code <datapackNamespace>:chests/...} という別 namespace になる。
 * ハードコードのままだと<b>データパックのチェストには何も入らず</b>、追加するたびに Java を触る
 * ことになる。プール定義を yml へ出し、editor から足せるようにした。
 *
 * <p><b>ルートテーブルの書き方は 3 通り</b>。データパックが実際に使うキーを事前に知る必要が
 * ないようにするため:
 * <ul>
 *   <li>{@code simple_dungeon} — パスの最後の要素と一致（バニラの従来指定と同じ）</li>
 *   <li>{@code minecraft:chests/simple_dungeon} — 完全一致</li>
 *   <li>{@code dungeons_and_taverns:*} — その namespace のすべて</li>
 * </ul>
 */
public class LootTableConfig {

    public static final String FILE_NAME = "loot-tables.yml";

    /** プール1件から出る候補。{@code enchantBook} なら {@code item} は無視して手続き生成する。 */
    public record Entry(String item, double chance, int min, int max, boolean enchantBook) {

        public Entry {
            chance = Math.max(0.0, Math.min(1.0, chance));
            min = Math.max(1, min);
            max = Math.max(min, max);
        }
    }

    /**
     * 1つのプール。
     *
     * @param id      プールID（yml のキー）
     * @param tables  対象ルートテーブルの指定（上の3通り）
     * @param rolls   このプールから抽選する回数。1 なら「各候補を1回ずつ判定」
     * @param entries 候補
     */
    public record Pool(String id, List<String> tables, int rolls, List<Entry> entries) {

        public Pool {
            tables = tables == null ? List.of() : List.copyOf(tables);
            // 上限を置くのは、桁を間違えた rolls でチェストが埋まる（＝経済が壊れる）のを防ぐため。
            rolls = Math.max(1, Math.min(16, rolls));
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        /** このプールが {@code lootTableKey}（{@code namespace:path} 形式）に当たるか。 */
        public boolean matches(String lootTableKey) {
            if (lootTableKey == null || lootTableKey.isEmpty()) {
                return false;
            }
            String lower = lootTableKey.toLowerCase(Locale.ROOT);
            int colon = lower.indexOf(':');
            String namespace = colon < 0 ? "" : lower.substring(0, colon);
            String path = colon < 0 ? lower : lower.substring(colon + 1);
            int slash = path.lastIndexOf('/');
            String leaf = slash < 0 ? path : path.substring(slash + 1);
            for (String pattern : tables) {
                if (pattern.equals(lower) || pattern.equals(leaf)) {
                    return true;
                }
                if (pattern.endsWith(":*") && namespace.equals(pattern.substring(0, pattern.length() - 2))) {
                    return true;
                }
            }
            return false;
        }
    }

    private final JavaPlugin plugin;
    private boolean enabled;
    private final Map<String, Pool> pools = new LinkedHashMap<>();

    public LootTableConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        pools.clear();
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** そのルートテーブルに当たるプール（定義順）。 */
    public List<Pool> poolsFor(String lootTableKey) {
        List<Pool> out = new ArrayList<>();
        for (Pool pool : pools.values()) {
            if (pool.matches(lootTableKey)) {
                out.add(pool);
            }
        }
        return out;
    }

    /** 全プール（テストと診断用）。 */
    public Map<String, Pool> pools() {
        return Map.copyOf(pools);
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("enabled", true);
        pools.putAll(parsePools(config.getConfigurationSection("pools"),
                message -> plugin.getLogger().warning("[" + FILE_NAME + "] " + message)));
        plugin.getLogger().info("[" + FILE_NAME + "] " + pools.size() + " 件のルートプールを読み込みました");
    }

    /**
     * {@code pools:} セクションをパースする。plugin に依存しないので単体テストから直接呼べる。
     *
     * @param root {@code pools:} セクション。null なら空を返す
     * @param warn 警告の出力先
     */
    public static Map<String, Pool> parsePools(ConfigurationSection root, java.util.function.Consumer<String> warn) {
        Map<String, Pool> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                warn.accept("pools." + id + " がマップでないためスキップ");
                continue;
            }
            List<String> tables = new ArrayList<>();
            for (String raw : section.getStringList("tables")) {
                if (raw != null && !raw.isBlank()) {
                    tables.add(raw.trim().toLowerCase(Locale.ROOT));
                }
            }
            if (tables.isEmpty()) {
                // 対象テーブルが空だと「書いたのに絶対に出ない」プールになるので、気づけるよう警告する。
                warn.accept("pools." + id + " に tables: が無いため、このプールは永久に発動しません");
            }
            List<Entry> entries = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("entries")) {
                Entry entry = parseEntry(raw, id, warn);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            if (entries.isEmpty()) {
                warn.accept("pools." + id + " に有効な entries: が無いため、このプールは何も追加しません");
            }
            out.put(id, new Pool(id, tables, section.getInt("rolls", 1), entries));
        }
        return out;
    }

    private static Entry parseEntry(Map<?, ?> raw, String poolId, java.util.function.Consumer<String> warn) {
        String type = string(raw.get("type"), "item").toLowerCase(Locale.ROOT);
        boolean enchantBook = type.equals("enchant-book") || type.equals("enchant_book");
        if (!enchantBook && !type.equals("item")) {
            warn.accept("pools." + poolId + ".entries の type: " + type
                    + " は未知のためスキップ（item / enchant-book のみ）");
            return null;
        }
        String item = string(raw.get("item"), "").trim();
        if (!enchantBook && item.isEmpty()) {
            warn.accept("pools." + poolId
                    + ".entries に item の無い要素があるためスキップ（type: enchant-book 以外は item 必須）");
            return null;
        }
        return new Entry(item, number(raw.get("chance"), 0.05),
                (int) number(raw.get("min"), 1.0), (int) number(raw.get("max"), 1.0), enchantBook);
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException notNumber) {
            return fallback;
        }
    }
}
