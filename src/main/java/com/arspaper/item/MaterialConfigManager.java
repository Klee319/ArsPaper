package com.arspaper.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * materials.ymlからカスタム中間素材定義を読み込む。
 */
public class MaterialConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, MaterialConfig> materials = new LinkedHashMap<>();

    public MaterialConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        materials.clear();

        File file = new File(plugin.getDataFolder(), "materials.yml");
        if (!file.exists()) {
            plugin.saveResource("materials.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("materials");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection matSection = section.getConfigurationSection(id);
            if (matSection == null) continue;

            try {
                String displayName = matSection.getString("display_name", id);
                String nameColor = matSection.getString("name_color", "&f");
                Material baseMaterial = Material.matchMaterial(
                    matSection.getString("base_material", "STONE"));
                if (baseMaterial == null) {
                    plugin.getLogger().warning("Unknown base_material for material: " + id);
                    continue;
                }
                int customModelData = matSection.getInt("custom_model_data", 0);
                List<String> lore = matSection.getStringList("lore");
                boolean enchantGlow = matSection.getBoolean("enchant_glow", true);
                // 食料として食べてよい素材か(既定 false = 従来どおり食用機能を奪う)。
                // 既定を false にしてあるのは、書き忘れた素材が「バニラ栄養値で食べられる」側へ
                // 倒れないようにするため(圧縮食料をバニラ換算で食べられると大量消費の事故になる)。
                boolean edible = matSection.getBoolean("edible", false);

                // レシピ
                String coreItem = null;
                List<String> pedestalItems = Collections.emptyList();
                int source = 0;

                // 2026-08-13: レシピが2件以上ある素材は recipe:(単数)が無く recipes:(配列)だけになる
                // (設定エディタが catalog.yml と同じ正規形へ書き戻すため)。ここが見ているのは
                // 「作り方の表示用フィールド」1件ぶんなので、単数が無ければ配列の1件目で代用する。
                ConfigurationSection recipeSection = matSection.getConfigurationSection("recipe");
                if (recipeSection == null && matSection.isList("recipes")) {
                    List<?> rawRecipes = matSection.getList("recipes", Collections.emptyList());
                    for (Object entry : rawRecipes) {
                        if (!(entry instanceof java.util.Map<?, ?> map)) continue;
                        org.bukkit.configuration.file.YamlConfiguration wrap =
                            new org.bukkit.configuration.file.YamlConfiguration();
                        java.util.Map<String, Object> stringKeyed = new java.util.HashMap<>();
                        for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                            if (e.getKey() == null) continue;
                            stringKeyed.put(String.valueOf(e.getKey()), e.getValue());
                        }
                        wrap.createSection("r", stringKeyed);
                        recipeSection = wrap.getConfigurationSection("r");
                        break;
                    }
                }
                if (recipeSection != null) {
                    coreItem = recipeSection.getString("core-item", null);
                    pedestalItems = recipeSection.getStringList("pedestal-items");
                    source = recipeSection.getInt("source", 0);
                }

                materials.put(id, new MaterialConfig(
                    id, displayName, nameColor, baseMaterial, customModelData,
                    lore, enchantGlow, coreItem, pedestalItems, source, edible));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load material: " + id + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + materials.size() + " materials from materials.yml");
    }

    public void reload() {
        load();
    }

    public Optional<MaterialConfig> get(String id) {
        return Optional.ofNullable(materials.get(id));
    }

    public Collection<MaterialConfig> getAll() {
        return materials.values();
    }
}
