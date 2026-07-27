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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
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
        logMismatch(key, selectedData, matrix);
        event.setCancelled(true); // どのフォークレシピにも合わない → クラフト不可
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
        List<String> box = boundingBox(data.shape());
        if (box.isEmpty()) {
            return false;
        }
        int rows = box.size();
        int cols = box.get(0).length();
        int gridHeight = matrix.length / gridWidth;
        if (rows > gridHeight || cols > gridWidth) {
            return false;
        }
        // vanilla の shaped は左右反転配置も受理するため両向き試す。
        for (boolean mirrored : new boolean[] {false, true}) {
            List<String> shape = mirrored ? mirror(box) : box;
            for (int dy = 0; dy + rows <= gridHeight; dy++) {
                for (int dx = 0; dx + cols <= gridWidth; dx++) {
                    if (matchesAt(matrix, gridWidth, gridHeight, shape, dy, dx, data)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesAt(ItemStack[] matrix, int gridWidth, int gridHeight,
                              List<String> shape, int dy, int dx,
                              UnifiedRecipeLoader.WorkbenchRecipeData data) {
        int rows = shape.size();
        int cols = shape.get(0).length();
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                ItemStack item = matrix[y * gridWidth + x];
                boolean inside = y >= dy && y < dy + rows && x >= dx && x < dx + cols;
                char symbol = inside ? shape.get(y - dy).charAt(x - dx) : ' ';
                if (symbol == ' ') {
                    if (item != null && !item.getType().isAir()) {
                        return false; // 空セルに物がある
                    }
                    continue;
                }
                String ingredient = data.ingredients().get(String.valueOf(symbol));
                if (ingredient == null || !accepts(ingredient, item)) {
                    return false;
                }
            }
        }
        return true;
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
