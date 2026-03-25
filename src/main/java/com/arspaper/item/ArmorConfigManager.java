package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * armors.yml からカスタム防具セット定義を読み込み管理する。
 */
public class ArmorConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private volatile Map<String, ArmorSetConfig> sets = new LinkedHashMap<>();
    private volatile Map<String, ArmorSetConfig> itemIdToSet = new HashMap<>();

    public ArmorConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        load();
    }

    public void reload() {
        load();
        logger.info("Reloaded armors.yml (" + sets.size() + " armor sets)");
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "armors.yml");
        if (!file.exists()) {
            plugin.saveResource("armors.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection setsSection = config.getConfigurationSection("armor_sets");
        if (setsSection == null) return;

        Map<String, ArmorSetConfig> newSets = new LinkedHashMap<>();
        Map<String, ArmorSetConfig> newItemIdMap = new HashMap<>();

        for (String setId : setsSection.getKeys(false)) {
            ConfigurationSection section = setsSection.getConfigurationSection(setId);
            if (section == null) continue;

            try {
                ArmorSetConfig armorSet = parseArmorSet(setId, section);
                newSets.put(setId, armorSet);
                for (String slot : ArmorSetConfig.getSlotNames()) {
                    newItemIdMap.put(armorSet.getItemId(slot), armorSet);
                }
            } catch (Exception e) {
                logger.warning("Failed to load armor set '" + setId + "': " + e.getMessage());
            }
        }

        this.sets = newSets;
        this.itemIdToSet = newItemIdMap;
    }

    private ArmorSetConfig parseArmorSet(String setId, ConfigurationSection section) {
        String displayNamePrefix = section.getString("display_name_prefix", setId);
        String nameColor = section.getString("name_color", "&f");
        String colorHex = section.getString("color", null);
        String material = section.getString("material", "LEATHER");
        int customModelDataBase = section.getInt("custom_model_data_base", 200001);
        boolean enchantGlow = section.getBoolean("enchant_glow", false);

        // Stats
        ConfigurationSection stats = section.getConfigurationSection("stats");
        int manaBonus = stats != null ? stats.getInt("mana_bonus", 0) : 0;
        int manaRegen = stats != null ? stats.getInt("mana_regen", 0) : 0;
        int hitManaRecovery = stats != null ? stats.getInt("hit_mana_recovery", 0) : 0;
        int damageManaRecovery = stats != null ? stats.getInt("damage_mana_recovery", 0) : 0;

        int threadSlots = section.getInt("thread_slots", 0);
        int durability = section.getInt("durability", 0);
        boolean enchantable = section.getBoolean("enchantable", true);

        // Defense
        Map<String, Integer> defense = new LinkedHashMap<>();
        ConfigurationSection defSection = section.getConfigurationSection("defense");
        if (defSection != null) {
            for (String slot : ArmorSetConfig.getSlotNames()) {
                defense.put(slot, defSection.getInt(slot, 0));
            }
        }
        double toughness = section.getDouble("toughness", 0.0);

        // Lore
        List<String> loreLines = section.getStringList("lore");

        // Recipes
        Map<String, RecipeDefinition> recipes = new LinkedHashMap<>();
        ConfigurationSection recipeSection = section.getConfigurationSection("recipe");
        if (recipeSection != null) {
            for (String slot : ArmorSetConfig.getSlotNames()) {
                ConfigurationSection slotRecipe = recipeSection.getConfigurationSection(slot);
                if (slotRecipe != null) {
                    recipes.put(slot, parseRecipe(slotRecipe));
                }
            }
        }

        return new ArmorSetConfig(
            setId, displayNamePrefix, nameColor,
            colorHex, material, customModelDataBase,
            enchantGlow,
            manaBonus, manaRegen, hitManaRecovery, damageManaRecovery,
            threadSlots, durability, enchantable,
            defense, toughness, loreLines, recipes
        );
    }

    private RecipeDefinition parseRecipe(ConfigurationSection section) {
        String type = section.getString("type", "shaped");
        List<String> shape = section.getStringList("shape");
        Map<String, String> ingredients = new LinkedHashMap<>();

        ConfigurationSection ingSection = section.getConfigurationSection("ingredients");
        if (ingSection != null) {
            for (String key : ingSection.getKeys(false)) {
                ingredients.put(key, ingSection.getString(key));
            }
        }
        return new RecipeDefinition(type, shape, ingredients);
    }

    /** セットIDで検索 */
    public ArmorSetConfig getSetById(String setId) {
        return sets.get(setId);
    }

    /** カスタムアイテムIDで検索（例: "battlemage_helmet"） */
    public ArmorSetConfig getSetByItemId(String customItemId) {
        return itemIdToSet.get(customItemId);
    }

    /** 全セットを返す */
    public Collection<ArmorSetConfig> getAll() {
        return sets.values();
    }

    /** 全セットIDを返す */
    public Set<String> getSetIds() {
        return sets.keySet();
    }
}
