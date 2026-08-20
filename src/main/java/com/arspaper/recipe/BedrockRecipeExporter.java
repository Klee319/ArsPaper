package com.arspaper.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * ArsPaper の作業台レシピを「素材の identity を保った表」として
 * {@code plugins/ArsPaper/bedrock-recipes.json} へ書き出す。
 *
 * <h2>なぜ要るのか</h2>
 * Geyser の {@code JavaUpdateRecipesTranslator} は Java のレシピを Bedrock へ変換するとき、
 * <b>素材を「Java のアイテム型 → バニラの Bedrock 定義」に落とす</b>。CustomModelData は
 * Java の {@code Ingredient} に載らないので、そもそも変換元に情報が無い。一方で
 * <b>結果アイテムだけはカスタム Bedrock アイテムへ正しく変換される</b>。
 *
 * <p>結果として統合版クライアントが持つレシピ表は「<b>バニラ</b>のソースジェム ×9 →
 * <b>カスタム</b>のソースジェムブロック」という形になり、盤面に乗る本物のカスタム素材と
 * <b>永久に一致しない</b>（統合版はクラフト結果をクライアントが計算する）。これが
 * 「統合版だけカスタム素材のクラフトが通らない」の真因。
 *
 * <p>この表は、その欠けた情報＝<b>素材が本当はどのカスタムアイテムなのか</b>を
 * material + CustomModelData の形で外へ出す。受け取った GeyserExtra が、実際に登録済みの
 * Bedrock アイテム定義を指す補正レシピをクライアントへ追送する。
 *
 * <h2>TrinityForge 側との形式の関係</h2>
 * TrinityForge も {@code plugins/TrinityForge/bedrock-recipes.json} へ<b>同じ形式</b>で書く。
 * <b>あえてクラスを共有していない</b> ── 共有すると ArsPaper のビルドが
 * {@code libs/TrinityForge.jar} の差し替え待ちになるため。受け取り側はファイルごとに
 * {@link #FORMAT_VERSION} を検査し、理解できない値なら<b>そのファイルを読まずに警告する</b>
 * （黙って半分だけ効く状態にはならない）。
 *
 * <p><b>2 つのバージョンは一致していなくてよい（2026-08-20 以降）。</b>
 * 受け取り側は {@code {1, 2}} の両方を受理する。v2 で増えたのは<b>スミス台レシピ</b>
 * ({@code type: "smithing"}) だけで、これは TrinityForge の {@code items/catalog.yml} の
 * {@code method: netherite} 専用 ── <b>ArsPaper はスミス台レシピを一切登録しない</b>ので、
 * ここを上げても出力は 1 バイトも変わらない。上げると意味のない再ビルドと再配備を強いるだけ。
 *
 * <p><b>形式そのものを変える（キーを増やす・意味を変える）ときだけ</b>、TrinityForge の
 * {@code BedrockRecipeTable}・この定数・GeyserExtra 側の
 * {@code SUPPORTED_FORMAT_VERSIONS} を<b>3 つまとめて</b>直すこと。
 *
 * <h2>書き出す対象</h2>
 * <ul>
 *   <li><b>実際に登録が成功したレシピだけ。</b> {@link RecipeManager#forwardRecipes()} を歩く。
 *       登録に失敗した（素材が解決できない等）レシピを出すと、統合版クライアントだけが
 *       成立すると信じてサーバに拒否される ── 今より悪い壊れ方になる。</li>
 *   <li>{@code reversible} の逆レシピ(解凍)も、<b>実際に登録されているときだけ</b>出す。</li>
 *   <li>素材にカスタム識別があるものだけ。素材が全部バニラなら Geyser の既定変換で足りる。</li>
 * </ul>
 */
public final class BedrockRecipeExporter {

    /**
     * ファイル形式のバージョン。
     *
     * <p><b>TrinityForge の {@code BedrockRecipeTable.FORMAT_VERSION} と一致している必要は無い。</b>
     * 受け取り側(GeyserExtra)が v1 と v2 の両方を受理し、v2 の追加分（スミス台レシピ）は
     * ArsPaper が登録しないため。詳細はクラス javadoc の「TrinityForge 側との形式の関係」。
     */
    public static final int FORMAT_VERSION = 1;

    /** 受け取り側が各プラグインのデータフォルダから探すファイル名。 */
    public static final String FILE_NAME = "bedrock-recipes.json";

    /** この表の出し手。受け取り側が複数プラグイン分をマージするときの出所表示。 */
    public static final String SOURCE = "ArsPaper";

    private BedrockRecipeExporter() {
    }

    /** 1 スロットが受け付けるアイテム 1 種。{@code customModelData} が null ならバニラ素材そのもの。 */
    public record ItemRef(Material material, Integer customModelData, int count) {

        public ItemRef {
            Objects.requireNonNull(material, "material");
            if (count < 1) {
                throw new IllegalArgumentException("count must be >= 1: " + count);
            }
        }

        public static ItemRef of(Material material, Integer customModelData) {
            return new ItemRef(material, customModelData, 1);
        }

        /** Geyser のバニラ変換では表現できない側か。 */
        public boolean isCustom() {
            return customModelData != null;
        }

        /**
         * {@link ItemStack} から起こす。CustomModelData を落とすとこの表の意味が丸ごと消える
         * （＝統合版のクラフトは何も直らないのに、表は出ているように見える）。
         */
        public static ItemRef from(ItemStack stack, int count) {
            ItemMeta meta = stack.getItemMeta();
            Integer cmd = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null;
            return new ItemRef(stack.getType(), cmd, count);
        }
    }

    /** 盤面 1 マス。候補が複数あるのは {@code list:} 素材のときだけ。空リストは「空欄」。 */
    public record Slot(List<ItemRef> items) {

        public Slot {
            items = List.copyOf(items);
        }

        public static Slot empty() {
            return new Slot(List.of());
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }

        public boolean hasCustom() {
            return items.stream().anyMatch(ItemRef::isCustom);
        }
    }

    /**
     * 1 レシピ。{@code shaped} のとき {@code slots} は行優先で {@code width * height} 個、
     * {@code shapeless} のときは素材の並び（空スロットを含まない）。
     */
    public record Recipe(String id, boolean shaped, int width, int height, List<Slot> slots, ItemRef result) {

        public Recipe {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(result, "result");
            slots = List.copyOf(slots);
        }

        /**
         * 素材のどこかにカスタム識別があるか。
         * <b>false のレシピは書き出さない</b> ── Geyser の既定変換と重複するだけになる。
         */
        public boolean needsBedrockFix() {
            return slots.stream().anyMatch(Slot::hasCustom);
        }
    }

    /** 書き出し 1 回分。{@code skipped} は素材か結果を解決できずに落としたレシピ。 */
    public record Table(int version, String source, List<Recipe> recipes, List<String> skipped) {

        public Table {
            recipes = List.copyOf(recipes);
            skipped = List.copyOf(skipped);
        }

        static Table of(List<Recipe> recipes, List<String> skipped) {
            List<Recipe> sorted = new ArrayList<>(recipes);
            // 出力を安定させる。並びが揺れると差分が毎回出て、配備のたびに全プレイヤーへ
            // 補正パケットを撃ち直す判断が付かなくなる。
            sorted.sort((a, b) -> a.id().compareTo(b.id()));
            List<String> sortedSkipped = new ArrayList<>(skipped);
            Collections.sort(sortedSkipped);
            return new Table(FORMAT_VERSION, SOURCE, sorted, sortedSkipped);
        }
    }

    /**
     * 書き出し対象 1 件。{@code decompressRegistered} は逆レシピが<b>実際に</b>
     * Bukkit へ入っているか ── 条件を再導出せず登録結果をそのまま写すためのフラグ。
     */
    public record Entry(String id, UnifiedRecipeLoader.WorkbenchRecipeData data, boolean decompressRegistered) {
    }

    /**
     * 素材文字列・結果文字列を実アイテムへ落とす手続き。差し替え可能にしてあるのは、
     * 盤面の組み立てが<b>Bukkit のサーバ実装なしでテストできる</b>ようにするため。
     */
    public interface ItemResolver {

        /** 素材が受理する候補。解決できないなら空リスト（レシピごと落とす合図）。 */
        List<ItemRef> ingredient(String raw);

        /** 結果アイテム。解決できないなら empty。 */
        Optional<ItemRef> result(String raw, int amount);
    }

    /** 実サーバ用の解決器。{@link RecipeManager} の解決を<b>そのまま</b>使う（別実装にしない）。 */
    public static ItemResolver resolverOf(RecipeManager manager) {
        Objects.requireNonNull(manager, "manager");
        return new ItemResolver() {
            @Override
            public List<ItemRef> ingredient(String raw) {
                RecipeManager.IngredientMatch match = manager.resolveIngredientMatch(raw);
                List<ItemRef> refs = new ArrayList<>();
                // バニラ素材は名前順、そのあとカスタム品を id 順。並びが揺れると差分が毎回出る。
                match.materials().stream()
                        .sorted((a, b) -> a.name().compareTo(b.name()))
                        .forEach(material -> refs.add(ItemRef.of(material, null)));
                match.customIds().stream().sorted().forEach(customId -> {
                    ItemStack stack = manager.resolveCustomOrCatalog(customId);
                    if (stack != null) {
                        refs.add(ItemRef.from(stack, 1));
                    }
                });
                return refs;
            }

            @Override
            public Optional<ItemRef> result(String raw, int amount) {
                ItemStack stack = manager.resolveResult(raw);
                return stack == null ? Optional.empty() : Optional.of(ItemRef.from(stack, Math.max(1, amount)));
            }
        };
    }

    /** レシピ表を組む。ファイル I/O もサーバ実装も触らないのでテストから直接叩ける。 */
    public static Table build(List<Entry> entries, ItemResolver resolver) {
        List<Recipe> recipes = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Entry entry : entries) {
            forward(entry, resolver).ifPresentOrElse(
                    recipe -> {
                        if (recipe.needsBedrockFix()) {
                            recipes.add(recipe);
                        }
                    },
                    () -> skipped.add(entry.id()));
            if (!entry.decompressRegistered()) {
                continue;
            }
            String reverseId = entry.id() + "_decompress";
            reverse(reverseId, entry.data(), resolver).ifPresentOrElse(
                    recipe -> {
                        if (recipe.needsBedrockFix()) {
                            recipes.add(recipe);
                        }
                    },
                    () -> skipped.add(reverseId));
        }
        return Table.of(recipes, skipped);
    }

    private static Optional<Recipe> forward(Entry entry, ItemResolver resolver) {
        UnifiedRecipeLoader.WorkbenchRecipeData data = entry.data();
        Optional<ItemRef> result = resolver.result(data.result(), data.amount());
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return "shapeless".equalsIgnoreCase(data.type())
                ? shapeless(entry.id(), data, resolver, result.get())
                : shaped(entry.id(), data, resolver, result.get());
    }

    /**
     * 逆レシピ(解凍)。素材はこのエントリの完成品 1 個、結果は元素材 N 個 ──
     * {@code RecipeManager#registerReverseIfNeeded} が Bukkit へ入れている形と同じ。
     */
    private static Optional<Recipe> reverse(String id, UnifiedRecipeLoader.WorkbenchRecipeData data,
                                            ItemResolver resolver) {
        Optional<String> uniform = RecipeManager.uniformIngredientValue(data);
        if (uniform.isEmpty()) {
            return Optional.empty();
        }
        Optional<ItemRef> source = resolver.result(data.result(), 1);
        Optional<ItemRef> result = resolver.result(uniform.get(), RecipeManager.uniformIngredientCount(data));
        if (source.isEmpty() || result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Recipe(id, false, 0, 0,
                List.of(new Slot(List.of(source.get()))), result.get()));
    }

    private static Optional<Recipe> shaped(String id, UnifiedRecipeLoader.WorkbenchRecipeData data,
                                           ItemResolver resolver, ItemRef result) {
        List<String> shape = data.shape();
        if (shape == null || shape.isEmpty()) {
            return Optional.empty();
        }
        int height = shape.size();
        int width = 0;
        for (String row : shape) {
            width = Math.max(width, row.length());
        }
        if (width < 1) {
            return Optional.empty();
        }
        List<Slot> slots = new ArrayList<>(width * height);
        for (String row : shape) {
            for (int x = 0; x < width; x++) {
                // 行が短いときは空欄で埋める。詰めると行優先の位置がずれる。
                char symbol = x < row.length() ? row.charAt(x) : ' ';
                if (symbol == ' ') {
                    slots.add(Slot.empty());
                    continue;
                }
                String ingredient = data.ingredients().get(String.valueOf(symbol));
                if (ingredient == null) {
                    return Optional.empty();
                }
                List<ItemRef> refs = resolver.ingredient(ingredient);
                if (refs.isEmpty()) {
                    return Optional.empty();
                }
                slots.add(new Slot(refs));
            }
        }
        return Optional.of(new Recipe(id, true, width, height, slots, result));
    }

    private static Optional<Recipe> shapeless(String id, UnifiedRecipeLoader.WorkbenchRecipeData data,
                                              ItemResolver resolver, ItemRef result) {
        if (data.ingredients().isEmpty()) {
            return Optional.empty();
        }
        List<Slot> slots = new ArrayList<>();
        // ingredients は HashMap なので順序が不定。symbol 順に固定して出力を安定させる
        // (shapeless なので照合上の意味は変わらない)。
        for (Map.Entry<String, String> ingredient : new TreeMap<>(data.ingredients()).entrySet()) {
            List<ItemRef> refs = resolver.ingredient(ingredient.getValue());
            if (refs.isEmpty()) {
                return Optional.empty();
            }
            slots.add(new Slot(refs));
        }
        return Optional.of(new Recipe(id, false, 0, 0, slots, result));
    }

    // ------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------

    public static JsonObject toJson(Table table) {
        JsonObject root = new JsonObject();
        root.addProperty("version", table.version());
        root.addProperty("source", table.source());
        JsonArray recipes = new JsonArray();
        for (Recipe recipe : table.recipes()) {
            recipes.add(toJson(recipe));
        }
        root.add("recipes", recipes);
        JsonArray skipped = new JsonArray();
        table.skipped().forEach(skipped::add);
        root.add("skipped", skipped);
        return root;
    }

    private static JsonObject toJson(Recipe recipe) {
        JsonObject object = new JsonObject();
        object.addProperty("id", recipe.id());
        object.addProperty("type", recipe.shaped() ? "shaped" : "shapeless");
        if (recipe.shaped()) {
            object.addProperty("width", recipe.width());
            object.addProperty("height", recipe.height());
        }
        JsonArray slots = new JsonArray();
        for (Slot slot : recipe.slots()) {
            if (slot.isEmpty()) {
                slots.add((String) null); // 空欄。shaped の行優先配置を崩さないため null を入れる
                continue;
            }
            JsonArray candidates = new JsonArray();
            for (ItemRef ref : slot.items()) {
                candidates.add(toJson(ref));
            }
            slots.add(candidates);
        }
        object.add("slots", slots);
        object.add("result", toJson(recipe.result()));
        return object;
    }

    private static JsonObject toJson(ItemRef ref) {
        JsonObject object = new JsonObject();
        object.addProperty("material", ref.material().name());
        if (ref.customModelData() != null) {
            object.addProperty("cmd", ref.customModelData());
        }
        if (ref.count() != 1) {
            object.addProperty("count", ref.count());
        }
        return object;
    }

    /**
     * {@code <dataFolder>/bedrock-recipes.json} へ書く。
     *
     * <p>同じフォルダの一時ファイルへ書いてから置き換える ── 受け取り側(別プロセス)が
     * 書きかけを読むと、壊れた JSON で読み込みごと落ちる。
     */
    public static void write(Path dataFolder, Table table) throws IOException {
        Files.createDirectories(dataFolder);
        Path target = dataFolder.resolve(FILE_NAME);
        Path temp = dataFolder.resolve(FILE_NAME + ".tmp");
        // UTF-8 で BOM 無し。Files.writeString は BOM を付けない
        // (PowerShell のリダイレクトで作ると BOM が付いて読み手が壊れる、という既知の罠がある)。
        Files.writeString(temp, toJson(table).toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 組んで書いてログに残すところまで。呼び出し側(有効化とリロード)を 1 行にするための入口。
     *
     * <p><b>書き出しに失敗しても呼び出し側は止めない。</b> この表が無くて困るのは
     * 統合版のクラフト補正だけで、Java 版のレシピ登録はもう終わっている。
     */
    public static void exportTo(RecipeManager manager, java.io.File dataFolder, java.util.logging.Logger log) {
        try {
            Table table = build(entriesOf(manager), resolverOf(manager));
            write(dataFolder.toPath(), table);
            log.info("[bedrock] 統合版向け補正レシピ表: " + table.recipes().size() + " 件"
                    + (table.skipped().isEmpty() ? "" : " / 素材未解決で見送り " + table.skipped().size() + " 件"));
        } catch (IOException | RuntimeException ex) {
            log.log(java.util.logging.Level.WARNING,
                    "[bedrock] 補正レシピ表の書き出しに失敗した(統合版のクラフト補正のみ無効になる)", ex);
        }
    }

    /** 登録済みレシピから {@link Entry} を起こす。逆レシピは<b>登録結果</b>をそのまま写す。 */
    public static List<Entry> entriesOf(RecipeManager manager) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<NamespacedKey, UnifiedRecipeLoader.WorkbenchRecipeData> forward : manager.forwardRecipes()) {
            NamespacedKey key = forward.getKey();
            NamespacedKey reverseKey = NamespacedKey.fromString(
                    key.getNamespace() + ":" + key.getKey() + "_decompress");
            boolean decompress = reverseKey != null && manager.getRegisteredRecipes().containsKey(reverseKey);
            entries.add(new Entry(key.toString(), forward.getValue(), decompress));
        }
        return entries;
    }
}
