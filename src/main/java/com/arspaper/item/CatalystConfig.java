package com.arspaper.item;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * spellbooks.yml の {@code catalysts:} 節から触媒定義を読み込み管理する（SpellBookConfigと同様の流儀）。
 *
 * <p>触媒のステ解決自体はTrinityForgeエンジンへ委譲するため、ここでは
 * fixed/per-quality/randomの生値をパースして保持するのみ（フォーク側でステ解決ロジックを重複実装しない）。
 */
public class CatalystConfig {

    private final JavaPlugin plugin;
    private final Logger logger;

    private volatile List<CatalystData> catalysts = List.of();
    private volatile Map<String, CatalystData> byId = Map.of();
    /** material名 + "#" + customModelData をキーとした逆引き索引（TF流儀のmaterial+cmd解決に合わせる）。 */
    private volatile Map<String, CatalystData> byItemKey = Map.of();

    public CatalystConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        load();
    }

    /**
     * spellbooks.ymlのcatalysts:節を再読み込みする。同一インスタンスを再利用するため、
     * ArsPaper#getCatalystConfig() 経由の参照はそのまま有効。
     */
    public void reload() {
        load();
        logger.info("Reloaded spellbooks.yml catalysts (" + catalysts.size() + " catalysts)");
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "spellbooks.yml");
        if (!file.exists()) {
            plugin.saveResource("spellbooks.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection catalystsSection = config.getConfigurationSection("catalysts");

        List<CatalystData> newCatalysts = new ArrayList<>();
        Map<String, CatalystData> newById = new LinkedHashMap<>();
        Map<String, CatalystData> newByItemKey = new LinkedHashMap<>();

        if (catalystsSection != null) {
            for (String id : catalystsSection.getKeys(false)) {
                ConfigurationSection section = catalystsSection.getConfigurationSection(id);
                if (section == null) continue;
                try {
                    CatalystData data = parseCatalyst(id, section);
                    newCatalysts.add(data);
                    newById.put(data.id(), data);
                    newByItemKey.put(itemKey(data.material(), data.customModelData()), data);
                } catch (Exception e) {
                    logger.warning("Failed to load catalyst '" + id + "': " + e.getMessage());
                }
            }
        }

        this.catalysts = List.copyOf(newCatalysts);
        this.byId = Map.copyOf(newById);
        this.byItemKey = Map.copyOf(newByItemKey);
    }

    private CatalystData parseCatalyst(String id, ConfigurationSection section) {
        String materialName = section.getString("material");
        Material material = materialName != null ? Material.matchMaterial(materialName) : null;
        if (material == null) {
            throw new IllegalArgumentException("不正または未設定のmaterial: " + materialName);
        }

        String displayName = section.getString("display-name", id);

        String nameColorHex = section.getString("name-color");
        TextColor nameColor = nameColorHex != null ? TextColor.fromHexString(nameColorHex) : null;
        if (nameColor == null) {
            nameColor = NamedTextColor.WHITE;
        }

        int customModelData = toInt(section.get("custom-model-data"), 0);

        Color dyeColor = null;
        String colorHex = section.getString("color");
        if (colorHex != null) {
            TextColor parsed = TextColor.fromHexString(colorHex);
            if (parsed != null) {
                dyeColor = Color.fromRGB(parsed.red(), parsed.green(), parsed.blue());
            }
        }

        List<String> lore = new ArrayList<>();
        List<?> loreRaw = section.getList("lore");
        if (loreRaw != null) {
            for (Object line : loreRaw) {
                lore.add(String.valueOf(line));
            }
        }

        String bindType = section.getString("bind-type");

        int maxBindTier = toInt(section.get("max-bind-tier"), 3);

        int manaFlatReduction = 0;
        int manaPercentReduction = 0;
        ConfigurationSection manaSection = section.getConfigurationSection("mana-cost-reduction");
        if (manaSection != null) {
            manaFlatReduction = toInt(manaSection.get("flat"), 0);
            manaPercentReduction = toInt(manaSection.get("percent"), 0);
        }

        double cooldownSeconds = section.getDouble("cooldown", 0);
        long cooldownMs = cooldownSeconds > 0 ? Math.round(cooldownSeconds * 1000) : 0L;

        Map<String, Double> fixedStats = Map.of();
        Map<String, Double> perQualityStats = Map.of();
        Map<String, CatalystStatRange> randomStats = Map.of();
        ConfigurationSection statsSection = section.getConfigurationSection("stats");
        if (statsSection != null) {
            fixedStats = parseDoubleMap(statsSection.getConfigurationSection("fixed"));
            perQualityStats = parseDoubleMap(statsSection.getConfigurationSection("per-quality"));
            randomStats = parseRandomMap(statsSection.getConfigurationSection("random"));
        }

        return new CatalystData(
            id, material, customModelData, displayName, nameColor, dyeColor, lore,
            bindType, maxBindTier, manaFlatReduction, manaPercentReduction, cooldownMs,
            fixedStats, perQualityStats, randomStats
        );
    }

    private static Map<String, Double> parseDoubleMap(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key, section.getDouble(key));
        }
        return Map.copyOf(result);
    }

    private static Map<String, CatalystStatRange> parseRandomMap(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, CatalystStatRange> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection range = section.getConfigurationSection(key);
            if (range == null) continue;
            double min = range.getDouble("min", 0);
            double max = range.getDouble("max", min);
            result.put(key, new CatalystStatRange(min, max));
        }
        return Map.copyOf(result);
    }

    /**
     * int系ステの切り捨て変換（thread-slots等の既存パターンに合わせ、小数はintValue()で切り捨てる）。
     */
    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // フォールスルーしてデフォルト値を返す
            }
        }
        return defaultValue;
    }

    private static String itemKey(Material material, int customModelData) {
        return material.name() + "#" + customModelData;
    }

    /** IDで検索（未知IDは null） */
    public CatalystData byId(String id) {
        return byId.get(id);
    }

    /** material + CustomModelData で検索（未登録の組み合わせは null） */
    public CatalystData byItem(Material material, int customModelData) {
        if (material == null) return null;
        return byItemKey.get(itemKey(material, customModelData));
    }

    /**
     * ItemStackが登録済み触媒かどうかを解決する。
     * material + CustomModelData（未設定は0扱い）で照合する（TrinityForgeのmaterial#cmd流儀に合わせる）。
     *
     * @return 触媒として登録済みならその定義、そうでなければ {@code null}
     */
    public CatalystData resolve(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        int cmd = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
        return byItem(item.getType(), cmd);
    }

    /** 全触媒定義を返す */
    public List<CatalystData> all() {
        return catalysts;
    }
}
