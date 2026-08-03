package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F2「武器・触媒のスレッド枠が機能しない」(2026-07-31)の配線の回帰ガード。
 *
 * <p>報告された症状は「lore に『スレッド枠 N枠』と出るのに何も起きない」で、原因は<b>フォーク側の
 * 2 段</b>:
 * <ol>
 *   <li>装着 GUI の入口が防具にしか無かった({@code ThreadGuiOpenListener} の {@code isArmorPiece} ゲート、
 *       {@code new ThreadGui} はリポジトリ全体で 1 箇所だけ = 代替入口なし)。</li>
 *   <li>スレッドの収集が {@code getArmorContents()} ループの中だけにあった。</li>
 * </ol>
 *
 * <p><b>訂正(2026-07-31 F3 指摘3)</b>: 「TF の Java 既定の枠上限(weapon/tool/other=0)と
 * 出荷 yml(=5)の drift」を 3 番目の原因として書いていたが<b>誤り</b>。出荷 yml は
 * {@code thread-slots} セクションを持ち 4 キーすべてを 5 で明示しているので
 * {@code CraftingFeaturesConfig#loadThreadSlots} の seed は必ず上書きされ、
 * <b>Java のフィールド既定値は稼働サーバで一度も効いていない</b>。lore に枠が出るようになったのは
 * 出荷 yml を 0→5 にした前段の変更({@code 7dca432})の帰結で、
 * 「出るのに効かない」の原因はここに挙げた 2 段だけである。
 *
 * <p><b>訂正(2026-07-31 F3 指摘2)</b>: 以前ここには「防具では二重発火が構造的に起きない」という
 * 前提があったが、これも誤り。{@code SpellBindListener#canBind} が弾くのは
 * {@code arspaper:custom_item_id} を持つ品だけで、TF カタログ防具は
 * {@code trinityforge:catalog_id} なので<b>バインドできる</b>
 * ({@code /ars bind} はバインド先を<b>オフハンド</b>から取るので、兜をオフハンドに・
 * 魔導書をメインハンドに持てば成立する)。防具側の GUI 起動にも
 * {@code isCancelled()} ガードが要る。
 *
 * <h2>なぜソース文字列の検査なのか（2026-07-31 F6 指摘7 への回答・これ以上の対処はしない）</h2>
 * <p>このフォークには <b>Bukkit ランタイムが無い</b>（{@code build.gradle.kts} の paper-api は
 * {@code compileOnly}/{@code testImplementation} の API だけで、MockBukkit も
 * {@code libs/TrinityForge.jar} の実体も入っていない）。イベントハンドラは
 * {@code PlayerInteractEvent} / {@code InventoryClickEvent} / {@code Player} を要求するので、
 * <b>リスナーそのものを実行する手段が原理的に無い</b>。
 * したがってここで縛れるのは「配線が繋がっているか」「分岐の順序が正しいか」だけであり、
 * その唯一実効的な形がソースの静的走査である（{@link ArmorManaListenerManaBonusGuardTest} と同じ流儀）。
 * <p>純関数へ切り出せる判断は切り出して<b>本当に実行する</b>テストへ移してある —
 * {@link ThreadApplicationPolicyTest}（スロット区分・スタック個数・バックパック）/
 * {@link ThreadSlotHintPolicyTest}（案内のスパム防止と負のキャッシュ）/
 * {@link ThreadTargetIdentityTest}（対象の同一性）。
 * 「リスナー本体まで純関数化する」案は採らない: {@code onInteract} の分岐は
 * イベントのキャンセル状態・手・アクション種別・スニーク状態という Bukkit 固有の入力の組み合わせで、
 * それを丸ごと写した純関数を作ると<b>本物と乖離した二重定義</b>になり、
 * 「純関数は緑なのに実サーバでは壊れている」という一番悪い形になる。
 * 代わりに順序（index 比較）で「他リスナーとの相対関係」を固定してある。
 * <b>この形の弱点は既知</b>: {@code if (event.isCancelled()) {} を改行やフォーマットで書き換えると
 * 偽の失敗になる（そのときは本テストの検索文字列も直すこと）。
 */
class ThreadHandheldWiringTest {

    private static String readSource(String... pathParts) throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper");
        for (String part : pathParts) {
            path = path.resolve(part);
        }
        assertTrue(Files.exists(path),
                "ソースが見つからない(パス変更時はこのテストの相対パスも更新すること): "
                        + path.toAbsolutePath());
        return Files.readString(path);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    // --- (2) 収集スロットの拡張 ---

    @Test
    @DisplayName("スレッド収集がメインハンド・オフハンドからも走っている")
    void threadCollectionRunsForHeldItemsToo() throws IOException {
        String source = readSource("item", "ArmorManaListener.java");

        assertTrue(source.contains("ThreadApplicationPolicy.SlotOrigin.WORN_ARMOR"),
                "防具4部位からのスレッド収集が消えている");
        assertTrue(source.contains("ThreadApplicationPolicy.SlotOrigin.MAIN_HAND"),
                "メインハンドのスレッドを集めていない。これが無いと武器・触媒のスレッドが"
                        + "『枠だけの飾り』に戻る(F2 の症状そのもの)。");
        assertTrue(source.contains("ThreadApplicationPolicy.SlotOrigin.OFF_HAND"),
                "オフハンド(offhand-stats-apply: true の品)のスレッドを集めていない");
    }

    @Test
    @DisplayName("集計結果は writeAddonCombatStats でTFの戦闘パイプラインへ渡している")
    void collectedStatsReachTheCombatPipeline() throws IOException {
        String source = readSource("item", "ArmorManaListener.java");

        assertTrue(source.contains("TrinityForgeBridge.writeAddonCombatStats(player"),
                "集計した戦闘ステをプレイヤーPDCへ書いていない(TF側が読めないので効果ゼロ)");
        assertTrue(source.contains("threadSetConfig.cumulativeBonus("),
                "thread-sets.yml のセット効果を足し込んでいない");
    }

    @Test
    @DisplayName("常時効果(ポーション/飛行)は policy のゲートを通してからしか立てていない")
    void ambientEffectsGoThroughThePolicyGate() throws IOException {
        String source = readSource("item", "ArmorManaListener.java");

        assertTrue(source.contains("ThreadApplicationPolicy.appliesAmbientEffects(origin)"),
                "常時効果のスロット判定が policy を経由していない");
        assertTrue(source.contains("if (ambient && thread.hasPotionEffect())"),
                "ポーション効果がスロット判定なしで立っている。手持ちで発動すると"
                        + "持ち替えのたびに点滅する(除外の理由は ThreadApplicationPolicy の javadoc)。");
        assertTrue(source.contains("if (ambient && thread.isFlightThread())"),
                "飛行スレッドがスロット判定なしで立っている。剣を握っただけで滑空が付く。");
    }

    // --- (1) 入口 ---

    @Test
    @DisplayName("右クリックのGUI起動は防具、またはツールを真上を見ながら構えたときだけ"
            + "(2026-08-02 依頼#44でツール分岐を追加)")
    void rightClickOpensGuiForArmorOrLookingUpTool() throws IOException {
        String source = readSource("item", "ThreadGuiOpenListener.java");

        assertTrue(source.contains("boolean armor = isArmorPiece(item);"),
                "防具判定を変数化していない(依頼#44でツール分岐を足す前提が崩れている)");
        assertTrue(source.contains("if (!armor) {"), "非防具の分岐が見つからない");
        assertTrue(source.contains("if (!isToolItem(item)) {"),
                "武器・触媒と非武器ツールを分ける判定が見つからない(依頼#44)。これが無いと"
                        + "剣や杖でも真上を見るだけでGUIが開いてしまう。");
        assertTrue(source.contains("if (!isLookingStraightUp(player)) {"),
                "真上を見ているかのゲートが見つからない(依頼#44)。ツールは採掘・耕作等で"
                        + "スニーク+右クリックを常用するため、これが無いと通常操作と衝突する。");
        assertTrue(source.contains("/ars thread"),
                "手持ち武器の入口(/ars thread)をプレイヤーへ案内していない。"
                        + "辿れない機能は無いのと同じ。");

        // ゲートの順序: 防具判定 → (非防具なら)ツール判定 → 見上げ判定 → 枠数解決、の順であること。
        int armorCheck = source.indexOf("boolean armor = isArmorPiece(item);");
        int toolCheck = source.indexOf("if (!isToolItem(item)) {");
        int lookUpCheck = source.indexOf("if (!isLookingStraightUp(player)) {");
        int slotsResolve = source.indexOf("int slots = effectiveThreadSlots(item, player);");
        assertTrue(armorCheck >= 0 && armorCheck < toolCheck && toolCheck < lookUpCheck
                        && lookUpCheck < slotsResolve,
                "GUI起動前のゲート順序が壊れている(防具→ツール→見上げ→枠数解決の順であること)");
    }

    @Test
    @DisplayName("防具側の GUI 起動も isCancelled を見る(呪文が出たなら開かない・F3 指摘2)")
    void armorGuiOpenIsGuardedByCancelledFlag() throws IOException {
        String source = readSource("item", "ThreadGuiOpenListener.java");

        int guard = source.indexOf("if (event.isCancelled()) {");
        int open = source.indexOf("openForHeldItem(player, item);");
        assertTrue(guard >= 0,
                "防具側の GUI 起動に isCancelled ガードが無い。リスナーは HIGH/"
                        + "ignoreCancelled=false なので、NORMAL の SpellBindListener が既に"
                        + "キャンセル+詠唱していても止まらない。TF カタログ防具は "
                        + "trinityforge:catalog_id なので canBind を通る = 実際に二重発火する。");
        assertTrue(open >= 0, "防具側の GUI 起動が openForHeldItem を経由していない");
        assertTrue(guard < open,
                "isCancelled ガードが GUI 起動より後にある。呪文が出たなら GUI は開かないのが正しい。");
    }

    @Test
    @DisplayName("手持ち武器はスニーク+右クリックで案内もGUIも出さない(弓/クロスボウ/トライデント/斧の"
            + "通常操作・F6 指摘3)")
    void sneakRightClickNeverOpensOrHintsForWeapons() throws IOException {
        // 報告された症状は「通常操作で案内が繰り返し出る」。武器はツールと違い見上げゲートが無い
        // (依頼#44はツールだけの追加入口なので、武器はこの経路では常に何もしない)。
        for (String material : new String[] {"BOW", "CROSSBOW", "TRIDENT", "NETHERITE_AXE", "BLAZE_ROD"}) {
            assertFalse(ThreadApplicationPolicy.isArmorSlotMaterialName(material),
                    material + " が防具扱いになっている(非防具ブランチを通らなくなる)");
            assertFalse(ThreadApplicationPolicy.isToolMaterialName(material),
                    material + " がツール扱いになっている(見上げゲート付きでGUIが開いてしまう。"
                            + "斧は weapon/tool 両方に分類されるが依頼#44の対象からは除外している)");
        }

        String source = readSource("item", "ThreadGuiOpenListener.java");
        assertFalse(source.contains("sendHandheldHint"),
                "スニーク+右クリックの案内が復活している。スレッド枠を持つ弓5件・クロスボウ5件・"
                        + "トライデント5件・斧4件でスニーク狙撃をすると、"
                        + "TF の EXP/会心アクションバー(SkillExpFeedbackService / CombatListener)を"
                        + "5秒ごとに無限に上書きし続ける。");
        assertFalse(source.contains("allowInteractHint"),
                "右クリック案内のスパム防止APIを再び呼んでいる(APIごと廃止済み)");

        // 案内の送出は「選択時」の1経路だけ。
        assertEquals(1, countOccurrences(source, "sendHint(player, slots);"),
                "案内の送出箇所が1つではない(選択時 hintForSelectedItem だけが正しい)");
        int selectHintGate = source.indexOf("hintPolicy.allowSelectHint(");
        int send = source.indexOf("sendHint(player, slots);");
        assertTrue(selectHintGate >= 0 && selectHintGate < send,
                "唯一の案内送出が allowSelectHint ゲートの後ろにない");
    }

    @Test
    @DisplayName("依頼#44: 純粋ツール(斧を除く)はisToolItem経由でGUI起動候補になる")
    void toolsAreEligibleForTheNewGestureEntry() throws IOException {
        for (String material : new String[] {
                "NETHERITE_PICKAXE", "DIAMOND_SHOVEL", "NETHERITE_HOE",
                "FISHING_ROD", "SHEARS", "FLINT_AND_STEEL"}) {
            assertTrue(ThreadApplicationPolicy.isToolMaterialName(material),
                    material + " が新ジェスチャーの対象ツール判定に入っていない(依頼#44)");
        }
        String source = readSource("item", "ThreadGuiOpenListener.java");
        assertTrue(source.contains("private static boolean isToolItem(ItemStack item) {")
                        && source.contains("ThreadApplicationPolicy.isToolMaterial(item.getType());"),
                "isToolItemがThreadApplicationPolicy.isToolMaterialへ委譲していない");
        assertTrue(source.contains("private static boolean isLookingStraightUp(Player player) {")
                        && source.contains("getPitch() <= -80f"),
                "真上判定のピッチ閾値(-80度)が見つからない");
    }

    // --- (F6 指摘1) スタックへの装着ガード ---

    @Test
    @DisplayName("2個以上のスタックへは装着させない(入口と装着直前の両方でガード)")
    void socketingIsRefusedForStacks() throws IOException {
        assertFalse(ThreadApplicationPolicy.isStackTooLargeToSocket(1), "1個は装着できる必要がある");
        assertFalse(ThreadApplicationPolicy.isStackTooLargeToSocket(0), "空/0個は別経路で弾く");
        assertTrue(ThreadApplicationPolicy.isStackTooLargeToSocket(2),
                "2個スタックを許すと ItemMeta がスタック単位なので複製になる");
        assertTrue(ThreadApplicationPolicy.isStackTooLargeToSocket(64));

        // 入口1: /ars thread(BLAZE_ROD 触媒11件と ENDER_EYE#85 は最大スタック64)
        String command = readSource("command", "handlers", "ThreadCommands.java");
        int stackGuard = command.indexOf("ThreadApplicationPolicy.isStackTooLargeToSocket(");
        int open = command.indexOf("new ThreadGui(");
        assertTrue(stackGuard >= 0, "/ars thread にスタックガードが無い");
        assertTrue(stackGuard < open, "スタックガードが GUI 起動より後ろにある");

        // 入口2: 防具のスニーク+右クリック(将来スタック可能な防具材質が増えても穴が開かないように)
        String listener = readSource("item", "ThreadGuiOpenListener.java");
        int listenerGuard = listener.indexOf("ThreadApplicationPolicy.isStackTooLargeToSocket(");
        int listenerOpen = listener.indexOf("openForHeldItem(player, item);");
        assertTrue(listenerGuard >= 0, "防具経路にスタックガードが無い");
        assertTrue(listenerGuard < listenerOpen, "スタックガードが GUI 起動より後ろにある");

        // 最後の砦: GUI を開いたあとにスタックを作り直せるので、装着/取り外しの直前でも見る。
        String gui = readSource("gui", "ThreadGui.java");
        int guiGuard = gui.indexOf("ThreadApplicationPolicy.isStackTooLargeToSocket(");
        int consume = gui.indexOf("findThreadItemInInventory(player)");
        assertTrue(guiGuard >= 0,
                "refreshTargetFromSlot にスタックガードが無い。入口だけでは GUI を開いたあとに"
                        + "スタックを作り直す経路が残る。");
        assertTrue(guiGuard < consume, "スタックガードがスレッド消費より後ろにある");
        assertTrue(gui.contains("同じ装備が重なっているため中断しました"),
                "中断の理由を日本語で伝えていない");
    }

    // --- (F6 指摘5) 案内経路のコスト ---

    @Test
    @DisplayName("枠を持たない品でもフル解決は1セッション1回に収まる(足切りが実際に働く)")
    void selectHintShortCircuitsBeforeTheExpensiveResolve() throws IOException {
        String source = readSource("item", "ThreadGuiOpenListener.java");

        int suppressed = source.indexOf("hintPolicy.isSelectHintSuppressed(");
        int resolve = source.indexOf("int slots = effectiveThreadSlots(selected, player);");
        assertTrue(suppressed >= 0 && resolve > suppressed,
                "安い足切り(isSelectHintSuppressed)が高い解決(effectiveThreadSlots)より後ろにある");
        assertTrue(source.contains("hintPolicy.markSelectHintEvaluated("),
                "枠0の品を『評価済み』として覚えていない。lastSelectHintAt は allowSelectHint の"
                        + "中でしか書かれないので、枠を1つも持たないプレイヤーでは"
                        + "isSelectHintSuppressed が永久に false を返し、ホットバー操作ごとに"
                        + "TF item-stats のフル解決が走り続ける。");
        assertTrue(source.contains("pendingSelectHint.add("),
                "シフトクリックごとに runTask が積まれる形に戻っている"
                        + "(二重チェストの整理で約50件スケジュールされる)");
    }

    @Test
    @DisplayName("案内は持ち替え・オフハンド入替・ホットバースワップでも出る(右クリックに依存しない)")
    void hintAlsoFiresWhenTheItemIsSelected() throws IOException {
        String source = readSource("item", "ThreadGuiOpenListener.java");

        assertTrue(source.contains("PlayerItemHeldEvent event"),
                "ホットバー持ち替えで案内していない。右クリックだけに頼ると、"
                        + "呪文をバインドした杖では案内へ辿り着けない(F3 指摘1)。");
        assertTrue(source.contains("PlayerSwapHandItemsEvent event"),
                "F キーのメイン/オフハンド入れ替えで案内していない");
        assertTrue(source.contains("ClickType.NUMBER_KEY"),
                "インベントリ画面でのホットバースワップ経路で案内していない"
                        + "(PlayerItemHeldEvent は選択スロットが変わらないので飛ばない)");
        assertTrue(source.contains("hintPolicy.isSelectHintSuppressed(")
                        && source.contains("hintPolicy.allowSelectHint("),
                "案内のスパム防止が ThreadSlotHintPolicy を経由していない");
        assertTrue(source.contains("hintPolicy.forget("),
                "退出時に案内の状態を捨てていない(常駐マップにオフラインプレイヤーが溜まる)");
        assertFalse(source.contains("5_000L") || source.contains("30_000L"),
                "案内の間隔がリスナー内のマジックナンバーになっている"
                        + "(名前付き定数は ThreadSlotHintPolicy 側に置く)");
    }

    @Test
    @DisplayName("/ars thread が実際に登録されていて ThreadGui を開く")
    void threadCommandIsRegisteredAndOpensTheGui() throws IOException {
        String command = readSource("command", "ArsCommand.java");
        assertTrue(command.contains("Commands.literal(\"thread\")"),
                "/ars thread が未登録。手持ち装備には他に入口が無い。");
        assertTrue(command.contains("ThreadCommands.executeThread("),
                "/ars thread がハンドラへ繋がっていない");

        String handler = readSource("command", "handlers", "ThreadCommands.java");
        assertTrue(handler.contains("getItemInMainHand()"),
                "/ars thread がメインハンドの装備を見ていない");
        assertTrue(handler.contains(
                        "new ThreadGui(player, held, plugin, player.getInventory().getHeldItemSlot())"),
                "/ars thread が対象スロット付きで ThreadGui を開いていない。"
                        + "スロットを渡さないと装着直前の同一性確認が効かず、"
                        + "対象を手から離したままスレッドだけ溶ける(F3 指摘5)。");
        assertTrue(handler.contains("スレッド枠がありません"),
                "枠 0 のときの理由が日本語で返っていない");
    }

    @Test
    @DisplayName("/ars help がコマンド一覧を出し、thread が載っている(F3 指摘1)")
    void helpCommandListsThread() throws IOException {
        String command = readSource("command", "ArsCommand.java");
        assertTrue(command.contains("Commands.literal(\"help\")"),
                "/ars help が未登録。Brigadier 補完だけが発見経路だと"
                        + "『存在を知っている人しか辿れない』機能ができる。");
        assertTrue(command.contains("HelpCommands.executeHelp("),
                "/ars help がハンドラへ繋がっていない");

        String help = readSource("command", "handlers", "HelpCommands.java");
        assertTrue(help.contains("\"/ars thread\""),
                "コマンド一覧に /ars thread が載っていない(これを載せるのが指摘1の3点目)");
        assertTrue(help.contains("arspaper.admin"),
                "管理コマンドを権限で絞っていない(打てないコマンドが並ぶだけになる)");
    }

    @Test
    @DisplayName("policy の ThreadType 版が enum のフラグを実際に読んでいる")
    void policyReadsTheRealThreadTypeFlags() throws IOException {
        // ThreadType はサーバ無しでロードできない(PotionEffectType レジストリ)ので、
        // enum フラグ → 純粋判定への委譲が生きていることはソースで固定する。
        String source = readSource("item", "ThreadApplicationPolicy.java");

        assertTrue(source.contains("type.isBackpackThread()"),
                "canSocketOutsideArmor がバックパック判定を読んでいない");
        assertTrue(source.contains("type.hasPotionEffect()")
                        && source.contains("type.isFlightThread()"),
                "isAmbientOnlyEffect がポーション/飛行のフラグを読んでいない");
        assertFalse(source.contains("material.isAir()"),
                "Material#isAir() はブロック型の遅延解決で org.bukkit.Registry を触るため"
                        + "サーバ無しでは落ちる。空気材質はどの接尾辞にも当たらないので判定にも要らない。");
    }

    @Test
    @DisplayName("GUI はバックパックスレッドの防具外装着を拒否する(収納データ喪失の防止)")
    void guiRejectsBackpackThreadOutsideArmor() throws IOException {
        String source = readSource("gui", "ThreadGui.java");

        assertTrue(source.contains("ThreadApplicationPolicy.canSocketOutsideArmor(threadType)"),
                "バックパックスレッドの防具外装着を止めていない。/ars backpack は着用防具しか"
                        + "走査しないので、武器へ入れると収納の中身へ二度と辿り着けない。");
        assertTrue(source.contains("ThreadApplicationPolicy.isAmbientOnlyEffect(type)"),
                "手持ちで効かない常時効果スレッドに注記を出していない"
                        + "(効果loreの『(装備中常時)』が嘘になる)");
    }

    @Test
    @DisplayName("GUI の文言が防具前提のままになっていない")
    void guiVocabularyIsSlotNeutral() throws IOException {
        String source = readSource("gui", "ThreadGui.java");

        assertFalse(source.contains("\"セット: \""),
                "情報ボタンが『セット:』(防具セット前提の語)のまま。武器・触媒でも開くので中立語にする。");
        assertTrue(source.contains("\"対象: \""), "情報ボタンの対象表示が無い");
        assertFalse(source.contains("armorItem"),
                "フィールド名が armorItem のまま(防具前提の語彙が残っている)");
    }

    // --- (F3 指摘5) 対象装備を動かされたときの保護 ---

    @Test
    @DisplayName("装着/取り外しの直前に対象スロットの同一性を再確認している")
    void threadSocketingRevalidatesTheTargetSlot() throws IOException {
        String source = readSource("gui", "ThreadGui.java");

        assertTrue(source.contains("private boolean refreshTargetFromSlot(Player player)"),
                "対象スロットの再取得/同一性確認が無い");
        assertTrue(source.contains("ThreadTargetIdentity.of(live)"),
                "スロットのライブスタックから同一性を作っていない");
        assertTrue(source.contains("targetIdentity.matches("),
                "同一性の比較をしていない(ItemStack の参照同一性は移動や editMeta で崩れる)");

        int guard = source.indexOf("if (!refreshTargetFromSlot(player)) {");
        int consume = source.indexOf("findThreadItemInInventory(player)");
        assertTrue(guard >= 0, "handleThreadSlotClick の先頭で同一性確認をしていない");
        assertTrue(consume >= 0, "スレッド消費の経路が見つからない(テストの前提が変わった)");
        assertTrue(guard < consume,
                "同一性確認がスレッド消費より後ろにある。対象が手から離れているとスレッドだけ"
                        + "消えて装備には書き込まれない(=プレイヤーは成功したと思ってスレッドを失う)。");
        assertTrue(source.contains("対象の装備が手から離れたため中断しました"),
                "中断の理由を日本語で伝えていない");
    }

    @Test
    @DisplayName("GuiListener が ThreadGui の対象スロットへのクリックを通していない")
    void guiListenerBlocksClicksOnTheTargetSlot() throws IOException {
        String source = readSource("gui", "GuiListener.java");

        assertTrue(source.contains("touchesTargetSlot("),
                "対象スロット判定が無い。ThreadGui の下段には対象そのものが描画されていて"
                        + "クリックできるので、同一性確認だけに頼ると『毎回中断される GUI』になる。");
        assertTrue(source.contains("gui instanceof ThreadGui threadGui"),
                "ThreadGui の例外分岐が対象スロットを読める形になっていない");
    }
}
