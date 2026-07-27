package com.arspaper.recipe;

import com.arspaper.item.ItemKeys;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * フォーク登録レシピの per-slot 厳密照合。圧縮ブロック系のように「base material 共通・CMD違い」の
 * カスタムアイテムが、プレーン素材 ingredient({@link org.bukkit.inventory.RecipeChoice.MaterialChoice})に
 * <em>material のみ</em>で誤一致する over-match を修正する。
 *
 * <p><b>症状①(圧縮ブロック over-match)</b>: 最上位の圧縮石 {@code stone_5x}(base=STONE, CMD=100045)を9個置くと、
 * 本来は次段レシピが未定義でクラフト不可のはずが、tier1 レシピ {@code stone_1x}(素材=プレーン{@code STONE})が
 * material 一致で成立し「下位の圧縮石 {@code stone_1x}」が生成されてしまう
 * (鎖の起点=プレーン素材レシピがあらゆる同 material のカスタムアイテムを受理してしまうのが根本原因)。
 *
 * <p><b>症状②(custom: 素材の素のバニラ代用)</b>: {@code custom:<id>} 素材は当初 {@code ExactChoice} で
 * 厳密照合していたが、PDC付きの実物と {@code isSimilar} 不一致になりクラフトがサイレント失敗する副作用があった
 * ({@code source_gem} 等)ため、{@link RecipeManager#resolveIngredient} 側で {@code list:} と同じ「型のみ」照合の
 * {@code MaterialChoice} に緩めた。その結果、盤面に本物のカスタムアイテムが1つも無くても、
 * 同じ base material の素のバニラアイテム(PDCなし)がBukkit側の粗い照合を素通りしてしまう余地が生まれる。
 * このため、選択レシピが {@code custom:} 素材を要求する場合は、盤面にカスタムアイテムが1つも無くても
 * per-slot 再検証を強制する({@link RecipeManager#requiresBareCustomIngredient}参照)。
 *
 * <p>TrinityForge {@code CatalogWorkbenchListener} と同じ方針: 選択されたフォークレシピを実際の盤面に対して
 * per-slot で再検証し、カスタムアイテムは {@code custom:}/{@code list:} で明示的に受理された id のみ、
 * プレーン素材スロットは vanilla アイテムのみ受理する。ミスマッチ時は兄弟フォークレシピへ再マッチし、
 * 正しい tier の結果へ差し替える。どれにも合わなければプレビュー結果をクリアする(=クラフト不可)。
 *
 * <p>逆レシピ({@code *_decompress})は {@code forwardRecipeData} に含めない設計なので対象外
 * ({@code ExactChoice} で既に厳密、介入不要)。
 */
public final class CustomIngredientCraftGuardListener implements Listener {

    private final RecipeManager recipeManager;

    public CustomIngredientCraftGuardListener(RecipeManager recipeManager) {
        this.recipeManager = Objects.requireNonNull(recipeManager, "recipeManager");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe selected = event.getRecipe();
        if (!(selected instanceof Keyed keyed)) {
            return;
        }
        NamespacedKey key = keyed.getKey();
        UnifiedRecipeLoader.WorkbenchRecipeData selectedData = recipeManager.forwardRecipeData(key);
        if (selectedData == null) {
            return; // フォークの前進レシピ以外(vanilla/他plugin/decompress)は触らない
        }
        ItemStack[] matrix = event.getInventory().getMatrix();
        // 盤面にカスタムアイテムが無ければ over-match は起こりえない、かつ選択レシピも custom: 素材を
        // 要求しないなら素のバニラ代用の余地も無い(プレーンクラフトは素通し)。
        // どちらか一方でも該当すれば per-slot 再検証を強制する必要がある
        // (前者=圧縮ブロック等の over-match 防止、後者=custom: 素材の素のバニラ代用防止)。
        if (!gridHasCustomItem(matrix) && !RecipeManager.requiresBareCustomIngredient(selectedData)) {
            return;
        }
        int gridWidth = matrix.length == 4 ? 2 : 3;
        if (matches(matrix, gridWidth, selectedData)) {
            return; // 選択レシピが厳密に一致 — 正当
        }
        // over-match/代用: 兄弟フォークレシピへ再マッチして正しい tier の結果へ差し替える。
        for (Map.Entry<NamespacedKey, UnifiedRecipeLoader.WorkbenchRecipeData> entry
                : recipeManager.forwardRecipes()) {
            if (matches(matrix, gridWidth, entry.getValue())) {
                ItemStack result = recipeManager.buildRecipeResult(entry.getValue());
                if (result != null) {
                    event.getInventory().setResult(result);
                    return;
                }
            }
        }
        ItemStack vanilla = shadowedVanillaResult(matrix, gridWidth);
        if (vanilla != null) {
            event.getInventory().setResult(vanilla);
            return;
        }
        logMismatch(key, selectedData, matrix);
        event.getInventory().setResult(null); // どのフォークレシピにも合わない → クラフト不可
    }

    /**
     * Crafter(自動作業台, 1.21)経路の防御。{@link PrepareItemCraftEvent} はCrafterでは発火しないため、
     * ここで同じ per-slot 厳密照合を行う。これが無いと {@code onPrepareCraft} で塞いだ over-match が
     * Crafterブロック経由で完全にバイパスできてしまう(例: 最上位圧縮石 {@code stone_5x}×9 を投入すると
     * tier1 の {@code MaterialChoice(STONE)} が material 一致し、下位 {@code stone_1x} が生成される)。
     * 盤面が選択レシピに厳密一致しなければ兄弟フォークレシピへ再マッチし、無ければクラフトをキャンセルする。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed)) {
            return;
        }
        NamespacedKey key = keyed.getKey();
        UnifiedRecipeLoader.WorkbenchRecipeData selectedData = recipeManager.forwardRecipeData(key);
        if (selectedData == null) {
            return; // フォークの前進レシピ以外(vanilla/他plugin/decompress)は触らない
        }
        ItemStack[] matrix;
        try {
            if (!(event.getBlock().getState() instanceof Crafter crafter)) {
                return;
            }
            matrix = crafter.getInventory().getContents();
        } catch (RuntimeException ex) {
            return; // 盤面を取得できない場合は介入しない(バニラ挙動へフォールバック)
        }
        // 盤面にカスタムアイテムが無ければ over-match は起こりえない、かつ選択レシピも custom: 素材を
        // 要求しないなら素のバニラ代用の余地も無い(プレーンクラフトは素通し)。
        // どちらか一方でも該当すれば per-slot 再検証を強制する必要がある
        // (前者=圧縮ブロック等の over-match 防止、後者=custom: 素材の素のバニラ代用防止)。
        if (!gridHasCustomItem(matrix) && !RecipeManager.requiresBareCustomIngredient(selectedData)) {
            return;
        }
        if (matches(matrix, 3, selectedData)) {
            return; // 選択レシピが厳密に一致 — 正当
        }
        // over-match/代用: 兄弟フォークレシピへ再マッチして正しい tier の結果へ差し替える。
        for (Map.Entry<NamespacedKey, UnifiedRecipeLoader.WorkbenchRecipeData> entry
                : recipeManager.forwardRecipes()) {
            if (matches(matrix, 3, entry.getValue())) {
                ItemStack result = recipeManager.buildRecipeResult(entry.getValue());
                if (result != null) {
                    event.setResult(result);
                    return;
                }
            }
        }
        ItemStack vanilla = shadowedVanillaResult(matrix, 3);
        if (vanilla != null) {
            event.setResult(vanilla);
            return;
        }
        logMismatch(key, selectedData, matrix);
        event.setCancelled(true); // どのフォークレシピにも合わない → クラフト不可
    }

    // ------------------------------------------------------------------
    // 影に入ったバニラレシピの復元 (2026-07-28)
    // ------------------------------------------------------------------

    /**
     * <b>「作業台がクラフトできない」バグの修正 (2026-07-28、実サーバ報告)</b>。
     *
     * <p>{@code custom:} 素材は {@link RecipeManager#resolveIngredient} で
     * {@link org.bukkit.inventory.RecipeChoice.MaterialChoice}(=base material のみ照合)に落ちるため、
     * フォークレシピは <b>素のバニラ素材だけの盤面にも Bukkit 側では一致してしまう</b>。
     * Bukkit は一致した中から 1 つしかレシピを返さないので、そこでフォークレシピが選ばれると
     * 同じ盤面に一致する<b>バニラレシピは選択肢ごと消える</b>。従来はそのあと per-slot 検証が
     * 「custom id が違う」と正しく弾き、兄弟にも合わないので {@code setResult(null)} していた
     * 結果、<b>バニラレシピが無言で死んでいた</b>。
     *
     * <p>実例: {@code plank_scrap}(base = {@code OAK_PLANKS})の「2×2 → 板材1枚」レシピが
     * {@code minecraft:crafting_table}(板材 2×2)と完全に重なっており、板材4枚を置いても
     * 結果枠が空のままで<b>作業台が作れなくなっていた</b>。
     *
     * <p>そこで、フォークレシピを弾いた盤面に<b>フォーク由来のカスタムアイテムが1つも無い</b>場合
     * (=純粋に material だけで誤選択された場合)に限り、フォーク前進レシピを除外して盤面を
     * 再照合し、本来選ばれるはずだったレシピの結果を戻す。盤面にカスタムアイテムが混ざる場合は
     * 従来どおりクラフト不可のまま — 圧縮ブロックの tier 誤爆(over-match)を再び開けないため、
     * この非対称は意図的。
     *
     * @return 復元すべき結果、または該当なしの {@code null}
     */
    private ItemStack shadowedVanillaResult(ItemStack[] matrix, int gridWidth) {
        if (gridHasCustomItem(matrix)) {
            return null;
        }
        Iterator<Recipe> it = org.bukkit.Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof Keyed keyed && recipeManager.forwardRecipeData(keyed.getKey()) != null) {
                continue; // フォークの前進レシピ自身(=今まさに弾いたもの)は候補から外す
            }
            if (matchesBukkitRecipe(recipe, matrix, gridWidth)) {
                return recipe.getResult();
            }
        }
        return null;
    }

    /** Bukkit 登録レシピ(shaped/shapeless のみ)を盤面と照合する。それ以外の型は常に false。 */
    private static boolean matchesBukkitRecipe(Recipe recipe, ItemStack[] matrix, int gridWidth) {
        if (recipe instanceof ShapedRecipe shaped) {
            return matchesBukkitShaped(shaped, matrix, gridWidth);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return matchesBukkitShapeless(shapeless, matrix);
        }
        return false;
    }

    private static boolean matchesBukkitShaped(ShapedRecipe recipe, ItemStack[] matrix, int gridWidth) {
        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        return placeableAnywhere(boundingBox(List.of(recipe.getShape())), gridWidth, matrix.length,
                (index, symbol) -> {
                    ItemStack item = matrix[index];
                    if (symbol == ' ') {
                        return isEmpty(item);
                    }
                    RecipeChoice choice = choices.get(symbol);
                    return choice != null && !isEmpty(item) && choice.test(item);
                });
    }

    private static boolean matchesBukkitShapeless(ShapelessRecipe recipe, ItemStack[] matrix) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }
        List<RecipeChoice> choices = recipe.getChoiceList();
        if (items.size() != choices.size()) {
            return false;
        }
        int n = items.size();
        int[] assignedItem = new int[n];
        java.util.Arrays.fill(assignedItem, -1);
        for (int i = 0; i < n; i++) {
            if (!assignChoice(items, choices, i, new boolean[n], assignedItem)) {
                return false;
            }
        }
        return true;
    }

    private static boolean assignChoice(List<ItemStack> items, List<RecipeChoice> choices, int itemIdx,
                                        boolean[] visited, int[] assignedItem) {
        for (int j = 0; j < choices.size(); j++) {
            if (visited[j] || !choices.get(j).test(items.get(itemIdx))) {
                continue;
            }
            visited[j] = true;
            if (assignedItem[j] < 0
                    || assignChoice(items, choices, assignedItem[j], visited, assignedItem)) {
                assignedItem[j] = itemIdx;
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // grid ↔ recipe 厳密照合 (TF CatalogWorkbenchListener 移植)
    // ------------------------------------------------------------------

    private boolean matches(ItemStack[] matrix, int gridWidth, UnifiedRecipeLoader.WorkbenchRecipeData data) {
        // workbench(作業台専用, 3×3)は2×2インベントリグリッドでは成立しない。
        if ("workbench".equalsIgnoreCase(data.method()) && matrix.length == 4) {
            return false;
        }
        if ("shaped".equalsIgnoreCase(data.type())) {
            return matchesShaped(matrix, gridWidth, data);
        }
        return matchesShapeless(matrix, data);
    }

    private boolean matchesShaped(ItemStack[] matrix, int gridWidth,
                                  UnifiedRecipeLoader.WorkbenchRecipeData data) {
        return placeableAnywhere(boundingBox(data.shape()), gridWidth, matrix.length,
                (index, symbol) -> {
                    ItemStack item = matrix[index];
                    if (symbol == ' ') {
                        return isEmpty(item); // 空セルに物があってはならない
                    }
                    String ingredient = data.ingredients().get(String.valueOf(symbol));
                    return ingredient != null && accepts(ingredient, item);
                });
    }

    /** 盤面の1セルが shape の記号を満たすか。{@code symbol == ' '} は「そこが空であること」を意味する。 */
    @FunctionalInterface
    interface CellPredicate {
        boolean accepts(int gridIndex, char symbol);
    }

    /**
     * 外接矩形済みの {@code shape} を {@code gridWidth × (gridSize / gridWidth)} の盤面のどこかに
     * 置けるか。vanilla の shaped は左右反転配置も受理するため鏡像も試す。
     *
     * <p>純関数なので、盤面の中身の解釈({@code custom:} id 一致なのか {@link RecipeChoice} なのか)を
     * 呼び出し側に委ねたまま、配置探索・鏡像・空セル判定だけを単体テストできる
     * ({@code CustomIngredientCraftGuardShapeTest})。フォークレシピ照合とバニラレシピ照合の
     * 共通実装でもある(片方だけ直して挙動がズレる事故を防ぐ)。
     */
    static boolean placeableAnywhere(List<String> shape, int gridWidth, int gridSize, CellPredicate cell) {
        if (shape.isEmpty() || gridWidth <= 0 || gridSize <= 0 || gridSize % gridWidth != 0) {
            return false;
        }
        int rows = shape.size();
        int cols = shape.get(0).length();
        int gridHeight = gridSize / gridWidth;
        if (rows > gridHeight || cols > gridWidth) {
            return false;
        }
        for (boolean mirrored : new boolean[] {false, true}) {
            List<String> placed = mirrored ? mirror(shape) : shape;
            for (int dy = 0; dy + rows <= gridHeight; dy++) {
                for (int dx = 0; dx + cols <= gridWidth; dx++) {
                    if (fitsAt(placed, gridWidth, gridHeight, dy, dx, cell)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean fitsAt(List<String> shape, int gridWidth, int gridHeight,
                                  int dy, int dx, CellPredicate cell) {
        int rows = shape.size();
        int cols = shape.get(0).length();
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                boolean inside = y >= dy && y < dy + rows && x >= dx && x < dx + cols;
                char symbol = inside ? shape.get(y - dy).charAt(x - dx) : ' ';
                if (!cell.accepts(y * gridWidth + x, symbol)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private boolean matchesShapeless(ItemStack[] matrix, UnifiedRecipeLoader.WorkbenchRecipeData data) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }
        List<String> ingredients = new ArrayList<>(data.ingredients().values());
        if (items.size() != ingredients.size()) {
            return false;
        }
        int n = items.size();
        int[] assignedItem = new int[n];
        java.util.Arrays.fill(assignedItem, -1);
        for (int i = 0; i < n; i++) {
            if (!assign(items, ingredients, i, new boolean[n], assignedItem)) {
                return false;
            }
        }
        return true;
    }

    private boolean assign(List<ItemStack> items, List<String> ingredients, int itemIdx,
                           boolean[] visited, int[] assignedItem) {
        for (int j = 0; j < ingredients.size(); j++) {
            if (visited[j] || !accepts(ingredients.get(j), items.get(itemIdx))) {
                continue;
            }
            visited[j] = true;
            if (assignedItem[j] < 0 || assign(items, ingredients, assignedItem[j], visited, assignedItem)) {
                assignedItem[j] = itemIdx;
                return true;
            }
        }
        return false;
    }

    /** ingredient 文字列が item を受理するか(custom は id 完全一致、プレーンは vanilla のみ)。 */
    private boolean accepts(String ingredient, ItemStack item) {
        return recipeManager.resolveIngredientMatch(ingredient).accepts(item, customIdOf(item));
    }

    /**
     * per-slot 再検証がどの兄弟フォークレシピにも一致せずクラフト不可へ倒れた際の診断ログ。
     * {@link Level#FINE} で出す(既定のサーバーログレベルはINFO以上なので、明示的にログレベルを
     * 下げない限り出力されない = 通常プレイでは一切出ない)。かつ、この呼び出しは「盤面変化イベント
     * 1回につき最大1回、しかも最終的にクラフト不可と確定した時だけ」なので、盤面を眺めているだけで
     * 連打されることもない。
     */
    private void logMismatch(NamespacedKey key, UnifiedRecipeLoader.WorkbenchRecipeData selectedData,
                             ItemStack[] matrix) {
        var logger = com.arspaper.ArsPaper.getInstance().getLogger();
        if (!logger.isLoggable(Level.FINE)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Recipe mismatch (custom ingredient id or over-match guard rejected craft): ")
            .append(key).append(" [method=").append(selectedData.method())
            .append(", type=").append(selectedData.type()).append("]");
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String customId = customIdOf(item);
            sb.append(" | slot").append(i).append('=')
                .append(customId != null ? "custom:" + customId : item.getType().name());
        }
        logger.fine(sb.toString());
    }

    private static boolean gridHasCustomItem(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (customIdOf(item) != null) {
                return true;
            }
        }
        return false;
    }

    /** アイテムの CUSTOM_ITEM_ID(フォークのカスタムアイテムid)。無ければ null。 */
    private static String customIdOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        return (id == null || id.isBlank()) ? null : id;
    }

    /**
     * shape を非空セルの外接矩形に切り詰める(行幅は揃える)。
     *
     * <p>package-private なのは単体テストのため(2026-07-24 レビューM指摘「fork の新ガードに単体テストが
     * 皆無」)。このガードの本体はBukkitイベントを受け取るので MockBukkit 無しには直接叩けないが、
     * over-match/代用の判定精度を決めているのは実質この shape 正規化なので、ここだけは純関数として
     * 切り出したまま検証できるようにしてある。
     */
    static List<String> boundingBox(List<String> shape) {
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        int left = Integer.MAX_VALUE;
        int right = -1;
        for (int y = 0; y < shape.size(); y++) {
            String row = shape.get(y);
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) != ' ') {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        if (bottom < 0) {
            return List.of();
        }
        List<String> box = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            String row = shape.get(y);
            StringBuilder sb = new StringBuilder();
            for (int x = left; x <= right; x++) {
                sb.append(x < row.length() ? row.charAt(x) : ' ');
            }
            box.add(sb.toString());
        }
        return box;
    }

    /** shape の左右反転(鏡像レシピの照合用)。package-private の理由は {@link #boundingBox} と同じ。 */
    static List<String> mirror(List<String> shape) {
        List<String> mirrored = new ArrayList<>(shape.size());
        for (String row : shape) {
            mirrored.add(new StringBuilder(row).reverse().toString());
        }
        return mirrored;
    }
}
