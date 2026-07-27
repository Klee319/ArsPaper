package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.ritual.RitualRecipe;
import com.arspaper.spell.GlyphConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * レシピ一覧GUI。全ての作業台レシピと儀式レシピを閲覧できる。
 * /ars recipes で開く。
 *
 * <p><b>2026-07-27 改修（素材⇔レシピ相互ジャンプ / ソート / 絞り込み / 名前検索）:</b>
 * <ul>
 *   <li>詳細画面で<b>素材</b>をクリック → その素材を作るレシピへ飛ぶ（1件なら直接その詳細、
 *       複数なら絞り込み一覧）。</li>
 *   <li>詳細画面で<b>完成品</b>をクリック → それを素材に使うレシピの一覧。</li>
 *   <li>並べ替え(名前 / 種別 / 使用可能レベル)・解放状態の絞り込み・ワイルドカード名前検索。
 *       並べ替えと絞り込みは<b>別のボタン</b>（ユーザー確定仕様）。</li>
 * </ul>
 * 並べ替え・絞り込みのロジック自体は {@link RecipeBrowserFilter}、1件分のデータは
 * {@link RecipeEntry} に切り出してある。
 *
 * <p>検索入力にチャットを使うのは、砧(anvil)GUI 方式が Bedrock/Geyser で
 * どう見えるかをこちらで実機確認できないため。チャット入力なら Java/Bedrock で同じ挙動になる。
 */
public class RecipeBrowserGui extends BaseGui {

    private static final int ITEMS_PER_PAGE = 28; // 4行×7列
    private static final int ITEM_START = 10;
    private static final int BTN_PREV = 45;
    private static final int BTN_SORT = 46;
    private static final int BTN_FILTER = 47;
    private static final int BTN_SEARCH = 48;
    private static final int BTN_CLOSE = 49;
    private static final int BTN_RELATED_CLEAR = 50;
    private static final int BTN_NEXT = 53;
    /** 詳細画面: 完成品スロット（ここをクリックすると「これを使うレシピ」一覧へ）。 */
    private static final int DETAIL_RESULT_SLOT = 15;
    /** 詳細画面: 戻るボタン。 */
    private static final int DETAIL_BACK_SLOT = 49;
    /** 検索をクリアするための入力トークン。 */
    private static final String SEARCH_CLEAR_TOKEN = "-";
    /** チャット検索入力のタイムアウト(tick)。 */
    private static final long SEARCH_TIMEOUT_TICKS = 600L;

    /** 3×3 グリッドのGUIスロット（行×列）。素材クリック判定にも使う。 */
    private static final int[][] GRID_SLOTS = {{10, 11, 12}, {19, 20, 21}, {28, 29, 30}};
    /** TrinityForge カタログレシピの NamespacedKey 接頭辞（TF {@code CatalogRecipeRegistrar} と対）。 */
    private static final String CATALOG_KEY_PREFIX = "catalog_";

    private final List<RecipeEntry> allRecipes;
    /** 現在の並べ替え・絞り込み・検索・関連表示を適用した後の表示対象。 */
    private List<RecipeEntry> visible;
    private int currentPage = 0;

    private RecipeBrowserFilter.SortMode sortMode = RecipeBrowserFilter.SortMode.DEFAULT;
    private RecipeBrowserFilter.FilterMode filterMode = RecipeBrowserFilter.FilterMode.ALL;
    private String searchTerm = "";

    /**
     * 関連レシピ表示（素材/完成品クリック由来の絞り込み）。null なら通常の全件一覧。
     *
     * @param token   突き合わせに使う素材トークン({@code custom:<id>} / Material名)
     * @param label   表示用の日本語名
     * @param usage   true = このアイテムを「使う」レシピ / false = このアイテムを「作る」レシピ
     */
    private record RelatedView(String token, String label, boolean usage) {
    }

    private RelatedView related = null;

    /** 素材ジャンプで潜った詳細画面の履歴（戻るボタンで1つずつ浮上する）。 */
    private final Deque<RecipeEntry> detailHistory = new ArrayDeque<>();

    public RecipeBrowserGui(Player viewer) {
        super(viewer, 6, Component.text("レシピ一覧", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        this.allRecipes = collectAllRecipes();
        this.visible = this.allRecipes;
        refresh();
    }

    /** 並べ替え・絞り込み・検索・関連表示を適用し直す(描画はしない)。 */
    private void refresh() {
        List<RecipeEntry> base = related == null ? allRecipes : relatedEntries(related);
        this.visible = RecipeBrowserFilter.arrange(base, sortMode, filterMode, searchTerm, this::isUnlocked);
        int totalPages = Math.max(1, (int) Math.ceil((double) visible.size() / ITEMS_PER_PAGE));
        if (currentPage > totalPages - 1) {
            currentPage = totalPages - 1;
        }
    }

    @Override
    public void render() {
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        int totalPages = Math.max(1, (int) Math.ceil((double) visible.size() / ITEMS_PER_PAGE));
        currentPage = Math.min(currentPage, totalPages - 1);

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int slot = ITEM_START;
        for (int i = startIndex; i < visible.size() && slot < 44; i++) {
            // 枠を避ける（左右端はスキップ）
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                i--;
                continue;
            }
            RecipeEntry entry = visible.get(i);
            inventory.setItem(slot, createRecipeButton(entry));
            slot++;
        }

        inventory.setItem(BTN_CLOSE, createButton(Material.DARK_OAK_DOOR,
            Component.text("閉じる", NamedTextColor.RED)));
        inventory.setItem(BTN_SORT, createButton(Material.HOPPER,
            Component.text("並べ替え: " + sortMode.label(), NamedTextColor.AQUA),
            List.of(detailText("クリックで切り替え", NamedTextColor.DARK_GRAY),
                detailText("種別=item-stats の使用スキル(無ければ素材)", NamedTextColor.DARK_GRAY))));
        inventory.setItem(BTN_FILTER, createButton(
            filterMode == RecipeBrowserFilter.FilterMode.LOCKED ? Material.IRON_BARS : Material.LIME_DYE,
            Component.text("表示: " + filterMode.label(), NamedTextColor.AQUA),
            List.of(detailText("クリックで切り替え（並べ替えとは独立）", NamedTextColor.DARK_GRAY))));
        inventory.setItem(BTN_SEARCH, createButton(
            searchTerm.isEmpty() ? Material.SPYGLASS : Material.WRITABLE_BOOK,
            Component.text(searchTerm.isEmpty() ? "名前検索" : "検索中: " + searchTerm, NamedTextColor.YELLOW),
            List.of(detailText("クリックしてチャットに入力", NamedTextColor.DARK_GRAY),
                detailText("ワイルドカード: * と ? が使える", NamedTextColor.DARK_GRAY),
                detailText("「" + SEARCH_CLEAR_TOKEN + "」で検索解除", NamedTextColor.DARK_GRAY))));

        if (related != null) {
            inventory.setItem(BTN_RELATED_CLEAR, createButton(Material.BARRIER,
                Component.text("← 全レシピに戻る", NamedTextColor.YELLOW)));
        }

        inventory.setItem(BTN_PREV, currentPage > 0
            ? createButton(Material.ARROW, Component.text("前のページ", NamedTextColor.WHITE))
            : createButton(Material.GRAY_STAINED_GLASS_PANE, Component.text("")));
        inventory.setItem(BTN_NEXT, currentPage < totalPages - 1
            ? createButton(Material.ARROW, Component.text("次のページ", NamedTextColor.WHITE))
            : createButton(Material.GRAY_STAINED_GLASS_PANE, Component.text("")));
        inventory.setItem(4, createHeaderItem(totalPages));
    }

    /** 見出し（ページ番号 + 現在の関連表示/検索状態）。 */
    private ItemStack createHeaderItem(int totalPages) {
        List<Component> lore = new ArrayList<>();
        lore.add(detailText("表示 " + visible.size() + " 件 / 全 " + allRecipes.size() + " 件",
            NamedTextColor.GRAY));
        if (related != null) {
            lore.add(detailText(related.usage()
                ? "「" + related.label() + "」を使うレシピ"
                : "「" + related.label() + "」を作るレシピ", NamedTextColor.LIGHT_PURPLE));
        }
        if (!searchTerm.isEmpty()) {
            lore.add(detailText("検索: " + searchTerm, NamedTextColor.YELLOW));
        }
        lore.add(detailText("並べ替え: " + sortMode.label(), NamedTextColor.DARK_GRAY));
        lore.add(detailText("表示: " + filterMode.label(), NamedTextColor.DARK_GRAY));
        return createButton(Material.PAPER,
            Component.text("ページ " + (currentPage + 1) + " / " + totalPages, NamedTextColor.WHITE),
            lore);
    }

    /** 詳細GUI表示中かどうか */
    private boolean detailMode = false;
    private RecipeEntry detailEntry = null;

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        if (detailMode) {
            return onDetailClick(slot, clicker);
        }

        if (slot == BTN_CLOSE) {
            clicker.closeInventory();
            return true;
        }
        if (slot == BTN_SORT) {
            sortMode = sortMode.next();
            currentPage = 0;
            refresh();
            render();
            return true;
        }
        if (slot == BTN_FILTER) {
            filterMode = filterMode.next();
            currentPage = 0;
            refresh();
            render();
            return true;
        }
        if (slot == BTN_SEARCH) {
            promptSearch(clicker);
            return true;
        }
        if (slot == BTN_RELATED_CLEAR && related != null) {
            related = null;
            currentPage = 0;
            refresh();
            render();
            return true;
        }
        if (slot == BTN_PREV && currentPage > 0) {
            currentPage--;
            render();
            return true;
        }
        int totalPages = Math.max(1, (int) Math.ceil((double) visible.size() / ITEMS_PER_PAGE));
        if (slot == BTN_NEXT && currentPage < totalPages - 1) {
            currentPage++;
            render();
            return true;
        }

        // レシピアイテムクリック → 作業台レシピなら詳細GUI
        RecipeEntry clicked = getEntryAtSlot(slot);
        if (clicked != null && !clicked.isRitual) {
            detailMode = true;
            detailEntry = clicked;
            detailHistory.clear();
            renderDetail();
            return true;
        }
        return true;
    }

    /**
     * 詳細画面のクリック処理。素材スロット→そのアイテムを作るレシピへ、
     * 完成品スロット→それを使うレシピ一覧へ、戻る→履歴を1つ浮上（無ければ一覧へ）。
     */
    private boolean onDetailClick(int slot, Player clicker) {
        if (slot == DETAIL_BACK_SLOT) {
            if (!detailHistory.isEmpty()) {
                detailEntry = detailHistory.pop();
                renderDetail();
            } else {
                detailMode = false;
                detailEntry = null;
                render();
            }
            return true;
        }
        if (detailEntry == null) {
            detailMode = false;
            render();
            return true;
        }
        if (slot == DETAIL_RESULT_SLOT) {
            showUsages(detailEntry.resultToken, clicker);
            return true;
        }
        String ingredient = ingredientTokenAtSlot(detailEntry, slot);
        if (ingredient != null) {
            jumpToProducers(ingredient, clicker);
        }
        return true;
    }

    /** 詳細画面の3×3グリッドのスロットに置かれている素材トークンを返す（無ければ null）。 */
    private String ingredientTokenAtSlot(RecipeEntry entry, int slot) {
        if (!entry.shape.isEmpty()) {
            for (int row = 0; row < entry.shape.size() && row < 3; row++) {
                String rowStr = entry.shape.get(row);
                for (int col = 0; col < rowStr.length() && col < 3; col++) {
                    if (GRID_SLOTS[row][col] != slot) continue;
                    char c = rowStr.charAt(col);
                    if (c == ' ') return null;
                    return entry.ingredientMap.get(String.valueOf(c));
                }
            }
            return null;
        }
        int idx = 0;
        for (String ing : entry.ingredientMap.values()) {
            if (idx >= 9) break;
            if (GRID_SLOTS[idx / 3][idx % 3] == slot) return ing;
            idx++;
        }
        return null;
    }

    /**
     * 素材クリック: そのアイテムを作るレシピへ飛ぶ。
     * 1件だけ（かつ作業台レシピ）ならその詳細を直接開き、複数なら絞り込み一覧を出す。
     */
    private void jumpToProducers(String token, Player clicker) {
        List<RecipeEntry> producers = producersOf(token);
        String label = localize(token);
        if (producers.isEmpty()) {
            clicker.sendMessage(Component.text("「" + label + "」を作るレシピは登録されていません",
                NamedTextColor.GRAY));
            return;
        }
        if (producers.size() == 1 && !producers.get(0).isRitual) {
            if (detailEntry != null) detailHistory.push(detailEntry);
            detailEntry = producers.get(0);
            renderDetail();
            return;
        }
        openRelated(new RelatedView(token, label, false));
    }

    /** 完成品クリック: そのアイテムを素材に使うレシピ一覧へ。 */
    private void showUsages(String token, Player clicker) {
        if (token == null) {
            return;
        }
        String label = localize(token);
        if (usersOf(token).isEmpty()) {
            clicker.sendMessage(Component.text("「" + label + "」を素材に使うレシピはありません",
                NamedTextColor.GRAY));
            return;
        }
        openRelated(new RelatedView(token, label, true));
    }

    /** 関連レシピ表示へ切り替える（詳細モードは抜ける）。 */
    private void openRelated(RelatedView view) {
        related = view;
        detailMode = false;
        detailEntry = null;
        detailHistory.clear();
        currentPage = 0;
        refresh();
        render();
    }

    /** 関連表示の対象レシピ。 */
    private List<RecipeEntry> relatedEntries(RelatedView view) {
        return view.usage() ? usersOf(view.token()) : producersOf(view.token());
    }

    /** {@code token} を作るレシピ。 */
    private List<RecipeEntry> producersOf(String token) {
        List<RecipeEntry> result = new ArrayList<>();
        for (RecipeEntry entry : allRecipes) {
            if (tokenSatisfies(entry.resultToken, token)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** {@code token} を素材に使うレシピ。 */
    private List<RecipeEntry> usersOf(String token) {
        List<RecipeEntry> result = new ArrayList<>();
        for (RecipeEntry entry : allRecipes) {
            for (String ing : entry.ingredientTokens()) {
                if (tokenSatisfies(token, ing)) {
                    result.add(entry);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * {@code candidate}(具体的なアイテム) が {@code requirement}(素材要求) を満たすか。
     * {@code requirement} が {@code list:<id>}(TF素材互換リスト)なら、そのメンバかどうかで判定する。
     */
    private boolean tokenSatisfies(String candidate, String requirement) {
        if (candidate == null || requirement == null) return false;
        if (candidate.equalsIgnoreCase(requirement)) return true;
        if (requirement.startsWith("list:")) {
            return materialListContains(requirement.substring("list:".length()), candidate);
        }
        return false;
    }

    /** TF素材互換リストにトークンが含まれるか。 */
    private boolean materialListContains(String listId, String token) {
        if (token.startsWith("custom:")) {
            Set<String> ids = com.arspaper.integration.TrinityForgeBridge
                .resolveMaterialListCustomIds(listId);
            return ids.contains(token.substring("custom:".length()));
        }
        Material mat = Material.matchMaterial(token);
        return mat != null && com.arspaper.integration.TrinityForgeBridge
            .resolveMaterialList(listId).contains(mat);
    }

    /**
     * このレシピが閲覧者にとって解放済みか。
     * TF未ロード/例外時は fail-open（解放済み扱い）— {@link com.arspaper.recipe.UnlockGate} と同じ方針。
     */
    private boolean isUnlocked(RecipeEntry entry) {
        try {
            com.arspaper.recipe.UnlockGate gate = ArsPaper.getInstance().getUnlockGate();
            if (gate == null) return true;
            return entry.isRitual
                ? gate.hasRitualPermission(viewer, entry.id)
                : gate.hasRecipePermission(viewer, entry.id);
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * チャットでの名前検索入力を受け付ける。
     * {@code SpellSettingsGui} のリネーム入力と同じ形（一時リスナー + タイムアウト自動解除）。
     */
    private void promptSearch(Player player) {
        player.closeInventory();
        player.sendMessage(Component.text("チャットに検索する名前を入力してください", NamedTextColor.YELLOW));
        player.sendMessage(Component.text(
            "ワイルドカード * ? が使えます / 「" + SEARCH_CLEAR_TOKEN + "」で解除 / 「cancel」で中止",
            NamedTextColor.GRAY));

        ArsPaper plugin = ArsPaper.getInstance();
        final java.util.UUID playerUuid = player.getUniqueId();
        final RecipeBrowserGui self = this;

        org.bukkit.event.Listener chatListener = new org.bukkit.event.Listener() {
            private void cleanup() {
                org.bukkit.event.HandlerList.unregisterAll(this);
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(playerUuid)) return;
                event.setCancelled(true);
                // Discord連携プラグイン等への漏洩を防止（受信者を空にする）
                event.viewers().clear();

                String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(event.message()).trim();

                cleanup();

                if (message.equalsIgnoreCase("cancel")) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> reopen(playerUuid, self));
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    self.searchTerm = SEARCH_CLEAR_TOKEN.equals(message) ? "" : message;
                    self.currentPage = 0;
                    self.refresh();
                    reopen(playerUuid, self);
                });
            }

            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                if (event.getPlayer().getUniqueId().equals(playerUuid)) {
                    cleanup();
                }
            }
        };

        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> org.bukkit.event.HandlerList.unregisterAll(chatListener), SEARCH_TIMEOUT_TICKS);
    }

    /** 検索入力後にGUIを開き直す(オフラインなら何もしない)。 */
    private static void reopen(java.util.UUID playerUuid, RecipeBrowserGui gui) {
        Player p = ArsPaper.getInstance().getServer().getPlayer(playerUuid);
        if (p == null || !p.isOnline()) return;
        gui.render();
        p.openInventory(gui.getInventory());
    }

    /**
     * 現在ページのスロット位置からRecipeEntryを取得する。
     */
    private RecipeEntry getEntryAtSlot(int slot) {
        if (slot < ITEM_START || slot >= 44) return null;
        int startIndex = currentPage * ITEMS_PER_PAGE;
        // スロットからインデックスを逆算（枠を考慮）
        int s = ITEM_START;
        for (int i = startIndex; i < visible.size() && s < 44; i++) {
            if (s % 9 == 0 || s % 9 == 8) { s++; i--; continue; }
            if (s == slot) return visible.get(i);
            s++;
        }
        return null;
    }

    /**
     * 作業台レシピの詳細表示: 3×3グリッドにアイテムを実際に配置。
     *
     * レイアウト (6行):
     *   Row 0: [border...] [タイトル] [border...]
     *   Row 1: [_][G][G][G][_][→][結果][_][_]
     *   Row 2: [_][G][G][G][_][_][ 情 ][_][_]
     *   Row 3: [_][G][G][G][_][_][    ][_][_]
     *   Row 4: [border...]
     *   Row 5: [border...] [戻る] [border...]
     */
    private void renderDetail() {
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        RecipeEntry entry = detailEntry;

        // タイトル
        inventory.setItem(4, createButton(Material.CRAFTING_TABLE,
            Component.text(entry.displayName, NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("§a【作業台レシピ】").decoration(TextDecoration.ITALIC, false))));

        // 3×3 グリッド (slots: 10,11,12 / 19,20,21 / 28,29,30)
        int[][] gridSlots = GRID_SLOTS;

        if (!entry.shape.isEmpty()) {
            // Shaped recipe
            for (int row = 0; row < entry.shape.size() && row < 3; row++) {
                String rowStr = entry.shape.get(row);
                for (int col = 0; col < rowStr.length() && col < 3; col++) {
                    char c = rowStr.charAt(col);
                    int guiSlot = gridSlots[row][col];
                    if (c == ' ') {
                        inventory.setItem(guiSlot, null); // 空スロット
                    } else {
                        String ingKey = String.valueOf(c);
                        String ingValue = entry.ingredientMap.get(ingKey);
                        if (ingValue != null) {
                            inventory.setItem(guiSlot, withJumpHint(createIngredientDisplay(ingValue), ingValue));
                        }
                    }
                }
            }
        } else {
            // Shapeless recipe: 左上から順に配置
            int idx = 0;
            for (String ing : entry.ingredientMap.values()) {
                if (idx >= 9) break;
                int row = idx / 3, col = idx % 3;
                inventory.setItem(gridSlots[row][col], withJumpHint(createIngredientDisplay(ing), ing));
                idx++;
            }
        }

        // 矢印 (slot 14)
        inventory.setItem(14, createButton(Material.ARROW,
            Component.text("→", NamedTextColor.WHITE)));

        // 結果アイテム (slot 15)
        if (entry.iconItem != null) {
            ItemStack resultDisplay = entry.iconItem.clone();
            if (entry.amount > 1) resultDisplay.setAmount(entry.amount);
            resultDisplay.editMeta(meta -> {
                List<Component> resultLore = meta.lore() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(meta.lore());
                if (!resultLore.isEmpty()) {
                    resultLore.add(Component.empty());
                }
                if (entry.amount > 1) {
                    resultLore.add(Component.text("完成数: " + entry.amount + "個", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                }
                // 詳細情報を結果アイテムに表示
                appendDetailLore(entry, resultLore);
                if (entry.resultToken != null) {
                    resultLore.add(Component.empty());
                    resultLore.add(detailText("クリック: これを使うレシピ一覧", NamedTextColor.DARK_GRAY));
                }
                if (!resultLore.isEmpty()) meta.lore(resultLore);
            });
            inventory.setItem(DETAIL_RESULT_SLOT, resultDisplay);
        } else {
            ItemStack resultDisplay = new ItemStack(entry.icon);
            if (entry.amount > 1) resultDisplay.setAmount(entry.amount);
            if (entry.resultToken != null) {
                resultDisplay.editMeta(meta -> meta.lore(List.of(
                    detailText("クリック: これを使うレシピ一覧", NamedTextColor.DARK_GRAY))));
            }
            inventory.setItem(DETAIL_RESULT_SLOT, resultDisplay);
        }

        // 素材個数サマリー (slot 24-25)
        inventory.setItem(24, createMaterialSummary(entry));

        // 戻るボタン (slot 49)
        inventory.setItem(DETAIL_BACK_SLOT, createButton(Material.DARK_OAK_DOOR,
            Component.text(detailHistory.isEmpty() ? "← 一覧に戻る" : "← 前のレシピに戻る",
                NamedTextColor.YELLOW),
            List.of(detailText("素材をクリックすると、その素材を作るレシピへ移動します",
                NamedTextColor.DARK_GRAY))));
    }

    /**
     * 素材表示に「クリックでこの素材を作るレシピへ」のヒント行を足す。
     * 生産レシピが1件も無い素材（原材料）には足さない — 押しても何も起きないことを明示するため。
     */
    private ItemStack withJumpHint(ItemStack display, String token) {
        if (display == null || token == null || producersOf(token).isEmpty()) {
            return display;
        }
        display.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(detailText("クリック: この素材を作るレシピへ", NamedTextColor.DARK_GRAY));
            meta.lore(lore);
        });
        return display;
    }

    /**
     * 素材文字列からGUI表示用のItemStackを生成する。
     */
    private ItemStack createIngredientDisplay(String ingredientStr) {
        if (ingredientStr.startsWith("custom:")) {
            String customId = ingredientStr.substring("custom:".length());
            // ArsPaperカスタムを優先し、無ければTrinityForgeカタログアイテムへフォールバック
            // (localize() と対称。以前はTFカタログ素材がBARRIER表示になっていた)。
            ItemStack stack = ArsPaper.getInstance().getItemRegistry().get(customId)
                .map(item -> item.createItemStack())
                .orElseGet(() -> com.arspaper.integration.TrinityForgeBridge.createCatalogIdentity(customId));
            if (stack != null) {
                stack.editMeta(meta -> meta.lore(List.of(
                    Component.text(localize(ingredientStr), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))));
                return stack;
            }
            return createButton(Material.BARRIER, Component.text(customId, NamedTextColor.RED));
        }
        if (ingredientStr.startsWith("list:")) {
            return createMaterialListDisplay(ingredientStr);
        }
        Material mat = Material.matchMaterial(ingredientStr);
        if (mat != null) {
            ItemStack stack = new ItemStack(mat);
            stack.editMeta(meta -> {
                meta.displayName(Component.text(localizeMaterial(mat), NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            });
            return stack;
        }
        return createButton(Material.BARRIER, Component.text(ingredientStr, NamedTextColor.RED));
    }

    /**
     * {@code list:<id>}(TrinityForge 素材互換リスト)の表示用アイテムを生成する。
     * 代表アイコン(先頭Material or 先頭customメンバ)にリスト名と「いずれか1つ」のメンバ一覧を
     * lore で添える。TF未ロード / 未定義リストのときは BARRIER。
     */
    private ItemStack createMaterialListDisplay(String ingredientStr) {
        String listId = ingredientStr.substring("list:".length());
        java.util.Set<Material> mats =
            com.arspaper.integration.TrinityForgeBridge.resolveMaterialList(listId);
        java.util.Set<String> customIds =
            com.arspaper.integration.TrinityForgeBridge.resolveMaterialListCustomIds(listId);

        ItemStack stack = null;
        Material iconMat = mats.stream().findFirst().orElse(null);
        if (iconMat != null) {
            stack = new ItemStack(iconMat);
        } else {
            String firstCustom = customIds.stream().findFirst().orElse(null);
            if (firstCustom != null) {
                stack = ArsPaper.getInstance().getItemRegistry().get(firstCustom)
                    .map(item -> item.createItemStack())
                    .orElseGet(() -> com.arspaper.integration.TrinityForgeBridge.createCatalogIdentity(firstCustom));
            }
        }
        if (stack == null) {
            return createButton(Material.BARRIER, Component.text(ingredientStr, NamedTextColor.RED));
        }

        String label = com.arspaper.integration.TrinityForgeBridge.materialListLabel(listId);
        List<Component> lore = new ArrayList<>();
        lore.add(detailText("互換素材リスト (いずれか1つ)", NamedTextColor.AQUA));
        int shown = 0;
        final int limit = 12;
        boolean truncated = false;
        for (Material m : mats) {
            if (shown >= limit) { truncated = true; break; }
            lore.add(detailText("・" + localizeMaterial(m), NamedTextColor.GRAY));
            shown++;
        }
        for (String cid : customIds) {
            if (shown >= limit) { truncated = true; break; }
            lore.add(detailText("・" + localize("custom:" + cid), NamedTextColor.GRAY));
            shown++;
        }
        if (truncated) {
            lore.add(detailText("…ほか", NamedTextColor.DARK_GRAY));
        }
        ItemStack finalStack = stack;
        finalStack.editMeta(meta -> {
            meta.displayName(Component.text(label, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return finalStack;
    }

    /**
     * 素材個数サマリーをアイテムとして生成する。
     */
    private ItemStack createMaterialSummary(RecipeEntry entry) {
        List<Component> lore = new ArrayList<>();
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();

        if (!entry.shape.isEmpty()) {
            for (String row : entry.shape) {
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (c != ' ') {
                        String ingValue = entry.ingredientMap.get(String.valueOf(c));
                        if (ingValue != null) {
                            counts.merge(localize(ingValue), 1, Integer::sum);
                        }
                    }
                }
            }
        } else {
            for (String ing : entry.ingredientMap.values()) {
                counts.merge(localize(ing), 1, Integer::sum);
            }
        }

        for (var e : counts.entrySet()) {
            lore.add(Component.text(e.getKey() + " ×" + e.getValue(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }

        return createButton(Material.BOOK,
            Component.text("必要素材", NamedTextColor.WHITE), lore);
    }

    private ItemStack createRecipeButton(RecipeEntry entry) {
        Material icon = entry.icon;
        List<Component> lore = new ArrayList<>();

        lore.add(Component.text(entry.isRitual ? "§6【儀式レシピ】" : "§a【作業台レシピ】")
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (entry.isRitual) {
            if (entry.coreItem != null) {
                lore.add(Component.text("コア: " + localize(entry.coreItem), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            // 同一素材を集約表示（「ソースジェム ×4」形式）
            for (var counted : aggregateIngredients(entry.ingredients)) {
                lore.add(Component.text("  " + counted, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (entry.source > 0) {
                lore.add(Component.text("Source: " + entry.source, NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            // 作業台レシピ: 素材個数のみ表示（クリックで配置詳細GUI）
            appendWorkbenchSummaryLore(entry, lore);
            lore.add(Component.empty());
            lore.add(Component.text("クリックで配置を確認", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }

        // 詳細情報を追加
        appendDetailLore(entry, lore);

        // 解放状態(2026-07-27): 絞り込みが「すべて」でも一目で分かるようにする
        if (!isUnlocked(entry)) {
            lore.add(Component.empty());
            lore.add(detailText("✖ 未解放（スキルツリーで解放が必要）", NamedTextColor.RED));
        }
        if (entry.sortLevel > 0) {
            lore.add(detailText("使用可能レベル: " + entry.sortLevel
                + (entry.sortSkill.isBlank() ? "" : " (" + entry.sortSkill + ")"),
                NamedTextColor.DARK_AQUA));
        }

        // iconItemがある場合はそのItemStackベースでボタン生成（革防具の色等を保持）
        if (entry.iconItem != null) {
            ItemStack button = entry.iconItem.clone();
            button.editMeta(meta -> {
                meta.displayName(Component.text(entry.displayName,
                    entry.isRitual ? NamedTextColor.GOLD : NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            return button;
        }
        return createButton(icon,
            Component.text(entry.displayName, entry.isRitual ? NamedTextColor.GOLD : NamedTextColor.GREEN),
            lore);
    }

    private List<RecipeEntry> collectAllRecipes() {
        List<RecipeEntry> entries = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        ArsPaper plugin = ArsPaper.getInstance();
        GlyphConfig glyphConfig = plugin.getGlyphConfig();

        // 儀式レシピ
        Collection<RitualRecipe> rituals = plugin.getRitualRecipeRegistry().getAll();
        for (RitualRecipe recipe : rituals) {
            if (!seenIds.add(recipe.id())) continue; // 重複排除
            RecipeEntry entry = new RecipeEntry();
            entry.id = recipe.id();
            entry.displayName = recipe.resultAmount() > 1
                ? recipe.name() + " ×" + recipe.resultAmount()
                : recipe.name();
            entry.amount = recipe.resultAmount();
            entry.isRitual = true;
            entry.coreItem = recipe.coreItem() != null
                ? (recipe.coreItem().isCustom() ? "custom:" + recipe.coreItem().materialOrCustomId() : recipe.coreItem().materialOrCustomId())
                : null;
            entry.ingredients = recipe.pedestalItems().stream()
                .map(ing -> ing.isCustom() ? "custom:" + ing.materialOrCustomId() : ing.materialOrCustomId())
                .toList();
            entry.source = recipe.sourceRequired();
            entry.resultCustomId = recipe.resultId();
            entry.resultToken = recipe.isCustomResult() && recipe.resultId() != null
                ? "custom:" + recipe.resultId()
                : (recipe.resultMaterial() != null ? recipe.resultMaterial().name() : null);
            entry.effectType = recipe.effectType();
            entry.effectParams = recipe.effectParams();
            if ("thread".equals(recipe.effectType()) && recipe.effectParams().containsKey("thread")) {
                entry.threadId = recipe.effectParams().get("thread");
            }

            // アイコン: 結果アイテムのItemStackを生成（色付き防具等に対応）
            if (recipe.isCustomResult()) {
                var itemOpt = plugin.getItemRegistry().get(recipe.resultId());
                if (itemOpt.isPresent()) {
                    entry.iconItem = itemOpt.get().createItemStack();
                    entry.icon = itemOpt.get().getBaseMaterial();
                } else {
                    entry.icon = Material.ENCHANTED_BOOK;
                }
            } else if (recipe.resultMaterial() != null) {
                entry.icon = recipe.resultMaterial();
            } else {
                // エフェクトタイプ別アイコン
                if ("thread".equals(recipe.effectType()) && recipe.effectParams().containsKey("thread")) {
                    com.arspaper.item.ThreadType tt = com.arspaper.item.ThreadType.fromId(recipe.effectParams().get("thread"));
                    entry.icon = tt != null ? tt.getBaseMaterial() : Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE;
                } else {
                    entry.icon = switch (recipe.effectType()) {
                        case "enchant_book" -> Material.ENCHANTED_BOOK;
                        case "weather" -> {
                            String mode = recipe.effectParams().get("mode");
                            yield switch (mode != null ? mode : "clear") {
                                case "rain" -> Material.WATER_BUCKET;
                                case "thunder" -> Material.LIGHTNING_ROD;
                                default -> Material.SUNFLOWER;
                            };
                        }
                        case "mob_summon" -> {
                            String g = entry.effectParams != null ? entry.effectParams.get("group") : null;
                            yield switch (g != null ? g : "default") {
                                case "raid" -> Material.CROSSBOW;
                                case "nether" -> Material.BLAZE_POWDER;
                                case "variant" -> Material.FERMENTED_SPIDER_EYE;
                                default -> Material.WITHER_SKELETON_SKULL;
                            };
                        }
                        case "animal_summon" -> Material.WHEAT;
                        case "flight" -> Material.FEATHER;
                        case "repair" -> Material.ANVIL;
                        case "moonfall" -> Material.CLOCK;
                        case "sunrise" -> Material.SUNFLOWER;
                        default -> Material.BREWING_STAND;
                    };
                }
            }
            entries.add(entry);
        }

        // 作業台レシピ (Ars RecipeManager 登録分)
        for (Map.Entry<NamespacedKey, Object> recipeEntry : plugin.getRecipeManager().getRegisteredRecipes().entrySet()) {
            RecipeEntry entry = workbenchEntryOf(recipeEntry.getKey().getKey(), recipeEntry.getValue());
            if (entry == null || !seenIds.add(entry.id)) continue; // 重複排除
            entries.add(entry);
        }

        // 作業台レシピ (TrinityForge catalog 登録分)。
        // Bukkit全体のrecipeIteratorでは他プラグインの動的レシピを列挙できない環境があるため、
        // TFのCatalogRecipeRegistrarが持つ登録済みキーを直接取得する。
        for (NamespacedKey key : com.arspaper.integration.TrinityForgeBridge.catalogWorkbenchRecipeKeys()) {
            org.bukkit.inventory.Recipe recipeObj = org.bukkit.Bukkit.getRecipe(key);
            if (recipeObj == null) continue;
            RecipeEntry entry = workbenchEntryOf(key.getKey(), recipeObj);
            if (entry == null || !seenIds.add(entry.id)) continue;
            // custom:素材はMaterialChoiceでは判別できないため、TF側のパース済みspecで上書きする。
            // このときshape(パターン行)も必ずTF spec由来へ揃える: Bukkitの getShape() は
            // サーバ再構築時にパターン文字を振り直すため、config元記号のingredientMapと突き合わせると
            // 全スロットがnull一致=素材が一切描画されない(木の棒すら出ない/ホバー素材リストも空)。
            var tokens = com.arspaper.integration.TrinityForgeBridge.catalogWorkbenchIngredients(key);
            if (tokens != null && !tokens.isEmpty()) {
                entry.ingredientMap = tokens;
                var specShape = com.arspaper.integration.TrinityForgeBridge.catalogWorkbenchShape(key);
                if (specShape != null && !specShape.isEmpty()) {
                    entry.shape = specShape;
                }
            }
            // Bukkit/Valhalla側の結果表示ではなく、items/catalog.yml の固定identity
            // (表示名・CMD・革色・発光・flavor lore)をレシピブラウザの正とする。
            ItemStack catalogDisplay =
                    com.arspaper.integration.TrinityForgeBridge.catalogWorkbenchDisplayItem(key);
            if (catalogDisplay != null) {
                entry.displayName = cleanDisplayName(catalogDisplay);
                entry.iconItem = catalogDisplay;
                entry.icon = catalogDisplay.getType();
            }
            // TFカタログレシピのキーは "catalog_<catalogId>"。素材トークンと同じ語彙へ揃える
            // (Bukkitの結果ItemStackにはカタログidのPDCが載らない経路があるため、キーから起こす)。
            String catalogId = entry.id.startsWith(CATALOG_KEY_PREFIX)
                ? entry.id.substring(CATALOG_KEY_PREFIX.length()) : null;
            if (catalogId != null && !catalogId.isBlank()) {
                entry.resultToken = "custom:" + catalogId;
            }
            entries.add(entry);
        }

        // 並べ替えキー(使用スキル種別 / 使用可能レベル)は、表示アイテムが最終確定した後にまとめて取る。
        for (RecipeEntry entry : entries) {
            applySortKeys(entry);
        }
        return entries;
    }

    /** item-stats の use-skill / use-level を並べ替えキーとして取り込む（未設定なら空/0のまま）。 */
    private void applySortKeys(RecipeEntry entry) {
        ItemStack probe = entry.iconItem;
        if (probe == null && entry.icon != null && entry.icon.isItem()) {
            probe = new ItemStack(entry.icon);
        }
        var gate = com.arspaper.integration.TrinityForgeBridge.itemUseGate(probe);
        if (gate == null) return;
        entry.sortSkill = gate.skill() == null ? "" : gate.skill();
        entry.sortLevel = Math.max(0, gate.level());
    }

    /** 作業台レシピ(Shaped/Shapeless)をRecipeEntryへ変換する。未知タイプはnull。 */
    private RecipeEntry workbenchEntryOf(String id, Object recipeObj) {
        RecipeEntry entry = new RecipeEntry();
        entry.id = id;
        entry.isRitual = false;

        if (recipeObj instanceof ShapedRecipe shaped) {
            entry.displayName = cleanDisplayName(shaped.getResult());
            entry.iconItem = shaped.getResult().clone();
            entry.icon = shaped.getResult().getType();
            entry.amount = shaped.getResult().getAmount();
            entry.shape = List.of(shaped.getShape());
            entry.resultToken = resultTokenOf(shaped.getResult());

            Map<String, String> ingMap = new HashMap<>();
            for (Map.Entry<Character, org.bukkit.inventory.RecipeChoice> choiceEntry : shaped.getChoiceMap().entrySet()) {
                ingMap.put(String.valueOf(choiceEntry.getKey()), describeChoice(choiceEntry.getValue()));
            }
            entry.ingredientMap = ingMap;
            return entry;
        }
        if (recipeObj instanceof ShapelessRecipe shapeless) {
            entry.displayName = cleanDisplayName(shapeless.getResult());
            entry.iconItem = shapeless.getResult().clone();
            entry.icon = shapeless.getResult().getType();
            entry.amount = shapeless.getResult().getAmount();
            entry.resultToken = resultTokenOf(shapeless.getResult());

            Map<String, String> ingMap = new HashMap<>();
            List<org.bukkit.inventory.RecipeChoice> choices = shapeless.getChoiceList();
            for (int i = 0; i < choices.size(); i++) {
                ingMap.put(String.valueOf(i + 1), describeChoice(choices.get(i)));
            }
            entry.ingredientMap = ingMap;
            return entry;
        }
        return null; // 未知のレシピタイプはスキップ
    }

    /**
     * 完成品を素材トークンと同じ語彙({@code custom:<id>} / Material名)へ変換する。
     * {@link #describeChoice} と対称 — 双方が同じ語彙を返すことで素材⇔完成品の突き合わせが成立する。
     */
    private String resultTokenOf(ItemStack result) {
        if (result == null || result.getType().isAir()) return null;
        if (result.hasItemMeta()) {
            String customId = result.getItemMeta().getPersistentDataContainer()
                .get(com.arspaper.item.ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if (customId != null && !customId.isBlank()) {
                return "custom:" + customId;
            }
        }
        return result.getType().name();
    }

    /**
     * レシピの結果アイテムやエフェクトに応じた詳細loreを追加する。
     */
    private void appendDetailLore(RecipeEntry entry, List<Component> lore) {
        ArsPaper plugin = ArsPaper.getInstance();

        // === カスタムアイテム結果の詳細 ===
        if (entry.resultCustomId != null) {
            // スペルブック（spellbooks.ymlのidで直接引く。旧実装のswitch文はconfig駆動化に伴い廃止）
            if (entry.resultCustomId.startsWith("spell_book_")) {
                com.arspaper.item.SpellBookTierData tier = plugin.getSpellBookConfig().byId(entry.resultCustomId);
                if (tier != null) {
                    lore.add(Component.empty());
                    lore.add(detailText("スロット数: " + tier.getMaxSlots(), NamedTextColor.AQUA));
                    lore.add(detailText("グリフTier上限: " + tier.getMaxGlyphTier(), NamedTextColor.AQUA));
                }
            }
            // 防具: TrinityForgeのitem-catalog/item-stats供給に一本化されたため、
            // ArsPaper側の防具詳細lore表示は撤去(防具レシピもArsPaper側では登録しない)。
        }

        // === スレッド詳細 ===
        if (entry.threadId != null) {
            com.arspaper.item.ThreadType threadType = com.arspaper.item.ThreadType.fromId(entry.threadId);
            if (threadType != null && threadType.hasEffect()) {
                lore.add(Component.empty());
                lore.addAll(plugin.getThreadConfig().getEffectLore(threadType));
            }
        }

        // === 儀式エフェクト詳細 ===
        if (entry.effectType != null && !"craft".equals(entry.effectType) && !"thread".equals(entry.effectType)) {
            lore.add(Component.empty());
            String desc = resolveEffectDescription(entry);
            if (desc != null) {
                lore.add(detailText(desc, NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    /**
     * 儀式エフェクトタイプに応じた説明文を返す。
     */
    private String resolveEffectDescription(RecipeEntry entry) {
        return switch (entry.effectType) {
            case "weather" -> {
                String mode = entry.effectParams != null ? entry.effectParams.get("mode") : null;
                yield switch (mode != null ? mode : "clear") {
                    case "rain" -> "コアに水入りバケツを置いて天候を雨に変更";
                    case "thunder" -> "コアに避雷針を置いて天候を雷雨に変更";
                    default -> "コアにひまわりを置いて天候を晴れに変更";
                };
            }
            case "flight" -> {
                int seconds = 300;
                if (entry.effectParams != null) {
                    String dur = entry.effectParams.get("duration");
                    if (dur != null) {
                        try { seconds = Integer.parseInt(dur); } catch (NumberFormatException ignored) {}
                    }
                }
                int minutes = seconds / 60;
                yield minutes + "分間のクリエイティブ飛行を付与";
            }
            case "moonfall" -> "時刻を夜（13000tick）に変更";
            case "sunrise" -> "時刻を朝（0tick）に変更";
            case "repair" -> "コアに修復対象の装備を置いて耐久値を全回復";
            case "animal_summon" -> {
                int count = 5;
                if (entry.effectParams != null) {
                    String c = entry.effectParams.get("count");
                    if (c != null) {
                        try { count = Integer.parseInt(c); } catch (NumberFormatException ignored) {}
                    }
                }
                yield "コア周囲に友好動物を" + count + "体召喚";
            }
            case "mob_summon" -> {
                int count = 3;
                if (entry.effectParams != null) {
                    String c = entry.effectParams.get("count");
                    if (c != null) {
                        try { count = Integer.parseInt(c); } catch (NumberFormatException ignored) {}
                    }
                }
                String group = entry.effectParams != null ? entry.effectParams.get("group") : null;
                String groupName = switch (group != null ? group : "default") {
                    case "raid" -> "襲撃";
                    case "nether" -> "ネザー";
                    case "variant" -> "変異";
                    default -> "敵";
                };
                yield "コア周囲に" + groupName + "モブを" + count + "体召喚";
            }
            case "enchant_book" -> "コアの本をカスタムエンチャント本に変換";
            default -> null;
        };
    }

    /**
     * 素材リストの重複を集約して「素材名 ×個数」形式のリストを返す。
     */
    private List<String> aggregateIngredients(List<String> ingredients) {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String ing : ingredients) {
            String name = localize(ing);
            counts.merge(name, 1, Integer::sum);
        }
        List<String> result = new ArrayList<>();
        for (var e : counts.entrySet()) {
            result.add(e.getValue() > 1 ? e.getKey() + " ×" + e.getValue() : e.getKey());
        }
        return result;
    }

    /**
     * 作業台レシピの素材個数サマリーをloreに追加する（一覧表示用、簡潔版）。
     */
    private void appendWorkbenchSummaryLore(RecipeEntry entry, List<Component> lore) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (!entry.shape.isEmpty()) {
            for (String row : entry.shape) {
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (c != ' ') {
                        String ingValue = entry.ingredientMap.get(String.valueOf(c));
                        if (ingValue != null) counts.merge(localize(ingValue), 1, Integer::sum);
                    }
                }
            }
        }
        if (counts.isEmpty()) {
            for (String ing : entry.ingredientMap.values()) {
                counts.merge(localize(ing), 1, Integer::sum);
            }
        }
        for (var e : counts.entrySet()) {
            lore.add(Component.text("  " + e.getKey() + " ×" + e.getValue(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        if (entry.amount > 1) {
            lore.add(Component.text("完成数: " + entry.amount + "個", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        }
    }

    private static Component detailText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private String localize(String materialOrCustom) {
        if (materialOrCustom == null) return "不明";

        // 素材互換リスト: TF の list:<id> をラベル + 「(いずれか)」で表示する。
        if (materialOrCustom.startsWith("list:")) {
            String listId = materialOrCustom.substring("list:".length());
            return com.arspaper.integration.TrinityForgeBridge.materialListLabel(listId) + " (いずれか)";
        }

        // カスタムアイテム: レジストリから表示名を取得（Ars未登録ならTFカタログ表示名へフォールバック）
        if (materialOrCustom.startsWith("custom:")) {
            String customId = materialOrCustom.substring("custom:".length());
            return ArsPaper.getInstance().getItemRegistry().get(customId)
                .map(item -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.resolveDisplayName()))
                .orElseGet(() -> {
                    String tfName = com.arspaper.integration.TrinityForgeBridge.catalogDisplayNamePlain(customId);
                    return tfName != null ? tfName : customId;
                });
        }

        // バニラ素材: Material名を日本語化
        Material mat = Material.matchMaterial(materialOrCustom);
        if (mat != null) {
            return localizeMaterial(mat);
        }
        return materialOrCustom;
    }

    /**
     * ItemStackの表示名を取得する。カスタム名があればそれを使い、
     * なければMaterialから日本語名を取得。[brackets]を除去する。
     */
    private String cleanDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
            return name.replaceAll("^\\[|\\]$", "");
        }
        return com.arspaper.util.JaTranslations.translate(item.getType());
    }

    private String localizeMaterial(Material mat) {
        return com.arspaper.util.JaTranslations.translate(mat);
    }

    private String describeChoice(org.bukkit.inventory.RecipeChoice choice) {
        if (choice instanceof org.bukkit.inventory.RecipeChoice.ExactChoice exact) {
            ItemStack item = exact.getChoices().get(0);
            String customId = item.getPersistentDataContainer()
                .get(com.arspaper.item.ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if (customId != null) {
                return "custom:" + customId;
            }
            // バニラ素材: Material名を返す（localize()で日本語化される）
            return item.getType().name();
        } else if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice matChoice) {
            return matChoice.getChoices().get(0).name();
        }
        return "UNKNOWN";
    }

}
