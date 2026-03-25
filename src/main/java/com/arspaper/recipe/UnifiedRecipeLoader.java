package com.arspaper.recipe;

import com.arspaper.ritual.RitualIngredient;
import com.arspaper.ritual.RitualRecipe;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 全YMLファイルからレシピを統合読み込みするローダー。
 *
 * 読み込み元:
 *   - items.yml     → items: (workbench/ritual) + ritual_effects: (world effects)
 *   - materials.yml → materials: (ritual recipes)
 *   - threads.yml   → threads: (ritual recipes, effect-type: thread)
 *   - armors.yml    → armor_sets: (workbench recipes は ArmorConfigManager 経由)
 *
 * RecipeManager（作業台）と RitualRecipeRegistry（儀式）にレシピを配布する。
 */
public class UnifiedRecipeLoader {

    private final JavaPlugin plugin;

    /** 作業台レシピ（items.yml の workbench method のみ） */
    private final List<WorkbenchRecipeData> workbenchRecipes = new ArrayList<>();

    /** 儀式レシピ（全ファイルの ritual method + ritual_effects） */
    private final List<RitualRecipe> ritualRecipes = new ArrayList<>();

    public UnifiedRecipeLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 全YMLファイルを読み込んでレシピを統合する。
     */
    public void loadAll() {
        workbenchRecipes.clear();
        ritualRecipes.clear();

        loadItemsYml();
        loadMaterialsYml();
        loadThreadsYml();

        plugin.getLogger().info("UnifiedRecipeLoader: " + workbenchRecipes.size()
            + " workbench + " + ritualRecipes.size() + " ritual recipes loaded");
    }

    // ============================
    // items.yml
    // ============================
    private void loadItemsYml() {
        YamlConfiguration config = loadYml("items.yml");
        if (config == null) return;

        // items セクション
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(id);
                if (itemSection == null) continue;
                ConfigurationSection recipeSection = itemSection.getConfigurationSection("recipe");
                if (recipeSection == null) continue;

                try {
                    String method = recipeSection.getString("method", "workbench");
                    if ("workbench".equalsIgnoreCase(method)) {
                        loadWorkbenchFromSection(id, recipeSection, itemSection);
                    } else if ("ritual".equalsIgnoreCase(method)) {
                        loadRitualFromSection(id, recipeSection, itemSection);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load item recipe: " + id, e);
                }
            }
        }

        // ritual_effects セクション
        ConfigurationSection effects = config.getConfigurationSection("ritual_effects");
        if (effects != null) {
            for (String id : effects.getKeys(false)) {
                ConfigurationSection effectSection = effects.getConfigurationSection(id);
                if (effectSection == null) continue;

                try {
                    loadRitualEffectFromSection(id, effectSection);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load ritual effect: " + id, e);
                }
            }
        }
    }

    // ============================
    // materials.yml
    // ============================
    private void loadMaterialsYml() {
        YamlConfiguration config = loadYml("materials.yml");
        if (config == null) return;

        ConfigurationSection materials = config.getConfigurationSection("materials");
        if (materials == null) return;

        for (String id : materials.getKeys(false)) {
            ConfigurationSection matSection = materials.getConfigurationSection(id);
            if (matSection == null) continue;
            ConfigurationSection recipeSection = matSection.getConfigurationSection("recipe");
            if (recipeSection == null) continue;

            try {
                String name = matSection.getString("display_name", id) + "精製";
                RitualIngredient coreItem = parseSingleIngredient(recipeSection.getString("core-item", null));
                List<RitualIngredient> pedestalItems = parsePedestalItems(recipeSection.getStringList("pedestal-items"));
                int source = recipeSection.getInt("source", 0);

                ritualRecipes.add(new RitualRecipe(
                    id, name, coreItem, pedestalItems, source,
                    id, null, "craft", Map.of()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load material recipe: " + id, e);
            }
        }
    }

    // ============================
    // threads.yml
    // ============================
    private void loadThreadsYml() {
        YamlConfiguration config = loadYml("threads.yml");
        if (config == null) return;

        ConfigurationSection threads = config.getConfigurationSection("threads");
        if (threads == null) return;

        for (String id : threads.getKeys(false)) {
            ConfigurationSection threadSection = threads.getConfigurationSection(id);
            if (threadSection == null) continue;
            ConfigurationSection recipeSection = threadSection.getConfigurationSection("recipe");
            if (recipeSection == null) continue;

            try {
                String name = threadSection.getString("display_name", id);
                RitualIngredient coreItem = parseSingleIngredient(recipeSection.getString("core-item", null));
                List<RitualIngredient> pedestalItems = parsePedestalItems(recipeSection.getStringList("pedestal-items"));
                int source = recipeSection.getInt("source", 0);

                // 空スレッドはクラフト結果あり、効果スレッドはeffect-type: thread
                if ("empty".equals(id)) {
                    ritualRecipes.add(new RitualRecipe(
                        "empty_thread", name, coreItem, pedestalItems, source,
                        "thread_empty", null, "craft", Map.of()));
                } else {
                    Map<String, String> params = new HashMap<>();
                    params.put("thread", id);
                    ritualRecipes.add(new RitualRecipe(
                        "thread_" + id, name, coreItem, pedestalItems, source,
                        null, null, "thread", params));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load thread recipe: " + id, e);
            }
        }
    }

    // ============================
    // Workbench recipe parser
    // ============================
    private void loadWorkbenchFromSection(String id, ConfigurationSection recipeSection,
                                           ConfigurationSection itemSection) {
        String type = recipeSection.getString("type", "shaped");
        String result = "custom:" + id;
        // items.yml側でresult指定があればそれを使う
        if (recipeSection.contains("result")) {
            result = recipeSection.getString("result", result);
        }
        int amount = recipeSection.getInt("amount", 1);
        List<String> shape = recipeSection.getStringList("shape");
        Map<String, String> ingredients = new HashMap<>();
        ConfigurationSection ingSection = recipeSection.getConfigurationSection("ingredients");
        if (ingSection != null) {
            for (String key : ingSection.getKeys(false)) {
                ingredients.put(key, ingSection.getString(key));
            }
        }

        workbenchRecipes.add(new WorkbenchRecipeData(id, type, result, amount, shape, ingredients));
    }

    // ============================
    // Ritual recipe parser
    // ============================
    private void loadRitualFromSection(String id, ConfigurationSection recipeSection,
                                        ConfigurationSection itemSection) {
        String name = recipeSection.getString("name", null);
        if (name == null) {
            // 結果アイテムの表示名から自動生成: "XXX 儀式レシピ"
            String resultStr = recipeSection.getString("result", "custom:" + id);
            name = resolveDisplayNameForRitual(resultStr, id);
        }
        RitualIngredient coreItem = parseSingleIngredient(recipeSection.getString("core-item", null));
        List<RitualIngredient> pedestalItems = parsePedestalItems(recipeSection.getStringList("pedestal-items"));
        int source = recipeSection.getInt("source", 0);

        String resultStr = recipeSection.getString("result", "custom:" + id);
        String resultId = null;
        Material resultMaterial = null;
        if (resultStr.startsWith("custom:")) {
            resultId = resultStr.substring("custom:".length());
        } else {
            resultMaterial = Material.matchMaterial(resultStr);
        }

        String effectType = recipeSection.getString("effect-type", "craft");
        Map<String, String> effectParams = parseEffectParams(recipeSection);
        int resultAmount = recipeSection.getInt("result-amount", 1);

        ritualRecipes.add(new RitualRecipe(
            id, name, coreItem, pedestalItems, source,
            resultId, resultMaterial, effectType, effectParams, resultAmount));
    }

    // ============================
    // Ritual effect parser (world effects, enchant books)
    // ============================
    private void loadRitualEffectFromSection(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String effectType = section.getString("effect-type", "craft");
        Map<String, String> effectParams = parseEffectParams(section);
        RitualIngredient coreItem = parseSingleIngredient(section.getString("core-item", null));
        List<RitualIngredient> pedestalItems = parsePedestalItems(section.getStringList("pedestal-items"));
        int source = section.getInt("source", 0);

        String resultStr = section.getString("result", "");
        String resultId = null;
        Material resultMaterial = null;
        if (resultStr.startsWith("custom:")) {
            resultId = resultStr.substring("custom:".length());
        } else if (!resultStr.isEmpty()) {
            resultMaterial = Material.matchMaterial(resultStr);
        }

        ritualRecipes.add(new RitualRecipe(
            id, name, coreItem, pedestalItems, source,
            resultId, resultMaterial, effectType, effectParams));
    }

    // ============================
    // Utility
    // ============================
    /**
     * 素材文字列をパースする。"MATERIAL x数量" や "custom:id x数量" 形式に対応。
     * @return [ingredient, count] のペア。パース失敗時はnull。
     */
    private RitualIngredient parseSingleIngredient(String str) {
        if (str == null || str.isEmpty()) return null;
        // "x数量" を除去して素材名のみ取得
        String materialPart = str.contains(" x") ? str.substring(0, str.lastIndexOf(" x")).trim() : str.trim();

        if (materialPart.startsWith("custom:")) {
            return RitualIngredient.ofCustom(materialPart.substring("custom:".length()));
        }
        Material mat = Material.matchMaterial(materialPart);
        if (mat != null) {
            return RitualIngredient.ofMaterial(mat);
        }
        plugin.getLogger().warning("Unknown ingredient: " + str);
        return null;
    }

    /**
     * "MATERIAL x数量" 形式から数量を取得する。
     */
    private int parseIngredientCount(String str) {
        if (str == null || !str.contains(" x")) return 1;
        try {
            return Integer.parseInt(str.substring(str.lastIndexOf(" x") + 2).trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private List<RitualIngredient> parsePedestalItems(List<String> items) {
        List<RitualIngredient> result = new ArrayList<>();
        for (String item : items) {
            RitualIngredient ing = parseSingleIngredient(item);
            if (ing == null) continue;
            int count = parseIngredientCount(item);
            for (int i = 0; i < count; i++) {
                result.add(ing);
            }
        }
        return result;
    }

    private Map<String, String> parseEffectParams(ConfigurationSection section) {
        Map<String, String> params = new HashMap<>();
        ConfigurationSection paramsSection = section.getConfigurationSection("effect-params");
        if (paramsSection != null) {
            for (String key : paramsSection.getKeys(false)) {
                params.put(key, paramsSection.getString(key, ""));
            }
        }
        return params;
    }

    /**
     * 結果アイテムの表示名から儀式レシピ名を生成する。
     * カスタムアイテムの場合はレジストリから表示名を取得。
     */
    private String resolveDisplayNameForRitual(String resultStr, String fallbackId) {
        if (resultStr != null && resultStr.startsWith("custom:")) {
            String customId = resultStr.substring("custom:".length());
            var itemOpt = com.arspaper.ArsPaper.getInstance().getItemRegistry().get(customId);
            if (itemOpt.isPresent()) {
                String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(itemOpt.get().getDisplayName());
                return displayName;
            }
        }
        return fallbackId;
    }

    private YamlConfiguration loadYml(String filename) {
        File file = new File(plugin.getDataFolder(), filename);
        if (!file.exists()) {
            plugin.saveResource(filename, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ============================
    // Getters
    // ============================
    public List<WorkbenchRecipeData> getWorkbenchRecipes() {
        return workbenchRecipes;
    }

    public List<RitualRecipe> getRitualRecipes() {
        return ritualRecipes;
    }

    /**
     * 作業台レシピのデータ。RecipeManagerが消費する。
     */
    public record WorkbenchRecipeData(
        String id,
        String type,
        String result,
        int amount,
        List<String> shape,
        Map<String, String> ingredients
    ) {}
}
