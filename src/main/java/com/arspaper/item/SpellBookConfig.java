package com.arspaper.item;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * spellbooks.yml から魔導書ティア定義を読み込み管理する（GlyphConfig と同様の流儀）。
 *
 * spellbooks.yml のリストの並び順=段階（tier番号、1始まり）。既存アイテムのPDC(BOOK_TIER)は
 * このリスト位置の整数値をそのまま指しているため、途中への要素挿入は既存アイテムが指す
 * ティアの意味をずらしてしまう点に注意（末尾追加、または入れ替え時の周知が前提）。
 */
public class SpellBookConfig {

    private final JavaPlugin plugin;
    private final Logger logger;

    private volatile List<SpellBookTierData> tiers = List.of();
    private volatile Map<String, SpellBookTierData> byId = Map.of();
    private volatile Map<Integer, SpellBookTierData> byTier = Map.of();

    public SpellBookConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        load();
    }

    /**
     * spellbooks.ymlを再読み込みする。同一インスタンスを再利用するため、
     * ArsPaper#getSpellBookConfig() 経由の参照はそのまま有効。
     */
    public void reload() {
        load();
        logger.info("Reloaded spellbooks.yml (" + tiers.size() + " spell book tiers)");
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "spellbooks.yml");
        if (!file.exists()) {
            plugin.saveResource("spellbooks.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        List<Map<?, ?>> rawList = config.getMapList("spell-books");

        // 新リストに構築してからアトミックに差し替え
        List<SpellBookTierData> newTiers = new ArrayList<>();
        Map<String, SpellBookTierData> newById = new LinkedHashMap<>();
        Map<Integer, SpellBookTierData> newByTier = new LinkedHashMap<>();

        int position = 0;
        for (Map<?, ?> raw : rawList) {
            position++;
            try {
                SpellBookTierData data = parseTier(position, raw);
                newTiers.add(data);
                newById.put(data.id(), data);
                newByTier.put(data.tier(), data);
            } catch (Exception e) {
                logger.warning("Failed to load spell book tier at position " + position + ": " + e.getMessage());
            }
        }

        if (newTiers.isEmpty()) {
            logger.warning("spellbooks.yml に有効な魔導書ティア定義がありません。spell-books セクションを確認してください。");
        }

        this.tiers = List.copyOf(newTiers);
        this.byId = Map.copyOf(newById);
        this.byTier = Map.copyOf(newByTier);

        validateAgainstItemsYml(newTiers);
    }

    private SpellBookTierData parseTier(int position, Map<?, ?> raw) {
        String id = String.valueOf(raw.get("id"));
        if (id == null || id.isBlank() || "null".equals(id)) {
            throw new IllegalArgumentException("id が未設定です");
        }

        String displayName = raw.get("display-name") != null ? String.valueOf(raw.get("display-name")) : id;

        String nameColorHex = raw.get("name-color") != null ? String.valueOf(raw.get("name-color")) : null;
        TextColor nameColor = nameColorHex != null ? TextColor.fromHexString(nameColorHex) : null;
        if (nameColor == null) {
            nameColor = NamedTextColor.WHITE;
        }

        int maxSlots = toInt(raw.get("max-slots"), 1);
        int maxGlyphTier = toInt(raw.get("max-glyph-tier"), 1);
        int maxGlyphs = toInt(raw.get("max-glyphs"), 9);
        int customModelData = toInt(raw.get("custom-model-data"), 100000 + position);

        Object upgradeFromRaw = raw.get("upgrade-from");
        String upgradeFrom = upgradeFromRaw != null ? String.valueOf(upgradeFromRaw) : null;

        // 発動CTオプション(秒)。未設定/0以下は従来の計算CT(SpellCaster側)にフォールバック。
        double cooldownSeconds = toDouble(raw.get("cooldown"), 0);
        long cooldownMs = cooldownSeconds > 0 ? Math.round(cooldownSeconds * 1000) : 0L;

        return new SpellBookTierData(position, id, displayName, nameColor, maxSlots, maxGlyphTier, maxGlyphs, customModelData, upgradeFrom, cooldownMs);
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                // フォールスルーしてデフォルト値を返す
            }
        }
        return defaultValue;
    }

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

    /**
     * spellbooks.yml のアップグレード段階数と、同ファイル（および互換で items.yml）内の
     * 魔導書アップグレード儀式数の整合性を検証する。
     */
    private void validateAgainstItemsYml(List<SpellBookTierData> tiers) {
        long expectedUpgrades = tiers.stream().filter(t -> t.upgradeFrom() != null).count();

        long ritualUpgradeCount = countSpellBookUpgradeRitualsInSpellbooksYml();
        // 旧配置（items.yml の upgrade_spell_book_*）へのフォールバック
        if (ritualUpgradeCount == 0) {
            ritualUpgradeCount = countSpellBookUpgradeRitualsInItemsYml();
        }
        // 現行配置: レシピは TrinityForge items/catalog.yml へ統一済み（catalog経由で儀式登録される）
        if (ritualUpgradeCount == 0) {
            ritualUpgradeCount =
                com.arspaper.integration.TrinityForgeBridge.countCatalogSpellBookUpgradeRituals();
        }

        if (ritualUpgradeCount != expectedUpgrades) {
            logger.warning("spellbooks.yml のアップグレード段階数(" + expectedUpgrades
                + ")と魔導書アップグレード儀式数(" + ritualUpgradeCount
                + ")が一致していません。TrinityForge items/catalog.yml の spell_book_* recipe（ritual）と"
                + " upgrade-from を確認してください。");
        }
    }

    private long countSpellBookUpgradeRitualsInSpellbooksYml() {
        File file = new File(plugin.getDataFolder(), "spellbooks.yml");
        if (!file.exists()) return 0;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> books = config.getList("spell-books");
        if (books == null) return 0;
        long count = 0;
        for (Object raw : books) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            Object recipeObj = map.get("recipe");
            if (!(recipeObj instanceof Map<?, ?> recipe)) continue;
            Object method = recipe.get("method");
            if (method == null || !"ritual".equalsIgnoreCase(String.valueOf(method))) continue;
            Object result = recipe.get("result");
            String resultStr = result == null ? "" : String.valueOf(result);
            Object idObj = map.get("id");
            if (resultStr.startsWith("custom:spell_book_")
                    || (idObj != null && String.valueOf(idObj).startsWith("spell_book_")
                    && map.get("upgrade-from") != null)) {
                count++;
            }
        }
        return count;
    }

    private long countSpellBookUpgradeRitualsInItemsYml() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) return 0;
        YamlConfiguration itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        ConfigurationSection items = itemsConfig.getConfigurationSection("items");
        if (items == null) return 0;
        long ritualUpgradeCount = 0;
        for (String key : items.getKeys(false)) {
            ConfigurationSection recipe = items.getConfigurationSection(key + ".recipe");
            if (recipe == null) continue;
            if (!"ritual".equalsIgnoreCase(recipe.getString("method", ""))) continue;
            String result = recipe.getString("result", "");
            if (result.startsWith("custom:spell_book_")) {
                ritualUpgradeCount++;
            }
        }
        return ritualUpgradeCount;
    }

    /** IDで検索（未知IDは null） */
    public SpellBookTierData byId(String id) {
        return byId.get(id);
    }

    /**
     * ティア番号（PDCのBOOK_TIER値）で検索。
     * 未知のティア番号は先頭ティア（旧NOVICE相当）にフォールバックする（旧 fromTier(int) 挙動を踏襲）。
     */
    public SpellBookTierData byTier(int tier) {
        SpellBookTierData found = byTier.get(tier);
        if (found != null) return found;
        return tiers.isEmpty() ? null : tiers.get(0);
    }

    /** 全ティアを段階順（tier昇順）で返す */
    public List<SpellBookTierData> all() {
        return tiers;
    }
}
