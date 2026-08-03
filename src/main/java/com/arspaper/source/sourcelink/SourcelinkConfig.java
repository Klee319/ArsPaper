package com.arspaper.source.sourcelink;

import com.arspaper.item.ItemCostTable;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * sourcelinks.yml から各ソースリンクのマテリアル／カスタムアイテム値マップと、本体アイテム定義(items:)を読み込む。
 */
public class SourcelinkConfig {

    /** 実装が挙動を持つソースリンク種別 (items.<id>.type に指定できる値)。 */
    public static final List<String> TYPES =
            List.of("volcanic", "mycelial", "alchemical", "vitalic", "botanical");

    public record ItemDef(
            String id,
            Material material,
            String displayName,
            int customModelData,
            List<String> lore,
            String type,
            double transferMultiplier,
            double yieldMultiplier) {

        /** 旧来の5固定id用 (type は id から推定、transfer/yield-multiplier は既定1.0)。 */
        public ItemDef(String id, Material material, String displayName,
                       int customModelData, List<String> lore) {
            this(id, material, displayName, customModelData, lore, inferType(id, ""), 1.0, 1.0);
        }
    }

    /**
     * type の解決: 明示指定を最優先し、無ければ id に含まれる種別名から推定する
     * (固定5種 "volcanic_sourcelink" 等はこれで解決)。どちらでも解決できなければ "" を返す。
     */
    public static String inferType(String id, String explicit) {
        String ex = explicit == null ? "" : explicit.trim().toLowerCase(Locale.ROOT);
        if (TYPES.contains(ex)) {
            return ex;
        }
        String lower = id == null ? "" : id.toLowerCase(Locale.ROOT);
        for (String type : TYPES) {
            if (lower.contains(type)) {
                return type;
            }
        }
        return "";
    }

    private final JavaPlugin plugin;
    private ItemCostTable volcanicMaterials = ItemCostTable.empty();
    private ItemCostTable mycelialMaterials = ItemCostTable.empty();
    private ItemCostTable alchemicalMaterials = ItemCostTable.empty();
    private Map<String, ItemDef> items = Map.of();
    // 転送速度/転送範囲/経路パーティクル(2026-08-01 config化)。未設定なら従来のハードコード値。
    private volatile com.arspaper.source.SourceTransferConfig transfer =
            com.arspaper.source.SourceTransferConfig.defaults();

    public SourcelinkConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "sourcelinks.yml");
        if (!file.exists()) {
            plugin.getLogger().info("sourcelinks.yml not found, using default values");
            volcanicMaterials = ItemCostTable.fromMaterials(VolcanicSourcelink.getDefaultFuelValues());
            mycelialMaterials = ItemCostTable.fromMaterials(MycelialSourcelink.getDefaultFoodValues());
            alchemicalMaterials = ItemCostTable.fromMaterials(AlchemicalSourcelink.getDefaultAlchemyValues());
            items = Map.of();
            transfer = com.arspaper.source.SourceTransferConfig.defaults();
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Logger logger = plugin.getLogger();

        volcanicMaterials = loadSection(config, "volcanic.materials",
                ItemCostTable.fromMaterials(VolcanicSourcelink.getDefaultFuelValues()), logger);
        mycelialMaterials = loadSection(config, "mycelial.materials",
                ItemCostTable.fromMaterials(MycelialSourcelink.getDefaultFoodValues()), logger);
        alchemicalMaterials = loadSection(config, "alchemical.materials",
                ItemCostTable.fromMaterials(AlchemicalSourcelink.getDefaultAlchemyValues()), logger);
        items = loadItems(config, logger);
        transfer = com.arspaper.source.SourceTransferConfig.parse(
                config.getConfigurationSection("transfer"), logger::warning);

        logger.info("Sourcelink config loaded: volcanic=" + volcanicMaterials.size()
                + ", mycelial=" + mycelialMaterials.size()
                + ", alchemical=" + alchemicalMaterials.size()
                + ", items=" + items.size()
                + ", transfer=" + transfer.sourcelinkMaxPerTransfer() + "/"
                + transfer.sourcelinkIntervalTicks() + "t"
                + ", network=" + transfer.networkMaxPerTransfer() + "/"
                + transfer.networkIntervalTicks() + "t range=" + transfer.networkMaxLinkRange());
    }

    /** 転送速度/転送範囲/経路パーティクルの設定。null にはならない。 */
    public com.arspaper.source.SourceTransferConfig transfer() {
        return transfer;
    }

    private Map<String, ItemDef> loadItems(YamlConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return Map.of();
        }
        Map<String, ItemDef> result = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            try {
                Material mat = Material.valueOf(entry.getString("material", "STONE")
                        .trim().toUpperCase(Locale.ROOT));
                String type = inferType(id, entry.getString("type", ""));
                if (type.isEmpty()) {
                    logger.warning("sourcelinks.yml: items." + id
                            + " has no resolvable type (set 'type:' to one of " + TYPES + ") — entry skipped");
                    continue;
                }
                result.put(id, new ItemDef(
                        id,
                        mat,
                        entry.getString("display-name", id),
                        entry.getInt("custom-model-data", 0),
                        List.copyOf(entry.getStringList("lore")),
                        type,
                        readTransferMultiplier(entry, id, logger),
                        readYieldMultiplier(entry, id, logger)));
            } catch (IllegalArgumentException ex) {
                logger.warning("sourcelinks.yml: items." + id + " invalid: " + ex.getMessage());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * {@code items.<id>.transfer-multiplier}(K-16対応: 階梯でソース転送レートを上げる階梯値)。
     * 未設定なら 1.0(挙動不変)。0以下・非有限値は設定ミスとして警告のうえ 1.0 にフォールバックする
     * ({@link com.arspaper.source.SourceTransferConfig#clampBuffer} と同じく「壊す方向のtypoを
     * 黙って通さない」方針)。
     */
    /* package-private for SourcelinkConfigTest */
    static double readTransferMultiplier(ConfigurationSection entry, String id, Logger logger) {
        return readPositiveMultiplier(entry, id, "transfer-multiplier", logger);
    }

    /**
     * {@code items.<id>.yield-multiplier}(2026-08-03: 階梯で<b>生成量</b>も上げる階梯値)。
     * 未設定なら 1.0(挙動不変)。0以下・非有限値は {@code transfer-multiplier} と同じ方針で
     * 警告のうえ 1.0 にフォールバックする。
     *
     * <p>転送速度({@code transfer-multiplier})と分けているのは<b>効き方が違う</b>ため —— 転送速度は
     * 「バッファから1周期に出せる量」で、上げても素材1個あたりの取得ソースは変わらない。
     * 生成量はその素材効率そのものを上げる。実際に掛ける場所は
     * {@link com.arspaper.source.SourceGenerationScaling}(返却経路に掛けない理由もそこに書いてある)。
     */
    /* package-private for SourcelinkConfigTest */
    static double readYieldMultiplier(ConfigurationSection entry, String id, Logger logger) {
        return readPositiveMultiplier(entry, id, "yield-multiplier", logger);
    }

    private static double readPositiveMultiplier(ConfigurationSection entry, String id,
                                                 String key, Logger logger) {
        if (!entry.isSet(key)) {
            return 1.0;
        }
        double raw = entry.getDouble(key, 1.0);
        if (!Double.isFinite(raw) || raw <= 0) {
            logger.warning("sourcelinks.yml: items." + id + "." + key + "=" + raw
                    + " is invalid (must be > 0) — falling back to 1.0");
            return 1.0;
        }
        return raw;
    }

    private ItemCostTable loadSection(YamlConfiguration config, String path,
                                      ItemCostTable defaults, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return defaults;
        }
        ItemCostTable parsed = ItemCostTable.parseSection(section, logger, "sourcelinks.yml " + path);
        return parsed.isEmpty() ? defaults : parsed;
    }

    public ItemCostTable getVolcanicMaterials() {
        return volcanicMaterials;
    }

    public ItemCostTable getMycelialMaterials() {
        return mycelialMaterials;
    }

    public ItemCostTable getAlchemicalMaterials() {
        return alchemicalMaterials;
    }

    public Optional<ItemDef> item(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Map<String, ItemDef> items() {
        return items;
    }
}
