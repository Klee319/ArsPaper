package com.arspaper.recipe;

import com.arspaper.ArsPaper;
import com.arspaper.integration.TrinityForgeBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * 作業台レシピをサーバーに登録する。
 * UnifiedRecipeLoaderから受け取ったレシピデータを登録する。
 * 防具はTrinityForgeのcatalogレシピ機構が担うため、ここでは扱わない。
 *
 * <p>method:
 * <ul>
 *   <li>{@code workbench} — 作業台専用(3×3上限)。2×2インベントリグリッドでは
 *       {@link WorkbenchGridGateListener} が結果を無効化する
 *       (TrinityForge {@code CatalogWorkbenchListener} の isWorkbench()+matrix.length==4 判定に準拠)。</li>
 *   <li>{@code inventory} — 2×2クラフト対応(2×2上限、shape最大2行×2列/shapeless最大4個)。
 *       2×2でも3×3でもバニラ同様に成立する。</li>
 * </ul>
 * <p>{@code reversible: true} のレシピは、素材が全て同一の場合に限り
 * {@link #registerReverseIfNeeded} が逆レシピ(結果1個→元の素材N個)を自動登録する。
 * 逆レシピの素材(=正レシピの結果アイテムそのもの)は {@link RecipeChoice.ExactChoice} で厳密照合する
 * — 同一CMD/別tierの取り違えによる複製exploitを防ぐため、型のみ照合の MaterialChoice は使わない。
 */
public class RecipeManager {

    private final JavaPlugin plugin;
    private final Map<NamespacedKey, Object> registeredRecipes = new HashMap<>();
    /** method=workbench(作業台専用)で登録されたキー。2×2グリッドでの成立を弾く対象。 */
    private final Set<NamespacedKey> workbenchOnlyKeys = new HashSet<>();
    /**
     * 前進(正)レシピの元データ。{@link CustomIngredientCraftGuardListener} が per-slot 厳密照合と
     * 再マッチに使う(逆レシピ/decompress は含めない — TF {@code CatalogRecipeRegistrar#registeredSpecs} と同義)。
     */
    private final Map<NamespacedKey, UnifiedRecipeLoader.WorkbenchRecipeData> forwardDataByKey =
        new java.util.LinkedHashMap<>();

    public RecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * UnifiedRecipeLoaderから作業台レシピを登録する。
     */
    public void registerWorkbenchRecipes(List<UnifiedRecipeLoader.WorkbenchRecipeData> recipes) {
        for (UnifiedRecipeLoader.WorkbenchRecipeData data : recipes) {
            try {
                switch (data.type().toLowerCase()) {
                    case "shaped" -> registerShaped(data);
                    case "shapeless" -> registerShapeless(data);
                    default -> plugin.getLogger().warning("Unknown recipe type: " + data.type() + " for " + data.id());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to register workbench recipe: " + data.id(), e);
            }
        }
        plugin.getLogger().info("Registered " + registeredRecipes.size() + " workbench recipes");
    }

    /** {@code method: workbench}(作業台専用)として登録済みのキーか。{@link WorkbenchGridGateListener}用。 */
    public boolean isWorkbenchOnly(NamespacedKey key) {
        return workbenchOnlyKeys.contains(key);
    }

    private void registerShaped(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        if (isInventoryMethod(data) && !fitsInventoryShape(data.shape())) {
            plugin.getLogger().warning("Skipping shaped recipe " + data.id()
                + ": inventory method exceeds 2x2 (shape=" + data.shape() + ")");
            return;
        }

        ItemStack result = resolveResult(data.result());
        if (result == null) {
            plugin.getLogger().warning("Skipping shaped recipe " + data.id() + ": result not found (" + data.result() + ")");
            return;
        }
        result.setAmount(data.amount());

        NamespacedKey nsKey = new NamespacedKey(plugin, data.id());

        // 全素材を事前解決（1つでも失敗したらレシピ登録をスキップ）
        Map<Character, RecipeChoice> resolvedIngredients = new HashMap<>();
        boolean allResolved = true;
        for (Map.Entry<String, String> entry : data.ingredients().entrySet()) {
            RecipeChoice choice = resolveIngredient(entry.getValue(), data.id());
            if (choice == null) {
                plugin.getLogger().warning("Skipping shaped recipe " + data.id()
                    + ": ingredient '" + entry.getKey() + "'=" + entry.getValue() + " not found");
                allResolved = false;
            } else {
                resolvedIngredients.put(entry.getKey().charAt(0), choice);
            }
        }
        if (!allResolved) return;

        // reload時の重複防止
        if (registeredRecipes.containsKey(nsKey)) {
            Bukkit.removeRecipe(nsKey);
        }

        ShapedRecipe recipe = new ShapedRecipe(nsKey, result);
        recipe.shape(data.shape().toArray(new String[0]));
        for (Map.Entry<Character, RecipeChoice> entry : resolvedIngredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }

        boolean added = Bukkit.addRecipe(recipe);
        if (added) {
            registeredRecipes.put(nsKey, recipe);
            forwardDataByKey.put(nsKey, data);
            if (isWorkbenchMethod(data)) {
                workbenchOnlyKeys.add(nsKey);
            }
            registerReverseIfNeeded(data);
        } else {
            plugin.getLogger().warning("Bukkit.addRecipe returned false for: " + data.id()
                + " (result=" + data.result() + ", shape=" + data.shape() + ")");
        }
    }

    private void registerShapeless(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        if (isInventoryMethod(data) && data.ingredients().size() > MAX_INVENTORY_SHAPELESS_INGREDIENTS) {
            plugin.getLogger().warning("Skipping shapeless recipe " + data.id()
                + ": inventory method exceeds " + MAX_INVENTORY_SHAPELESS_INGREDIENTS + " ingredients");
            return;
        }

        ItemStack result = resolveResult(data.result());
        if (result == null) {
            plugin.getLogger().warning("Skipping shapeless recipe " + data.id() + ": result not found (" + data.result() + ")");
            return;
        }
        result.setAmount(data.amount());

        NamespacedKey nsKey = new NamespacedKey(plugin, data.id());

        // 全素材を事前解決（shaped側と同じく、1つでも失敗したら劣化登録せずスキップ）
        List<RecipeChoice> resolvedChoices = new ArrayList<>();
        boolean allResolved = true;
        for (String ing : data.ingredients().values()) {
            RecipeChoice choice = resolveIngredient(ing, data.id());
            if (choice == null) {
                plugin.getLogger().warning("Skipping shapeless recipe " + data.id()
                    + ": ingredient '" + ing + "' not found");
                allResolved = false;
            } else {
                resolvedChoices.add(choice);
            }
        }
        if (!allResolved) return;

        // reload時の重複防止
        if (registeredRecipes.containsKey(nsKey)) {
            Bukkit.removeRecipe(nsKey);
        }

        ShapelessRecipe recipe = new ShapelessRecipe(nsKey, result);
        for (RecipeChoice choice : resolvedChoices) {
            recipe.addIngredient(choice);
        }

        boolean added = Bukkit.addRecipe(recipe);
        if (added) {
            registeredRecipes.put(nsKey, recipe);
            forwardDataByKey.put(nsKey, data);
            if (isWorkbenchMethod(data)) {
                workbenchOnlyKeys.add(nsKey);
            }
            registerReverseIfNeeded(data);
        } else {
            plugin.getLogger().warning("Bukkit.addRecipe returned false for: " + data.id());
        }
    }

    private static final int MAX_INVENTORY_DIMENSION = 2;
    private static final int MAX_INVENTORY_SHAPELESS_INGREDIENTS = 4;

    private static boolean isInventoryMethod(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        return "inventory".equalsIgnoreCase(data.method());
    }

    private static boolean isWorkbenchMethod(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        return "workbench".equalsIgnoreCase(data.method());
    }

    static boolean fitsInventoryShape(List<String> shape) {
        if (shape.size() > MAX_INVENTORY_DIMENSION) return false;
        for (String row : shape) {
            if (row.length() > MAX_INVENTORY_DIMENSION) return false;
        }
        return true;
    }

    /**
     * {@code reversible: true} な圧縮レシピの逆レシピ(このエントリ1個 → 元の素材N個)を登録する。
     * 前提(素材が全同一)を満たさない場合は警告のみでスキップする(fail-soft)。
     * 逆レシピの素材(=正レシピの結果アイテムそのもの)は {@link RecipeChoice.ExactChoice} で厳密照合する
     * — CMD重複や同一baseMaterialの別tierを取り違える複製exploitを防ぐため。
     */
    private void registerReverseIfNeeded(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        if (!data.reversible()) return;

        Optional<String> uniform = uniformIngredientValue(data);
        if (uniform.isEmpty()) {
            plugin.getLogger().warning("Skipping reversible recipe for " + data.id()
                + ": ingredients are not uniform (all slots must be the same material)");
            return;
        }
        int count = uniformIngredientCount(data);

        ItemStack forwardResult = resolveResult(data.result());
        if (forwardResult == null) {
            plugin.getLogger().warning("Skipping reversible recipe for " + data.id() + ": result not found");
            return;
        }
        forwardResult.setAmount(1);

        ItemStack reverseResult = resolveResult(uniform.get());
        if (reverseResult == null) {
            plugin.getLogger().warning("Skipping reversible recipe for " + data.id()
                + ": ingredient '" + uniform.get() + "' not resolvable");
            return;
        }
        reverseResult.setAmount(count);

        NamespacedKey reverseKey = new NamespacedKey(plugin, data.id() + "_decompress");
        if (registeredRecipes.containsKey(reverseKey)) {
            Bukkit.removeRecipe(reverseKey);
        }

        ShapelessRecipe recipe = new ShapelessRecipe(reverseKey, reverseResult);
        recipe.addIngredient(new RecipeChoice.ExactChoice(forwardResult));

        boolean added = Bukkit.addRecipe(recipe);
        if (added) {
            registeredRecipes.put(reverseKey, recipe);
        } else {
            plugin.getLogger().warning("Bukkit.addRecipe returned false for reverse recipe: " + reverseKey);
        }
    }

    /**
     * shaped の全非空スロット(shapeless は全ingredient値)が単一の素材文字列に揃っているときだけ
     * その値を返す。1つでも異なる/欠損があれば empty(=reversible対象外)。
     */
    static Optional<String> uniformIngredientValue(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        List<String> used = new ArrayList<>();
        if ("shaped".equalsIgnoreCase(data.type())) {
            for (String row : data.shape()) {
                for (char symbol : row.toCharArray()) {
                    if (symbol == ' ') continue;
                    String ing = data.ingredients().get(String.valueOf(symbol));
                    if (ing == null) return Optional.empty();
                    used.add(ing);
                }
            }
        } else {
            used.addAll(data.ingredients().values());
        }
        if (used.isEmpty()) return Optional.empty();
        String first = used.get(0);
        for (String value : used) {
            if (!value.equals(first)) return Optional.empty();
        }
        return Optional.of(first);
    }

    /** 逆レシピの結果個数 = 正レシピが消費した同一素材スロット数。 */
    static int uniformIngredientCount(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        if ("shaped".equalsIgnoreCase(data.type())) {
            int count = 0;
            for (String row : data.shape()) {
                for (char symbol : row.toCharArray()) {
                    if (symbol != ' ') count++;
                }
            }
            return count;
        }
        return data.ingredients().size();
    }

    /**
     * 素材文字列をRecipeChoiceに変換。"custom:item_id"形式ならExactChoice。
     */
    RecipeChoice resolveIngredient(String ingredientName, String recipeKey) {
        if (ingredientName == null) return null;

        // list:<id> — TrinityForge の素材互換リスト。TF側 catalog レシピと同じ MaterialLists
        // スナップショットを参照する。純Material構成なら MaterialChoice、custom メンバを含むなら
        // Material + カスタムアイテムを混ぜた ExactChoice を返す。
        if (ingredientName.startsWith("list:")) {
            String listId = ingredientName.substring("list:".length());
            Set<Material> mats = TrinityForgeBridge.resolveMaterialList(listId);
            Set<String> customIds = TrinityForgeBridge.resolveMaterialListCustomIds(listId);
            // vanilla・custom メンバともに「型のみ」照合の MaterialChoice へ統一する。
            // TF側 CatalogRecipeRegistrar.choiceFor と同じ意味論: custom メンバは基底 Material で
            // 照合する(精密判定は本来 PrepareItemCraftEvent listener が担うが、ArsPaper作業台レシピは
            // その listener 対象外のため型照合に倒す)。ExactChoice で厳密照合すると PDC 付きの実物と
            // isSimilar 不一致になりクラフト不成立(サイレント失敗)になるのを避ける。custom が混じった
            // 途端に照合が厳密化する副作用も防ぐ。
            LinkedHashSet<Material> union = new LinkedHashSet<>(mats);
            for (String cid : customIds) {
                ItemStack s = resolveCustomOrCatalog(cid);
                if (s != null) union.add(s.getType());
            }
            if (!union.isEmpty()) {
                return new RecipeChoice.MaterialChoice(new ArrayList<>(union));
            }
            plugin.getLogger().warning("Unknown or empty material list 'list:" + listId
                + "' in recipe " + recipeKey);
            return null;
        }

        if (ingredientName.startsWith("custom:")) {
            String customId = ingredientName.substring("custom:".length());
            ItemStack customItem = resolveCustomOrCatalog(customId);
            if (customItem != null) {
                // list: 分岐と同じ理由(上記コメント参照)で「型のみ」照合の MaterialChoice へ倒す。
                // ExactChoice で厳密照合すると PDC 付きの実物と isSimilar 不一致になり
                // クラフト不成立(サイレント失敗)になるのを避ける。
                // 精密判定(id完全一致)は CustomIngredientCraftGuardListener の per-slot 再検証が
                // 必ず担保する — この分岐が custom: を要求するレシピは、盤面にカスタムアイテムが
                // 1つも無くても per-slot 再検証を強制させている(素のバニラ代用を防ぐため)。
                return new RecipeChoice.MaterialChoice(customItem.getType());
            }
            plugin.getLogger().warning("Unknown custom ingredient: " + customId + " in recipe " + recipeKey);
            return null;
        }

        Material mat = Material.matchMaterial(ingredientName);
        if (mat != null) {
            return new RecipeChoice.MaterialChoice(mat);
        }
        plugin.getLogger().warning("Unknown material: " + ingredientName + " in recipe " + recipeKey);
        return null;
    }

    /**
     * {@code custom:<id>} を ItemStack へ解決する。ArsPaper のカスタムアイテムを優先し、
     * 無ければ TrinityForge カタログの identity-only アイテムへフォールバックする。
     * どちらでも解決できなければ {@code null}。
     */
    private ItemStack resolveCustomOrCatalog(String customId) {
        ItemStack item = ArsPaper.getInstance().getItemRegistry()
            .get(customId)
            .map(i -> i.createItemStack())
            .orElse(null);
        if (item == null) {
            item = TrinityForgeBridge.createCatalogIdentity(customId);
        }
        return item;
    }

    private ItemStack resolveResult(String resultStr) {
        if (resultStr == null) return null;

        if (resultStr.startsWith("custom:")) {
            String customId = resultStr.substring("custom:".length());
            var opt = ArsPaper.getInstance().getItemRegistry().get(customId);
            if (opt.isPresent()) {
                return opt.get().createItemStack();
            }
            // ArsPaper未登録なら TrinityForge カタログアイテムへフォールバック
            ItemStack catalog = TrinityForgeBridge.createCatalogIdentity(customId);
            if (catalog != null) {
                return catalog;
            }
            // それも無ければバニラMaterialとしてフォールバック
            Material fallback = Material.matchMaterial(customId);
            if (fallback != null) {
                return new ItemStack(fallback);
            }
            plugin.getLogger().warning("Unknown custom item or material: " + customId);
            return null;
        }

        Material mat = Material.matchMaterial(resultStr);
        if (mat == null) {
            plugin.getLogger().warning("Unknown material for result: " + resultStr);
            return null;
        }
        return new ItemStack(mat);
    }

    public void unloadRecipes() {
        for (NamespacedKey key : registeredRecipes.keySet()) {
            Bukkit.removeRecipe(key);
        }
        registeredRecipes.clear();
        workbenchOnlyKeys.clear();
        forwardDataByKey.clear();
    }

    public int getRecipeCount() {
        return registeredRecipes.size();
    }

    // ------------------------------------------------------------------
    // per-slot 厳密照合サポート (CustomIngredientCraftGuardListener 用)
    // 圧縮ブロック等、base material 共通・CMD違いのcustomアイテムが「プレーン素材ingredient
    // (MaterialChoice)」に material のみで誤一致する over-match を防ぐためのメタデータ。
    // ------------------------------------------------------------------

    /** 前進(正)レシピの元データ。逆/decompress は含まない。 */
    public UnifiedRecipeLoader.WorkbenchRecipeData forwardRecipeData(NamespacedKey key) {
        return forwardDataByKey.get(key);
    }

    /** 全前進レシピ(登録順)。再マッチ走査用。 */
    public java.util.Collection<Map.Entry<NamespacedKey, UnifiedRecipeLoader.WorkbenchRecipeData>>
            forwardRecipes() {
        return java.util.List.copyOf(forwardDataByKey.entrySet());
    }

    /**
     * このレシピが {@code custom:<id>}(list: は除く)素材を1つでも要求するか。
     * true の場合、{@link CustomIngredientCraftGuardListener} は盤面にカスタムアイテムが
     * 1つも無くても per-slot 再検証を強制しなければならない —
     * {@code custom:} 素材は {@link RecipeChoice.MaterialChoice} で「型のみ」照合になったため、
     * 素のバニラアイテム(同base material・PDCなし)がBukkit側の粗い照合を素通りしてしまう。
     * per-slot 側の {@link IngredientMatch#accepts} は customId 完全一致を要求するので、
     * ここで per-slot 検証さえ強制すれば素のバニラ代用は必ず弾かれる。
     * <p>{@code list:} は原則対象外 — vanilla メンバを受理するのが設計上正当だからだ。ただし
     * <b>custom メンバしか持たない list</b>(vanilla メンバが1つも無い)はその理屈が成り立たず、
     * 実質 {@code custom:} と同じ「素のバニラでは絶対に満たせない素材」になる。2026-07-24 レビューの
     * M指摘「fork の短絡が TF 条件より弱い(custom-only list 前提)」がこれで、当時の出荷configには
     * 該当リストが無かったため実害ゼロだったが、リストの中身は運用中に編集される(editorで custom だけの
     * 互換リストを作れる)以上、config 側の内容に依存して静かに穴が開く形になっていた。2026-07-26 に
     * custom-only list も強制ガード対象へ含めて閉じた。
     */
    public static boolean requiresBareCustomIngredient(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        for (String ingredient : data.ingredients().values()) {
            if (ingredient == null) {
                continue;
            }
            if (ingredient.startsWith("custom:")) {
                return true;
            }
            if (ingredient.startsWith("list:")) {
                String listId = ingredient.substring("list:".length());
                if (listRequiresForcedGuard(TrinityForgeBridge.resolveMaterialListCustomIds(listId),
                        TrinityForgeBridge.resolveMaterialList(listId))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code list:} 素材が強制ガード対象か。custom メンバを持ち、vanilla メンバを1つも持たない
     * (= 素のバニラアイテムでは絶対に満たせない)リストだけが対象。
     *
     * <p>解決済みの集合を受け取る純関数として切り出してあるのは、{@code TrinityForgeBridge} が
     * TrinityForge 本体を必要とする(テスト実行時は未ロードで常に空集合を返す)ため、この判定自体を
     * 単体テストできるようにするため。
     */
    static boolean listRequiresForcedGuard(Set<String> customIds, Set<Material> materials) {
        return !customIds.isEmpty() && materials.isEmpty();
    }

    /** レシピ結果の再構築(再マッチ時に正しいtierの結果を差し込むため)。 */
    public ItemStack buildRecipeResult(UnifiedRecipeLoader.WorkbenchRecipeData data) {
        ItemStack result = resolveResult(data.result());
        if (result != null) {
            result.setAmount(Math.max(1, data.amount()));
        }
        return result;
    }

    /**
     * 1つの ingredient 文字列を、照合に必要な「受理する vanilla Material 集合」と
     * 「受理する custom アイテムid 集合」に分解する。
     * <ul>
     *   <li>{@code custom:<id>} → materials=空, customIds={id}(vanillaは一切受理しない)</li>
     *   <li>{@code list:<id>} → materials=リストのvanillaメンバ, customIds=リストのcustomメンバ</li>
     *   <li>プレーン {@code MATERIAL} → materials={その素材}, customIds=空
     *       (=CMD付きcustomアイテムは受理しない ← 圧縮over-match修正の核心)</li>
     * </ul>
     */
    public IngredientMatch resolveIngredientMatch(String ingredientName) {
        if (ingredientName == null) {
            return new IngredientMatch(Set.of(), Set.of());
        }
        if (ingredientName.startsWith("custom:")) {
            return new IngredientMatch(Set.of(), Set.of(ingredientName.substring("custom:".length())));
        }
        if (ingredientName.startsWith("list:")) {
            String listId = ingredientName.substring("list:".length());
            Set<Material> mats = new LinkedHashSet<>(TrinityForgeBridge.resolveMaterialList(listId));
            Set<String> customIds = new LinkedHashSet<>(TrinityForgeBridge.resolveMaterialListCustomIds(listId));
            return new IngredientMatch(mats, customIds);
        }
        Material mat = Material.matchMaterial(ingredientName);
        return mat != null
            ? new IngredientMatch(Set.of(mat), Set.of())
            : new IngredientMatch(Set.of(), Set.of());
    }

    /** ingredient が受理する vanilla Material 集合 / custom アイテムid 集合。 */
    public record IngredientMatch(Set<Material> materials, Set<String> customIds) {
        /**
         * このingredientが item を受理するか。custom アイテム(CUSTOM_ITEM_ID保持)は id 完全一致のみ、
         * vanilla アイテムは material 一致のみで受理する(customを material で誤受理しない)。
         */
        public boolean accepts(ItemStack item, String customId) {
            if (item == null || item.getType().isAir()) {
                return false;
            }
            if (customId != null && !customId.isBlank()) {
                return customIds.contains(customId);
            }
            return materials.contains(item.getType());
        }
    }

    /**
     * 登録済みレシピのマップを返す。
     */
    public Map<NamespacedKey, Object> getRegisteredRecipes() {
        return registeredRecipes;
    }
}
