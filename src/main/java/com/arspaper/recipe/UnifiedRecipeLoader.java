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
 *   - functional-items.yml → items: の各エントリ内 recipe: (workbench/ritual)。機能アイテム
 *     (ワンド/コンパス/儀式ブロック等)のレシピはここに統一済み(2026-07-25 catalog.yml/items.yml統合)。
 *   - items.yml       → items: (workbench/ritual) + ritual_effects: (world effects)
 *   - materials.yml   → materials: (workbench or ritual recipes)
 *   - threads.yml     → threads: (ritual recipes, effect-type: thread)
 *   - sourcejars.yml  → jars.*.recipe
 *   - sourcelinks.yml → items.*.recipe
 *   - spellbooks.yml  → spell-books[].recipe
 *   - 防具は TrinityForge の item-catalog レシピ機構が担うため、ここでは扱わない。
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

        loadFunctionalItemsYml();
        loadItemsYml();
        loadMaterialsYml();
        loadThreadsYml();
        loadSourceJarsYml();
        loadSourceLinksYml();
        loadSpellbooksYml();

        plugin.getLogger().info("UnifiedRecipeLoader: " + workbenchRecipes.size()
            + " workbench + " + ritualRecipes.size() + " ritual recipes loaded");
    }

    // ============================
    // functional-items.yml
    // ============================
    /**
     * functional-items.yml の items.&lt;id&gt;.recipe を読み込む。表示名/lore/enchant-glow/material の
     * 上書きは {@link com.arspaper.item.FunctionalItemConfig} が別途扱うため、ここではrecipeのみ見る
     * (同じファイルを2つのローダーがそれぞれの関心事だけ読む — items.ymlの構造をそのまま踏襲)。
     */
    private void loadFunctionalItemsYml() {
        YamlConfiguration config = loadYml("functional-items.yml");
        if (config == null) return;

        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) return;

        for (String id : items.getKeys(false)) {
            ConfigurationSection itemSection = items.getConfigurationSection(id);
            if (itemSection == null) continue;
            ConfigurationSection recipeSection = itemSection.getConfigurationSection("recipe");
            if (recipeSection == null) continue;

            try {
                loadItemRecipe(id, recipeSection, itemSection);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load functional item recipe: " + id, e);
            }
        }
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
                    if ("workbench".equalsIgnoreCase(method) || "inventory".equalsIgnoreCase(method)) {
                        loadWorkbenchFromSection(id, recipeSection, itemSection, method.toLowerCase());
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
                String method = resolveMaterialRecipeMethod(recipeSection);
                if (method == null) continue;
                if ("workbench".equals(method) || "inventory".equals(method)) {
                    loadWorkbenchFromSection(id, recipeSection, matSection, method);
                } else if ("ritual".equals(method)) {
                    loadMaterialRitualFromSection(id, recipeSection, matSection);
                } else {
                    plugin.getLogger().warning("Unknown material recipe method for " + id + ": " + method);
                }
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
    // sourcejars.yml
    // ============================
    private void loadSourceJarsYml() {
        YamlConfiguration config = loadYml("sourcejars.yml");
        if (config == null) return;
        ConfigurationSection jars = config.getConfigurationSection("jars");
        if (jars == null) return;
        for (String id : jars.getKeys(false)) {
            ConfigurationSection jarSection = jars.getConfigurationSection(id);
            if (jarSection == null) continue;
            ConfigurationSection recipeSection = jarSection.getConfigurationSection("recipe");
            if (recipeSection == null) continue;
            try {
                loadItemRecipe(id, recipeSection, jarSection);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load sourcejar recipe: " + id, e);
            }
        }
    }

    // ============================
    // sourcelinks.yml
    // ============================
    private void loadSourceLinksYml() {
        YamlConfiguration config = loadYml("sourcelinks.yml");
        if (config == null) return;
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) return;
        for (String id : items.getKeys(false)) {
            ConfigurationSection itemSection = items.getConfigurationSection(id);
            if (itemSection == null) continue;
            ConfigurationSection recipeSection = itemSection.getConfigurationSection("recipe");
            if (recipeSection == null) continue;
            try {
                loadItemRecipe(id, recipeSection, itemSection);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load sourcelink recipe: " + id, e);
            }
        }
    }

    // ============================
    // spellbooks.yml
    // ============================
    private void loadSpellbooksYml() {
        YamlConfiguration config = loadYml("spellbooks.yml");
        if (config == null) return;
        List<?> books = config.getList("spell-books");
        if (books == null) return;
        for (Object raw : books) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            Object idObj = map.get("id");
            if (!(idObj instanceof String id) || id.isBlank()) continue;
            Object recipeObj = map.get("recipe");
            if (!(recipeObj instanceof Map<?, ?>)) continue;
            // Map → 一時 YamlConfiguration 経由で ConfigurationSection 化
            YamlConfiguration wrap = new YamlConfiguration();
            wrap.createSection("root", castStringKeyMap(map));
            ConfigurationSection bookSection = wrap.getConfigurationSection("root");
            if (bookSection == null) continue;
            ConfigurationSection recipeSection = bookSection.getConfigurationSection("recipe");
            if (recipeSection == null) continue;
            try {
                loadItemRecipe(id, recipeSection, bookSection);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load spellbook recipe: " + id, e);
            }
        }
    }

    /** items.yml / jars / sourcelinks / spellbooks 共通の recipe 振り分け。 */
    private void loadItemRecipe(String id, ConfigurationSection recipeSection,
                                ConfigurationSection itemSection) {
        String method = recipeSection.getString("method", "workbench");
        if ("workbench".equalsIgnoreCase(method) || "inventory".equalsIgnoreCase(method)) {
            loadWorkbenchFromSection(id, recipeSection, itemSection, method.toLowerCase());
        } else if ("ritual".equalsIgnoreCase(method)) {
            loadRitualFromSection(id, recipeSection, itemSection);
        } else {
            plugin.getLogger().warning("Unsupported recipe method for " + id + ": " + method);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringKeyMap(Map<?, ?> map) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) continue;
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    /**
     * materials.yml の recipe.method を解決する。
     * method 未指定時: core-item/pedestal-items があれば ritual、
     * shape/ingredients/type があれば workbench、どちらもなければ null (スキップ)。
     */
    private String resolveMaterialRecipeMethod(ConfigurationSection recipeSection) {
        String method = recipeSection.getString("method", null);
        if (method != null && !method.isEmpty()) {
            return method.toLowerCase();
        }
        if (recipeSection.contains("core-item") || recipeSection.contains("pedestal-items")) {
            return "ritual";
        }
        if (recipeSection.contains("shape") || recipeSection.contains("ingredients")
            || recipeSection.contains("type")) {
            return "workbench";
        }
        return null;
    }

    private void loadMaterialRitualFromSection(String id, ConfigurationSection recipeSection,
                                                ConfigurationSection matSection) {
        String name = matSection.getString("display_name", id) + "精製";
        RitualIngredient coreItem = parseSingleIngredient(recipeSection.getString("core-item", null));
        List<RitualIngredient> pedestalItems = parsePedestalItems(recipeSection.getStringList("pedestal-items"));
        int source = recipeSection.getInt("source", 0);

        ritualRecipes.add(new RitualRecipe(
            id, name, coreItem, pedestalItems, source,
            id, null, "craft", Map.of()));
    }

    // ============================
    // Workbench recipe parser
    // ============================
    private void loadWorkbenchFromSection(String id, ConfigurationSection recipeSection,
                                           ConfigurationSection itemSection, String method) {
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
        } else if (recipeSection.isList("ingredients")) {
            // カタログUIの shapeless 配列形式 (Material名のリスト)
            List<String> list = recipeSection.getStringList("ingredients");
            for (int i = 0; i < list.size() && i < 9; i++) {
                String mat = list.get(i);
                if (mat == null || mat.isBlank()) continue;
                ingredients.put(String.valueOf((char) ('A' + i)), mat.trim());
            }
        }
        boolean reversible = recipeSection.getBoolean("reversible", false);

        workbenchRecipes.add(new WorkbenchRecipeData(id, type, result, amount, shape, ingredients, method, reversible));
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
                // list 値（entities 等）はカンマ結合して String Map に載せる
                List<String> asList = paramsSection.getStringList(key);
                if (asList != null && !asList.isEmpty()) {
                    params.put(key, String.join(",", asList));
                    continue;
                }
                Object raw = paramsSection.get(key);
                if (raw == null) {
                    params.put(key, "");
                } else {
                    params.put(key, String.valueOf(raw));
                }
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
                    .plainText().serialize(itemOpt.get().resolveDisplayName());
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
     *
     * @param method     "workbench"(作業台専用, 3×3上限) または "inventory"(2×2クラフト対応, 2×2上限)。
     * @param reversible true の場合、RecipeManagerが逆レシピ(このエントリ1個→元の素材N個)を自動登録する。
     *                   素材が全同一でない場合はRecipeManager側で黙ってスキップされる(fail-soft)。
     */
    public record WorkbenchRecipeData(
        String id,
        String type,
        String result,
        int amount,
        List<String> shape,
        Map<String, String> ingredients,
        String method,
        boolean reversible
    ) {}
}
