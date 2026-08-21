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
 * {@code /tf recipes} で開く(2026-07-28: 入口を TrinityForge 側へ一本化し、{@code /ars recipes} は
 * 削除した。TF は ArsPaper にコンパイル依存できないため、TF 側は
 * {@code com.trinityforge.integration.ars.ArsRecipeBrowserBridge} からリフレクションで
 * このクラスを生成し {@code open()} を呼ぶ — <b>コンストラクタと open() のシグネチャを変えると
 * TF 側が無言で fail-soft に落ちる</b>ので注意)。
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
 * <p><b>2026-07-28 改修（儀式レシピの詳細画面）:</b> 以前は一覧で儀式レシピをクリックしても
 * 何も起きず、素材は lore の名前だけで実アイテムのプレビューが見られなかった。儀式にも
 * 詳細画面（{@link #renderRitualDetail()}）を用意し、中央=コア・周囲8マス=ペデスタル素材を
 * 実アイテムで並べる。素材⇔レシピの相互ジャンプも作業台と同じように働く。
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
    /** 圧縮の中間段階・解凍レシピを出すかのトグル (2026-08-19 W-122 / W-99)。 */
    private static final int BTN_COMPRESSION = 51;
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
    /** 儀式詳細: 中央=コア。周囲8マスにペデスタル素材を（同一素材は集約して）並べる。 */
    private static final int RITUAL_CORE_SLOT = 20;
    private static final int[] RITUAL_PEDESTAL_SLOTS = {10, 11, 12, 19, 21, 28, 29, 30};
    /** 儀式詳細: 必要Source表示スロット。 */
    private static final int RITUAL_SOURCE_SLOT = 33;
    /** TrinityForge カタログレシピの NamespacedKey 接頭辞（TF {@code CatalogRecipeRegistrar} と対）。 */
    private static final String CATALOG_KEY_PREFIX = "catalog_";

    private final List<RecipeEntry> allRecipes;
    /** 現在の並べ替え・絞り込み・検索・関連表示を適用した後の表示対象。 */
    private List<RecipeEntry> visible;
    private int currentPage = 0;

    // 既定の並び順は名前順(2026-07-30 ユーザー確定)。登録順は巡回の最後に置いてある。
    private RecipeBrowserFilter.SortMode sortMode = RecipeBrowserFilter.SortMode.NAME;
    private RecipeBrowserFilter.KindMode kindMode = RecipeBrowserFilter.KindMode.ALL;
    private String searchTerm = "";

    /**
     * 圧縮の中間段階と解凍レシピを表示するか (2026-08-19 W-122 / W-99 ユーザー確定「最大倍率の
     * 1つだけを出し、中間レシピと解凍レシピはオプションで on/off、既定 off」)。
     */
    private boolean showCompressionDetails = false;

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

    /**
     * 詳細画面で「どのスロットにどの素材トークンを描いたか」。作業台/儀式のどちらの詳細でも
     * 描画時にここへ記録し、クリック判定は必ずこのマップだけを見る（描画とクリック判定で
     * スロット計算を二重に持つと、儀式のように配置ルールが違う画面を足したときに必ずズレるため）。
     */
    private final Map<Integer, String> detailSlotTokens = new HashMap<>();

    /**
     * 並べ替え/絞り込みをプレイヤーへ保存する PDC キー(2026-08-18 W-97 実サーバ報告
     * 「以前設定していたレシピ・図鑑のソートの記憶保持をするようにしてほしい」)。
     *
     * <p><b>enum の {@code name()} で保存する</b>(ordinal ではない)。ordinal だと
     * {@code SortMode}/{@code KindMode} に定数を1つ挿しただけで、保存済みの全プレイヤーの設定が
     * 無言で別の並び順に化ける。
     */
    private static final String PREF_SORT = "recipe_browser_sort";
    private static final String PREF_KIND = "recipe_browser_kind";
    /** 圧縮の中間段階トグル。未設定 = 既定(隠す)。 */
    private static final String PREF_COMPRESSION = "recipe_browser_compression";

    public RecipeBrowserGui(Player viewer) {
        super(viewer, 6, Component.text("レシピ一覧", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        this.allRecipes = collectAllRecipes();
        this.visible = this.allRecipes;
        // 前回の並べ替え/絞り込みを引き継ぐ。検索語は持ち越さない ——
        // 開くたびに前回の検索で絞られていると「レシピが消えた」ようにしか見えないため。
        this.sortMode = readPreference(viewer, PREF_SORT,
            RecipeBrowserFilter.SortMode.class, RecipeBrowserFilter.SortMode.NAME);
        this.kindMode = readPreference(viewer, PREF_KIND,
            RecipeBrowserFilter.KindMode.class, RecipeBrowserFilter.KindMode.ALL);
        this.showCompressionDetails = readFlag(viewer, PREF_COMPRESSION);
        refresh();
    }

    private static org.bukkit.NamespacedKey prefKey(String name) {
        return new org.bukkit.NamespacedKey(com.arspaper.ArsPaper.getInstance(), name);
    }

    /** 保存済みの設定。未設定 / 削除された定数が残っていた場合は {@code fallback}。 */
    private static <E extends Enum<E>> E readPreference(Player viewer, String name,
                                                        Class<E> type, E fallback) {
        try {
            String stored = viewer.getPersistentDataContainer()
                .get(prefKey(name), org.bukkit.persistence.PersistentDataType.STRING);
            return stored == null ? fallback : Enum.valueOf(type, stored);
        } catch (RuntimeException ex) {
            return fallback; // 削除された定数が残っていた / PDC を読めなかった
        }
    }

    /** 保存済みの真偽トグル。未設定 / 読めなかった場合は false(＝既定)。 */
    private static boolean readFlag(Player viewer, String name) {
        try {
            String stored = viewer.getPersistentDataContainer()
                .get(prefKey(name), org.bukkit.persistence.PersistentDataType.STRING);
            return "true".equals(stored);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** 現在の並べ替え/絞り込みを保存する。次に開いたときの初期値になる。 */
    private void rememberPreferences() {
        try {
            var pdc = viewer.getPersistentDataContainer();
            pdc.set(prefKey(PREF_SORT), org.bukkit.persistence.PersistentDataType.STRING,
                sortMode.name());
            pdc.set(prefKey(PREF_KIND), org.bukkit.persistence.PersistentDataType.STRING,
                kindMode.name());
            pdc.set(prefKey(PREF_COMPRESSION), org.bukkit.persistence.PersistentDataType.STRING,
                Boolean.toString(showCompressionDetails));
        } catch (RuntimeException ignored) {
            // 保存できなくても一覧の表示は続ける(記憶は利便性であって機能ではない)。
        }
    }

    /** 並べ替え・絞り込み・検索・関連表示を適用し直す(描画はしない)。 */
    private void refresh() {
        List<RecipeEntry> base = related == null ? allRecipes : relatedEntries(related);
        this.visible = RecipeBrowserFilter.arrange(base, sortMode, kindMode, searchTerm,
            showCompressionDetails);
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
            sortLore(sortMode)));
        inventory.setItem(BTN_FILTER, createButton(kindIcon(kindMode),
            Component.text("表示: " + kindMode.label(), NamedTextColor.AQUA),
            kindLore(kindMode)));
        inventory.setItem(BTN_SEARCH, createButton(
            searchTerm.isEmpty() ? Material.SPYGLASS : Material.WRITABLE_BOOK,
            Component.text(searchTerm.isEmpty() ? "名前検索" : "検索中: " + searchTerm, NamedTextColor.YELLOW),
            List.of(detailText("クリックしてチャットに入力", NamedTextColor.DARK_GRAY),
                detailText("ワイルドカード: * と ? が使える", NamedTextColor.DARK_GRAY),
                detailText("「" + SEARCH_CLEAR_TOKEN + "」で検索解除", NamedTextColor.DARK_GRAY))));

        inventory.setItem(BTN_COMPRESSION, createButton(
            showCompressionDetails ? Material.PISTON : Material.STICKY_PISTON,
            Component.text(showCompressionDetails ? "圧縮: 全段を表示" : "圧縮: 最大倍率のみ",
                NamedTextColor.AQUA),
            compressionLore(showCompressionDetails)));

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
        lore.add(detailText("表示: " + kindMode.label(), NamedTextColor.DARK_GRAY));
        if (!showCompressionDetails) {
            lore.add(detailText("圧縮: 最大倍率のみ(中間段と解凍は非表示)", NamedTextColor.DARK_GRAY));
        }
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
            rememberPreferences();
            refresh();
            render();
            return true;
        }
        if (slot == BTN_FILTER) {
            kindMode = kindMode.next();
            currentPage = 0;
            rememberPreferences();
            refresh();
            render();
            return true;
        }
        if (slot == BTN_COMPRESSION) {
            showCompressionDetails = !showCompressionDetails;
            currentPage = 0;
            rememberPreferences();
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

        // レシピアイテムクリック → 詳細GUI（2026-07-28: 儀式レシピもここへ入る。以前は
        // 儀式だけ弾いていたため「クリックしても素材アイテムのプレビューが出ない」状態だった）
        RecipeEntry clicked = getEntryAtSlot(slot);
        if (clicked != null) {
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
        String ingredient = detailSlotTokens.get(slot);
        if (ingredient != null) {
            jumpToProducers(ingredient, clicker);
        }
        return true;
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
        if (producers.size() == 1) {
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
            // 醸造は Ars の UnlockGate ではなく TF のスキルツリー(brew:<groupId>)が門番。
            if (entry.isBrewing) {
                return com.arspaper.integration.TrinityForgeBridge
                    .brewGroupUnlocked(viewer, entry.brewGroupId);
            }
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
        detailSlotTokens.clear();
        if (detailEntry != null && detailEntry.isRitual) {
            renderRitualDetail();
            return;
        }
        if (detailEntry != null && detailEntry.isBrewing) {
            renderBrewingDetail();
            return;
        }
        renderWorkbenchDetail();
    }

    /**
     * 醸造レシピの詳細表示 (W-167, 2026-08-20)。醸造台の並びをそのまま写す:
     * 上段に素材、下段にベースのポーション、右に完成品。
     *
     * <p>3×3 グリッドは使わない。醸造台は「上段1 + 下段3」で、作業台の格子に落とすと
     * どこに何を置くのか却って読めなくなるため。
     *
     * レイアウト (6行):
     *   Row 1: [_][_][素材][_][_][→][結果][_][_]
     *   Row 3: [_][_][ベース][_][_][情報][_][_][_]
     *   Row 5: [border...] [戻る] [border...]
     */
    private void renderBrewingDetail() {
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        RecipeEntry entry = detailEntry;

        inventory.setItem(4, createButton(Material.BREWING_STAND,
            Component.text(entry.displayName, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("§d【醸造レシピ】").decoration(TextDecoration.ITALIC, false),
                detailText("醸造台の上段に素材、下段にベースのポーションを置く", NamedTextColor.DARK_GRAY))));

        // 素材(上段スロット)
        if (entry.brewIngredient != null && !entry.brewIngredient.isBlank()) {
            ItemStack ingredient = createIngredientDisplay(entry.brewIngredient);
            if (ingredient != null) {
                ingredient.editMeta(meta -> {
                    List<Component> lore = meta.lore() == null
                        ? new ArrayList<>() : new ArrayList<>(meta.lore());
                    lore.add(detailText("醸造台の上段に置く", NamedTextColor.YELLOW));
                    meta.lore(lore);
                });
                inventory.setItem(11, withJumpHint(ingredient, entry.brewIngredient));
                detailSlotTokens.put(11, entry.brewIngredient);
            }
        }

        // ベースのポーション(下段スロット)
        inventory.setItem(29, brewBaseDisplay(entry.brewBase));

        inventory.setItem(14, createButton(Material.ARROW, Component.text("→", NamedTextColor.WHITE)));
        inventory.setItem(DETAIL_RESULT_SLOT, resultDisplayOf(entry));

        inventory.setItem(DETAIL_BACK_SLOT, createButton(Material.DARK_OAK_DOOR,
            Component.text(detailHistory.isEmpty() ? "← 一覧に戻る" : "← 前のレシピに戻る",
                NamedTextColor.YELLOW),
            List.of(detailText("素材をクリックすると、その素材を作るレシピへ移動します",
                NamedTextColor.DARK_GRAY))));
    }

    /**
     * ベースのポーション({@code THICK} / {@code MUNDANE} 等)の表示アイテム。
     * 名前は翻訳キーを使わず {@code PotionType} 名を日本語へ落とす
     * ({@code item.minecraft.potion.effect.<名前>} は効果の無いベースには訳語が無い)。
     */
    private ItemStack brewBaseDisplay(String baseName) {
        ItemStack bottle = new ItemStack(Material.POTION);
        String label = brewBaseLabel(baseName);
        bottle.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.PotionMeta potionMeta && baseName != null) {
                try {
                    potionMeta.setBasePotionType(
                        org.bukkit.potion.PotionType.valueOf(baseName.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // 未知のベース名。瓶の見た目だけ出して名前で補う。
                }
            }
            meta.displayName(Component.text(label, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(detailText("醸造台の下段に置く", NamedTextColor.YELLOW)));
        });
        return bottle;
    }

    private static String brewBaseLabel(String baseName) {
        if (baseName == null) return "ベースのポーション";
        return switch (baseName.trim().toUpperCase()) {
            // 2026-08-21 実サーバ報告「醸造レシピ表示で翻訳が間違っている」。
            // ここはバニラ ja_jp の item.minecraft.potion.effect.<名前> と同じ語でなければ、
            // 同じ瓶がレシピ画面とインベントリで別名になる(旧: MUNDANE=ただの水 / THICK=濃厚な水)。
            case "WATER" -> "水入り瓶";
            case "MUNDANE" -> "ありふれたポーション";
            case "THICK" -> "濃厚なポーション";
            case "AWKWARD" -> "奇妙なポーション";
            default -> baseName;
        };
    }

    /**
     * 儀式レシピの詳細表示(2026-07-28 新設): 中央にコアアイテム、その周囲8マスに
     * ペデスタル素材を「同一素材は集約(個数=スタック数)」して実アイテムで並べる。
     *
     * <p>作業台レシピと同じく素材クリックで生産レシピへ飛べる。一覧のloreだけでは
     * 「どのアイテムか」が名前でしか分からず、custom素材だと実物と結び付かなかったため、
     * 儀式にも実アイテムのプレビューを与えるのがこの画面の目的。
     *
     * レイアウト (6行):
     *   Row 1: [_][P][P][P][_][→][結果][_][_]
     *   Row 2: [_][P][コア][P][_][_][_][_][_]
     *   Row 3: [_][P][P][P][_][必要素材][_][Source][_]
     *   Row 5: [border...] [戻る] [border...]
     */
    private void renderRitualDetail() {
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        RecipeEntry entry = detailEntry;

        inventory.setItem(4, createButton(Material.BREWING_STAND,
            Component.text(entry.displayName, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("§6【儀式レシピ】").decoration(TextDecoration.ITALIC, false),
                detailText("儀式コアの周囲(距離2)のペデスタルに素材を置いて発動", NamedTextColor.DARK_GRAY))));

        // コアアイテム(無い儀式もある: コア不要レシピ)
        if (entry.coreItem != null) {
            ItemStack core = createIngredientDisplay(entry.coreItem);
            if (core != null) {
                core.editMeta(meta -> {
                    List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                    lore.add(detailText("儀式コアに置く", NamedTextColor.YELLOW));
                    meta.lore(lore);
                });
                inventory.setItem(RITUAL_CORE_SLOT, withJumpHint(core, entry.coreItem));
                detailSlotTokens.put(RITUAL_CORE_SLOT, entry.coreItem);
            }
        } else {
            inventory.setItem(RITUAL_CORE_SLOT, createButton(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Component.text("コアアイテム不要", NamedTextColor.GRAY)));
        }

        // ペデスタル素材(同一素材は集約してスタック数で表現)
        Map<String, Integer> pedestals = aggregateCounts(entry.ingredients);
        int index = 0;
        int overflow = 0;
        for (Map.Entry<String, Integer> counted : pedestals.entrySet()) {
            if (index >= RITUAL_PEDESTAL_SLOTS.length) {
                overflow++;
                continue;
            }
            String token = counted.getKey();
            int amount = counted.getValue();
            ItemStack display = createIngredientDisplay(token);
            if (display == null) {
                continue;
            }
            display.setAmount(Math.max(1, Math.min(64, amount)));
            display.editMeta(meta -> {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(detailText("ペデスタルに " + amount + " 個", NamedTextColor.AQUA));
                meta.lore(lore);
            });
            int slot = RITUAL_PEDESTAL_SLOTS[index];
            inventory.setItem(slot, withJumpHint(display, token));
            detailSlotTokens.put(slot, token);
            index++;
        }

        // 矢印 + 結果
        inventory.setItem(14, createButton(Material.ARROW, Component.text("→", NamedTextColor.WHITE)));
        inventory.setItem(DETAIL_RESULT_SLOT, resultDisplayOf(entry));

        // 必要素材サマリー(コア + ペデスタル全量)。8種を超えた分はここにだけ載る。
        inventory.setItem(24, createRitualSummary(entry, pedestals, overflow));

        if (entry.source > 0) {
            inventory.setItem(RITUAL_SOURCE_SLOT, createButton(Material.AMETHYST_SHARD,
                Component.text("必要Source: " + entry.source, NamedTextColor.AQUA),
                List.of(detailText("周囲のソースジャーから供給される", NamedTextColor.DARK_GRAY))));
        }

        inventory.setItem(DETAIL_BACK_SLOT, createButton(Material.DARK_OAK_DOOR,
            Component.text(detailHistory.isEmpty() ? "← 一覧に戻る" : "← 前のレシピに戻る",
                NamedTextColor.YELLOW),
            List.of(detailText("素材をクリックすると、その素材を作るレシピへ移動します",
                NamedTextColor.DARK_GRAY))));
    }

    /** 儀式詳細の「必要素材」まとめ(コア + ペデスタル + Source)。 */
    private ItemStack createRitualSummary(RecipeEntry entry, Map<String, Integer> pedestals, int overflow) {
        List<Component> lore = new ArrayList<>();
        if (entry.coreItem != null) {
            lore.add(Component.text("コア: " + localize(entry.coreItem), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }
        for (Map.Entry<String, Integer> counted : pedestals.entrySet()) {
            lore.add(Component.text(localize(counted.getKey()) + " ×" + counted.getValue(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        if (entry.source > 0) {
            lore.add(Component.text("Source: " + entry.source, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        }
        if (overflow > 0) {
            lore.add(detailText("※ 種類が多いため、盤面には8種類までしか表示できません",
                NamedTextColor.DARK_GRAY));
        }
        return createButton(Material.BOOK, Component.text("必要素材", NamedTextColor.WHITE), lore);
    }

    /** 素材トークンの並びを「トークン → 個数」へ集約する(順序は初出順を保つ)。 */
    private static Map<String, Integer> aggregateCounts(List<String> tokens) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (tokens == null) {
            return counts;
        }
        for (String token : tokens) {
            if (token != null) {
                counts.merge(token, 1, Integer::sum);
            }
        }
        return counts;
    }

    private void renderWorkbenchDetail() {
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
                            detailSlotTokens.put(guiSlot, ingValue);
                        }
                    }
                }
            }
        } else {
            // Shapeless recipe: 左上から順に配置(記号キー昇順 = config に書いた順)
            int idx = 0;
            for (String ing : RecipeShapeNormalizer.orderedIngredients(entry.ingredientMap)) {
                if (idx >= 9) break;
                int row = idx / 3, col = idx % 3;
                inventory.setItem(gridSlots[row][col], withJumpHint(createIngredientDisplay(ing), ing));
                detailSlotTokens.put(gridSlots[row][col], ing);
                idx++;
            }
        }

        // 矢印 (slot 14)
        inventory.setItem(14, createButton(Material.ARROW,
            Component.text("→", NamedTextColor.WHITE)));

        // 結果アイテム (slot 15)
        inventory.setItem(DETAIL_RESULT_SLOT, resultDisplayOf(entry));

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
     * 詳細画面の完成品スロットに置く表示アイテム(作業台/儀式で共通)。
     * 完成数・効果詳細lore・「クリック: これを使うレシピ一覧」の案内を載せる。
     */
    private ItemStack resultDisplayOf(RecipeEntry entry) {
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
            return resultDisplay;
        }
        ItemStack resultDisplay = new ItemStack(entry.icon);
        if (entry.amount > 1) resultDisplay.setAmount(entry.amount);
        List<Component> lore = new ArrayList<>();
        appendDetailLore(entry, lore);
        if (entry.resultToken != null) {
            lore.add(detailText("クリック: これを使うレシピ一覧", NamedTextColor.DARK_GRAY));
        }
        if (!lore.isEmpty()) {
            resultDisplay.editMeta(meta -> meta.lore(lore));
        }
        return resultDisplay;
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
            for (String ing : RecipeShapeNormalizer.orderedIngredients(entry.ingredientMap)) {
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

        lore.add(Component.text(kindTag(entry)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (entry.isBrewing) {
            lore.add(Component.text("ベース: " + brewBaseLabel(entry.brewBase), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            if (entry.brewIngredient != null && !entry.brewIngredient.isBlank()) {
                lore.add(Component.text("素材: " + localize(entry.brewIngredient), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("クリックで配置を確認", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        } else if (entry.isRitual) {
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
            lore.add(Component.empty());
            lore.add(Component.text("クリックで配置を確認", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
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
                meta.displayName(Component.text(entry.displayName, kindColor(entry))
                    .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            return button;
        }
        return createButton(icon,
            Component.text(entry.displayName, kindColor(entry)),
            lore);
    }

    /** 一覧 lore の先頭に出す種別の見出し。 */
    private static String kindTag(RecipeEntry entry) {
        if (entry.isBrewing) return "§d【醸造レシピ】";
        return entry.isRitual ? "§6【儀式レシピ】" : "§a【作業台レシピ】";
    }

    /** 種別ごとの表示名の色。見出しの色と必ず揃える。 */
    private static NamedTextColor kindColor(RecipeEntry entry) {
        if (entry.isBrewing) return NamedTextColor.LIGHT_PURPLE;
        return entry.isRitual ? NamedTextColor.GOLD : NamedTextColor.GREEN;
    }

    /**
     * TF の醸造レシピ 1 件を一覧エントリへ落とす (W-167, 2026-08-20)。
     *
     * <p>表示名はバニラの翻訳キーではなく {@link com.arspaper.util.JaTranslations#translateEffect}
     * で作る。{@code entry.displayName} は<b>検索・並べ替えに使う素の文字列</b>なので、
     * 翻訳コンポーネントのままにすると「幸運」で検索しても引っかからない。
     */
    private RecipeEntry brewEntryOf(com.arspaper.integration.TrinityForgeBridge.BrewRecipeView brew) {
        RecipeEntry entry = new RecipeEntry();
        entry.id = brew.id();
        entry.isBrewing = true;
        entry.brewBase = brew.base();
        entry.brewIngredient = brew.ingredient();
        entry.brewGroupId = brew.groupId();
        entry.brewDurationTicks = brew.durationTicks();
        entry.amount = 1;
        entry.icon = Material.POTION;
        entry.iconItem = brew.result();
        entry.displayName = brewDisplayName(brew);
        // 「この素材を使うレシピ」からの逆引きに乗せるため、素材トークンを作業台と同じ語彙で持たせる。
        entry.ingredientMap = brew.ingredient() == null || brew.ingredient().isBlank()
            ? Map.of()
            : Map.of("i", brew.ingredient());
        return entry;
    }

    /** 「幸運のポーション II」の形。効果レベルは 0 始まりなので +1 してローマ数字にする(I は付けない)。 */
    private String brewDisplayName(com.arspaper.integration.TrinityForgeBridge.BrewRecipeView brew) {
        org.bukkit.potion.PotionEffectType type = brew.effectKey() == null
            ? null
            : org.bukkit.Registry.EFFECT.get(NamespacedKey.fromString(brew.effectKey()));
        String base = com.arspaper.util.JaTranslations.translateEffect(type) + "のポーション";
        String level = roman(brew.amplifier() + 1);
        return level.isEmpty() ? base : base + " " + level;
    }

    /** 1..10 のローマ数字。範囲外はアラビア数字のまま返す(表示が壊れるより読めるほうがよい)。 */
    private static String roman(int value) {
        return switch (value) {
            case 1 -> "";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> value > 0 ? String.valueOf(value) : "";
        };
    }

    /** 効果時間を {@code m:ss} で表す。0 以下(即時回復など)は null＝行を出さない。 */
    private static String brewDurationLabel(int durationTicks) {
        if (durationTicks <= 0) return null;
        int seconds = durationTicks / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
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
                } else if (!applyCatalogRitualIdentity(entry, recipe)) {
                    entry.icon = Material.ENCHANTED_BOOK;
                }
            } else if (recipe.resultMaterial() != null) {
                entry.icon = recipe.resultMaterial();
            } else {
                // エフェクトタイプ別アイコン
                if ("thread".equals(recipe.effectType()) && recipe.effectParams().containsKey("thread")) {
                    com.arspaper.item.ThreadType tt = com.arspaper.item.ThreadType.fromId(recipe.effectParams().get("thread"));
                    entry.icon = tt != null ? tt.getBaseMaterial() : Material.STRING;
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
                        // 2026-08-19 W-123: 既定の醸造台アイコンだと「枠拡張の儀式」だと分からない。
                        // スレッド枠は装備を鍛冶台で強化する感覚なので鍛冶台にする。
                        case "thread_slot_expand" -> Material.SMITHING_TABLE;
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
            applyForwardSpecTokens(recipeEntry.getKey(), entry);
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

        // 醸造レシピ (TrinityForge brew-unlocks 登録分)。W-167(2026-08-20)。
        // 醸造台は Bukkit の Recipe ではなく Paper の PotionMix なので recipeIterator には一切出ない。
        // TF が「実際に登録できた」計画だけを貰う(登録できなかった組は醸造自体が始まらない)。
        for (var brew : com.arspaper.integration.TrinityForgeBridge.brewRecipes()) {
            if (!seenIds.add(brew.id())) continue;
            entries.add(brewEntryOf(brew));
        }

        // 並べ替えキー(使用スキル種別 / 使用可能レベル)は、表示アイテムが最終確定した後にまとめて取る。
        for (RecipeEntry entry : entries) {
            applySortKeys(entry);
            // 素材を1つも指していない shape はここで捨てる。詳細画面は shape が空でなければ
            // 必ず格子描画へ倒し、引けなかったマスは黙って空欄にするので、記号が全滅した shape が
            // 1本混ざるだけで「素材欄が空のレシピ」が出来上がる(2026-08-21 コア系3種の実バグ)。
            // 供給元が3経路(Ars登録 / TFカタログ / 元configのspec上書き)あるので、
            // 個々の経路ではなく全部が合流したここで1度だけ整える。
            entry.shape = RecipeShapeNormalizer.usableShape(entry.shape, entry.ingredientMap);
            // 表示名の書式はここが最後の砦。displayName は yml(レガシー &記法) / TFカタログ
            // (MiniMessage) / ItemStack の3経路から来るので、描画側で Component.text() に渡す前に
            // 必ずプレーン化する ―― 1経路でも生記号が残ると画面にそのまま出る(2026-08-03 実バグ)。
            if (entry.displayName != null && com.arspaper.util.DisplayText.hasMarkup(entry.displayName)) {
                String plain = com.arspaper.util.DisplayText.plain(entry.displayName);
                if (!plain.isBlank()) {
                    entry.displayName = plain;
                }
            }
        }
        applyCategories(entries);
        return entries;
    }

    /**
     * 5分類(防具/素材/武器/ツール/その他)を全レシピへ焼き付ける(N4)。
     *
     * <p><b>全件そろってからでないと決められない</b>: 「素材」の判定に
     * 「この結果が他のレシピの素材として使われているか」を使うため、
     * 先に全レシピの素材トークンを1つの集合へ集めてから分類する。
     */
    private void applyCategories(List<RecipeEntry> entries) {
        java.util.Set<String> usedAsIngredient = new java.util.HashSet<>();
        for (RecipeEntry entry : entries) {
            usedAsIngredient.addAll(entry.ingredientTokens());
        }
        for (RecipeEntry entry : entries) {
            ItemStack probe = entry.iconItem;
            if (probe == null && entry.icon != null && entry.icon.isItem()) {
                probe = new ItemStack(entry.icon);
            }
            String tfCategory =
                    com.arspaper.integration.TrinityForgeStatPreview.topLevelCategory(probe);
            entry.sortCategory = RecipeCategory.classify(tfCategory, entry.sortSkill, entry.icon,
                    entry.resultToken != null && usedAsIngredient.contains(entry.resultToken));
        }
    }

    /**
     * <b>「儀式レシピのアイコンが全てエンチャント本になる」バグの修正 (2026-07-30)</b>。
     *
     * <p>{@link com.arspaper.ritual.CatalogRitualRegistrar} が登録する TrinityForge カタログ儀式は
     * 結果IDが {@code tfcatalog:<catalogId>} で、これは <b>ArsPaper の itemRegistry には存在しない</b>
     * (TF側カタログのIDなので当然)。従来はそこで検索に失敗して一律 {@link Material#ENCHANTED_BOOK}
     * へ倒れていたため、TFカタログ由来の儀式レシピが<b>全部同じ本アイコン</b>になり、
     * 表示名・lore・並べ替えキーも結果アイテム由来にならずハードコード同然に見えていた。
     *
     * <p>ここで TF カタログの identity アイテム(表示名/CMD/革色/発光/flavor lore まで入った実物)を
     * 引き直し、作業台レシピの TF カタログ経路
     * ({@code catalogWorkbenchDisplayItem} を使う分岐)と同じ見た目に揃える。
     * 併せて {@code resultToken} も {@code custom:<catalogId>} へ直す — 従来は
     * {@code "custom:" + resultId} = {@code custom:tfcatalog:<id>} という素材語彙に存在しない
     * トークンになっており、素材⇔レシピの相互ジャンプが無言で外れていた。
     *
     * @return TFカタログ由来として解決できたら true(呼び出し側はフォールバックしない)
     */
    private boolean applyCatalogRitualIdentity(RecipeEntry entry, RitualRecipe recipe) {
        String resultId = recipe.resultId();
        if (resultId == null
                || !resultId.startsWith(com.arspaper.ritual.CatalogRitualRegistrar.RESULT_PREFIX)) {
            return false;
        }
        String catalogId = resultId.substring(
                com.arspaper.ritual.CatalogRitualRegistrar.RESULT_PREFIX.length());
        if (catalogId.isBlank()) {
            return false;
        }
        entry.resultCustomId = catalogId;
        entry.resultToken = "custom:" + catalogId;
        ItemStack identity = com.arspaper.integration.TrinityForgeBridge.createCatalogIdentity(catalogId);
        if (identity == null) {
            return false;
        }
        entry.iconItem = identity;
        entry.icon = identity.getType();
        // 表示名も TF カタログの正(MiniMessage解決済み)へ揃える。儀式の「×N」接尾辞は付け直す。
        String name = cleanDisplayName(identity);
        if (name != null && !name.isBlank()) {
            entry.displayName = recipe.resultAmount() > 1 ? name + " ×" + recipe.resultAmount() : name;
        }
        return true;
    }

    /**
     * Ars 自身が登録した作業台レシピの素材表示を、Bukkit の {@code RecipeChoice} ではなく<b>元config
     * (materials.yml / items.yml)の素材トークン</b>で上書きする(2026-07-28 バグ修正)。
     *
     * <p><b>なぜ必要か</b>: {@link com.arspaper.recipe.RecipeManager#resolveIngredient} は
     * {@code custom:<id>} も {@code list:<id>} も、意図的に「型のみ照合」の
     * {@link org.bukkit.inventory.RecipeChoice.MaterialChoice} へ倒している(ExactChoice だと PDC 付きの
     * 実物と isSimilar 不一致でクラフトが無言失敗するため)。その結果 {@link #describeChoice} は
     * MaterialChoice の<b>先頭Materialの名前しか復元できず</b>、
     * 例えば {@code custom:coal_block_3x} が素の {@code COAL_BLOCK} として表示されていた
     * (ジュエリーコアのレシピが「圧縮ブロックではない」と見えていた実バグ)。{@code list:} も
     * 先頭1種だけの表示に潰れる。元configのトークンなら {@code custom:}/{@code list:} のまま復元でき、
     * {@link #createIngredientDisplay} が正しい実アイテム/リスト表示を組める。
     *
     * <p>shape も必ず spec 由来へ揃えること — Bukkit の {@code getShape()} はパターン文字を振り直すため、
     * config由来の ingredientMap と Bukkit由来の shape を混ぜると全スロットが不一致になり素材が
     * 1つも描画されなくなる(TFカタログ経路で既に踏んだ罠。{@code catalogWorkbenchShape} のコメント参照)。
     *
     * <p>逆(解体)レシピは {@code forwardDataByKey} に載らないので素通しになるが、あちらの素材は
     * {@code ExactChoice} で登録されており {@link #describeChoice} が {@code custom:<id>} を
     * 正しく復元できるため、この上書きは不要。
     */
    private void applyForwardSpecTokens(NamespacedKey key, RecipeEntry entry) {
        var data = ArsPaper.getInstance().getRecipeManager().forwardRecipeData(key);
        if (data == null || data.ingredients() == null || data.ingredients().isEmpty()) {
            return;
        }
        entry.ingredientMap = new HashMap<>(data.ingredients());
        entry.shape = data.shape() == null || data.shape().isEmpty()
            ? List.of()
            : List.copyOf(data.shape());
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

        // === 醸造の効果時間 (W-167) ===
        // 効果レベルは表示名のローマ数字で出しているので、ここは持続時間だけ。
        if (entry.isBrewing) {
            String duration = brewDurationLabel(entry.brewDurationTicks);
            if (duration != null) {
                lore.add(Component.empty());
                lore.add(detailText("効果時間: " + duration, NamedTextColor.AQUA));
            }
        }

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

        // === 最低品質でのステータス下限(N4) ===
        // 品質と厳選ロールでどれだけ振れても「最低これだけは出る」を先に見せる。
        // TF未ロード / item-stats 未登録 / lore.yml に表示定義が無い場合は1行も出ない。
        List<Component> floorLines = minimumStatLines(entry);
        if (!floorLines.isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(floorLines);
        }
    }

    /**
     * このレシピの完成品が最低品質(既定では【劣悪】)で保証するステータスの lore 行。
     * 実際の計算は {@link com.arspaper.integration.TrinityForgeStatPreview} 側。
     */
    private List<Component> minimumStatLines(RecipeEntry entry) {
        ItemStack probe = entry.iconItem;
        if (probe == null && entry.icon != null && entry.icon.isItem()) {
            probe = new ItemStack(entry.icon);
        }
        return com.arspaper.integration.TrinityForgeStatPreview.minimumStatLines(probe);
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
            // 2026-08-19 W-123: 「スレッド枠付与の儀式」は説明が1行も出ていなかった
            // (default -> null に落ちていた)。累計上限は儀式ごとの max-slots で決まる。
            case "thread_slot_expand" -> {
                String max = entry.effectParams != null ? entry.effectParams.get("max-slots") : null;
                yield "コアに置いた装備のスレッド枠を +1"
                        + (max == null ? "" : "（儀式由来の累計 " + max + " 枠まで）");
            }
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
            for (String ing : RecipeShapeNormalizer.orderedIngredients(entry.ingredientMap)) {
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

    /**
     * 並べ替えボタンの lore を組み立てる(純粋関数)。TF図鑑の {@code CollectionGui#sortButton}
     * と同じ形式: 1行目は操作案内、続けて全モードを列挙し現在選択中のものだけ
     * {@code "▶ "} 前置＋緑、残りは補足行(種別の定義)。
     */
    static List<Component> sortLore(RecipeBrowserFilter.SortMode current) {
        List<Component> lore = new ArrayList<>();
        lore.add(detailText("クリックで次の並び順へ", NamedTextColor.GRAY));
        for (RecipeBrowserFilter.SortMode mode : RecipeBrowserFilter.SortMode.values()) {
            lore.add(detailText((mode == current ? "▶ " : "  ") + mode.label(),
                mode == current ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        }
        lore.add(detailText("種別=item-stats の使用スキル(無ければ素材)", NamedTextColor.DARK_GRAY));
        lore.add(detailText("分類=防具/素材/武器/ツール/その他", NamedTextColor.DARK_GRAY));
        return lore;
    }

    /**
     * レシピ種別ボタンの lore(純粋関数)。{@link #sortLore} と同じ「全モード列挙 + 選択中だけ
     * ▶ 緑」の形式に揃える — 同じ列に並ぶ2つのボタンで見た目の作法が違うと押し間違えるため。
     */
    static List<Component> kindLore(RecipeBrowserFilter.KindMode current) {
        List<Component> lore = new ArrayList<>();
        lore.add(detailText("クリックで表示するレシピ種別を切り替え", NamedTextColor.GRAY));
        for (RecipeBrowserFilter.KindMode mode : RecipeBrowserFilter.KindMode.values()) {
            lore.add(detailText((mode == current ? "▶ " : "  ") + mode.label(),
                mode == current ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        }
        lore.add(detailText("並べ替えとは独立", NamedTextColor.DARK_GRAY));
        return lore;
    }

    /**
     * 圧縮トグルの lore(純粋関数)。何が隠れているのかを必ず書く —— 「レシピが足りない」と
     * 誤解されると、隠したこと自体がバグ報告になって返ってくる。
     */
    static List<Component> compressionLore(boolean showDetails) {
        List<Component> lore = new ArrayList<>();
        lore.add(detailText("クリックで切り替え", NamedTextColor.GRAY));
        lore.add(detailText((showDetails ? "  " : "▶ ") + "最大倍率のみ(既定)",
            showDetails ? NamedTextColor.DARK_GRAY : NamedTextColor.GREEN));
        lore.add(detailText((showDetails ? "▶ " : "  ") + "全段 + 解凍レシピ",
            showDetails ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(detailText("圧縮素材は1系統で最大5段あるため、", NamedTextColor.DARK_GRAY));
        lore.add(detailText("既定では中間段と解凍を隠しています", NamedTextColor.DARK_GRAY));
        return lore;
    }

    /** レシピ種別ボタンのアイコン: 作業台は作業台ブロック、儀式は儀式の核に対応する見た目。 */
    private static Material kindIcon(RecipeBrowserFilter.KindMode mode) {
        return switch (mode) {
            case ALL -> Material.LIME_DYE;
            case WORKBENCH -> Material.CRAFTING_TABLE;
            case RITUAL -> Material.AMETHYST_CLUSTER;
            // 儀式エフェクトは「アイテムにならない儀式」。日の出/天候が代表なので日時計にした
            // (紫水晶=儀式レシピ と一目で見分けが付く見た目にする)。
            case RITUAL_EFFECT -> Material.DAYLIGHT_DETECTOR;
            case BREWING -> Material.BREWING_STAND;
        };
    }

    private String localize(String materialOrCustom) {
        if (materialOrCustom == null) return "不明";

        // 素材互換リスト: TF の list:<id> をラベル + 「(いずれか)」で表示する。
        if (materialOrCustom.startsWith("list:")) {
            String listId = materialOrCustom.substring("list:".length());
            return com.arspaper.integration.TrinityForgeBridge.materialListLabel(listId) + " (いずれか)";
        }

        // カスタムアイテム: レジストリから表示名を取得（Ars未登録ならTFカタログ表示名へフォールバック）。
        // どちらでも解決できないときだけ生IDが出る = config の配備ズレのサイン。黙って出すと
        // 「レシピGUIにIDが出る」実バグ(2026-08-03 source_singularity_jar)に気づけないので警告を残す。
        if (materialOrCustom.startsWith("custom:")) {
            String customId = materialOrCustom.substring("custom:".length());
            return ArsPaper.getInstance().getItemRegistry().get(customId)
                .map(item -> com.arspaper.util.DisplayText.plain(item.resolveDisplayName()))
                .filter(name -> !name.isBlank())
                .orElseGet(() -> {
                    String tfName = com.arspaper.integration.TrinityForgeBridge.catalogDisplayNamePlain(customId);
                    if (tfName != null && !tfName.isBlank()) {
                        return tfName;
                    }
                    warnUnresolved(customId);
                    return customId;
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

    /** 既に警告した未解決id(1回開くたびに何十行も出さない)。 */
    private static final java.util.Set<String> WARNED_UNRESOLVED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * {@code custom:<id>} が Ars にも TF カタログにも無いときの警告。GUIには生IDが出るしかないが、
     * 「なぜIDが出ているのか」をサーバ側で追えるようにする(実例: materials.yml だけ新しく配備され
     * sourcejars.yml が古いままで {@code source_singularity_jar} が解決できなかった)。
     */
    private static void warnUnresolved(String customId) {
        if (customId == null || !WARNED_UNRESOLVED.add(customId)) {
            return;
        }
        ArsPaper plugin = ArsPaper.getInstance();
        if (plugin != null) {
            plugin.getLogger().warning("RecipeBrowserGui: 'custom:" + customId
                + "' を Ars レジストリでも TrinityForge カタログでも解決できません"
                + " — レシピGUIに内部IDが出ます(config の配備漏れ / id のtypo を疑う)");
        }
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
