package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;
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
    @DisplayName("重複セットの可否は policy の純関数で判定し、既定を『重複不可』へ戻していない")
    void duplicatePolicyIsWiredAndDefaultsToAllowed() throws IOException {
        assertTrue(readSource("gui", "ThreadGui.java")
                        .contains("ThreadApplicationPolicy.canSocketAnother("),
                "装着経路が重複/最大積載の判定を自前で持っている。判定は policy の純関数に集約する"
                        + "(ThreadApplicationPolicyTest が挙動を固定しているのはそちら側)");

        // 否定形で縛るのは「旧既定だけに存在する形」に限る(ソースの綴りを固定すると
        // 実装を書き換えただけで誤検知する — allowlist-tests の教訓)。
        String config = readSource("item", "ThreadConfig.java");
        assertFalse(config.replace(" ", "").contains("getBoolean(\"stackable\",false)"),
                "threads.yml の stackable 未記載を false(重複不可)に戻している。"
                        + "既定は ThreadApplicationPolicy.DEFAULT_STACKABLE を読むこと");
        assertFalse(config.replace(" ", "").contains("maxStack.getOrDefault(threadId,Integer.MAX_VALUE)"),
                "max 未記載を無制限に戻している。1種へ全枠集中できると TF の帯目標が壊れる");
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
        // 2026-08-08: potion-effect の config 上書き(ThreadConfig#getPotionEffect)対応で
        // thread.hasPotionEffect() 直読みから threadConfig.getPotionEffect(thread) != null へ
        // 変わった。判定の入口が ambient のif分岐であることは変わらない。
        assertTrue(source.contains("if (ambient) {")
                        && source.contains("PotionEffectType potionType = threadConfig.getPotionEffect(thread);"),
                "ポーション効果がスロット判定なしで立っている。手持ちで発動すると"
                        + "持ち替えのたびに点滅する(除外の理由は ThreadApplicationPolicy の javadoc)。");
        assertTrue(source.contains("if (ambient && threadConfig.isFlightThread(thread))"),
                "飛行スレッドがスロット判定なしで立っている。剣を握っただけで滑空が付く。");
    }

    // --- (1) 入口 ---

    @Test
    @DisplayName("右クリックのGUI起動は防具、または非防具(種別問わず)を『下を向いて直近にジャンプ』"
            + "したときだけ(2026-08-04: 真上ジェスチャーはチャット出力へ譲った)")
    void rightClickOpensGuiForArmorOrAnyDownwardJumpingEquipment() throws IOException {
        String source = readSource("item", "ThreadGuiOpenListener.java");

        assertTrue(source.contains("if (!isOpenGesture(player, item)) {"),
                "GUI起動のジェスチャー判定が isOpenGesture へ集約されていない");
        assertTrue(source.contains("return isLookingDown(player) && jumpedRecently(player);"),
                "非防具のジェスチャーが『下向き かつ 直近ジャンプ』の AND になっていない。"
                        + "下向きだけにすると採掘・耕作・パス化・ブロック設置(すべて下向き+スニーク+"
                        + "右クリック)を奪う ── ジャンプが唯一の安全装置。");
        assertFalse(source.contains("isLookingStraightUp("),
                "真上判定が GUI 起動側に残っている。真上+スニークは "
                        + "ThreadStatChatListener(内訳のチャット出力)へ割り当て済みなので、"
                        + "両方が同じ姿勢を要求すると片方が無言で死ぬ。");
        assertFalse(source.contains("isToolItem("),
                "isToolItemによる素材カテゴリの絞り込みが復活している(2026-08-04に撤廃済み)。");
        assertTrue(source.contains("/ars thread"),
                "全装備共通のフォールバック入口(/ars thread)をプレイヤーへ案内していない。"
                        + "辿れない機能は無いのと同じ。");

        // ゲートの順序: ジェスチャー判定 → 枠数解決、の順であること。
        int gesture = source.indexOf("if (!isOpenGesture(player, item)) {");
        int slotsResolve = source.indexOf("int slots = effectiveThreadSlots(item, player);");
        assertTrue(gesture >= 0 && gesture < slotsResolve,
                "GUI起動前のゲート順序が壊れている(ジェスチャー→枠数解決の順であること)");
    }

    @Test
    @DisplayName("装備の lore は『・スレッド名【品質】』の1行だけで、厳選ステの明細を展開していない")
    void equipmentLoreKeepsOnlyTheSummaryLinePerThread() throws IOException {
        String gui = readSource("gui", "ThreadGui.java");

        int build = gui.indexOf("private List<Component> buildThreadLore() {");
        assertTrue(build >= 0, "buildThreadLore が見つからない(テストの前提が変わった)");
        int buildEnd = gui.indexOf("\n    }", build);
        assertTrue(buildEnd > build, "buildThreadLore の終端が取れない");
        String body = gui.substring(build, buildEnd);

        assertTrue(body.contains("SocketedThreads.summaryLine("),
                "装備 lore が共通の要約行(・スレッド名【品質】)を使っていない");
        assertFalse(body.contains("ThreadItem.rollLore("),
                "装備 lore に厳選ステの明細を展開している。5枠埋めるとツールチップが数十行になり、"
                        + "装備本体のステが画面外へ押し出される(明細は ThreadStatChatListener でチャットへ)。");

        String socketed = readSource("item", "SocketedThreads.java");
        assertTrue(socketed.contains("qualityTierLabel("),
                "品質表記が TF の quality-tiers.yml 由来でない。ここで『品質3』等と数値化すると"
                        + "TF 装備の品質行と食い違う。");
    }

    @Test
    @DisplayName("スレッドのステ表示を自前で連結していない(ステータスidが素で出る形へ戻さない)")
    void threadStatLinesAreBuiltByTrinityForgeOnly() throws IOException {
        // javadoc の引用文に反応しないよう、rollLore の【本体】だけを切り出して見る。
        String item = readSource("item", "impl", "ThreadItem.java");
        int roll = item.indexOf("public static List<Component> rollLore(");
        assertTrue(roll >= 0, "rollLore が見つからない(テストの前提が変わった)");
        int rollEnd = item.indexOf("\n    }", roll);
        assertTrue(rollEnd > roll, "rollLore の終端が取れない");
        String body = item.substring(roll, rollEnd);

        assertTrue(body.contains("TrinityForgeBridge.threadStatLore(stats)"),
                "厳選ステの整形が TF の LoreComposer 経由でない");
        assertFalse(body.contains("Component.text("),
                "rollLore が行を自前で組み立てている。TF の整形結果をそのまま返すこと"
                        + "(色/アイコン/桁数/単位/テンプレートが TF 装備と揃わなくなる)。");
        assertFalse(body.contains("orElseGet(") || body.contains("forEach("),
                "表示名の引き当て失敗時にキー名を素で出すフォールバックや自前ループが復活している"
                        + "(実機でステータスidが並んだ 2026-08-04 の症状そのもの)");

        String bridge = readSource("integration", "TrinityForgeBridge.java");
        assertTrue(bridge.contains("loreComposer().statLines("),
                "threadStatLore が statLines(区切り線なし)を使っていない。差し込み用/チャット用の経路は"
                        + "区切り線を含めてはいけない(幅可変なので差し込み先に溜まる)。");
        assertTrue(bridge.contains("itemFactory().statLoreBlock("),
                "スレッドアイテム自身の lore が TF の装備経路(statLoreBlock)を通っていない。"
                        + "2026-08-05 の要望『スレッドのステ lore の体裁とフォントを通常の装備と同じに』は"
                        + "品質行と区切り線を含む装備の lore 経路をそのまま使うことで満たしている。");
        assertFalse(bridge.contains("Optional<ThreadStatDisplay> threadStatDisplay"),
                "自前連結を招く threadStatDisplay が復活している(撤去済み。理由は同ファイルのコメント)");

        String config = readSource("item", "ThreadConfig.java");
        assertFalse(config.contains("NamedTextColor.AQUA")
                        || config.contains("NamedTextColor.GOLD")
                        || config.contains("NamedTextColor.DARK_RED"),
                "効果説明の色が効果ごとのバラバラな指定へ戻っている。1本のスレッドの lore で色が"
                        + "混在し、TF 装備の灰色テンプレートとも揃わない(依頼#46)。");
    }

    @Test
    @DisplayName("ジャンプ時刻を Paper の PlayerJumpEvent で拾い、退出時に捨てている")
    void jumpTrackingIsWiredAndCleanedUp() throws IOException {
        String source = readSource("item", "ThreadGuiOpenListener.java");

        assertTrue(source.contains("PlayerJumpEvent event"),
                "ジャンプを拾っていない。jumpedRecently が永久に false を返し、"
                        + "非防具装備の GUI 入口が丸ごと死ぬ(=/ars thread しか残らない)。");
        // 2026-08-18: ここは `lastJumpAt.put(` という**識別子の綴りそのもの**を固定していたため、
        // フィールドが定数命名(LAST_JUMP_AT)へ直された時点で、配線は正しいのに赤くなっていた
        // (=直っているものを壊れていると報告する誤検知)。綴りではなく「記録している/捨てている」
        // という配線の有無で見るために、大文字小文字と下線を落として突き合わせる。
        String normalized = source.toLowerCase(Locale.ROOT).replace("_", "");
        assertTrue(normalized.contains("lastjumpat.put("), "ジャンプ時刻を記録していない");
        assertTrue(normalized.contains("lastjumpat.remove("),
                "退出時にジャンプ時刻を捨てていない(常駐マップにオフラインプレイヤーが溜まる)");
    }

    @Test
    @DisplayName("装着スレッドの内訳は真上+スニークでチャットへ出す(lore からは外した分の受け皿)")
    void threadBreakdownGoesToChatOnLookUpSneak() throws IOException {
        String source = readSource("item", "ThreadStatChatListener.java");

        assertTrue(source.contains("PlayerToggleSneakEvent event"),
                "スニーク開始で発火していない。右クリックを条件にすると手持ちの通常操作を奪う。");
        assertTrue(source.contains("if (!event.isSneaking()) {"),
                "スニーク解除でも出している(1操作で2回流れる)");
        assertTrue(source.contains("STRAIGHT_UP_PITCH"),
                "真上判定が無い。スニーク開始は頻繁なイベントなので、姿勢の限定が唯一の足切り。");
        assertTrue(source.contains("SocketedThreads.read("),
                "装着スレッドの読み出しが共通リーダーを経由していない。"
                        + "別実装にすると『表示されているスレッドと効いているスレッドが違う』になる。");
        assertTrue(source.contains("TrinityForgeBridge.threadStatLore("),
                "明細の整形が TF の LoreComposer 経由でない(lore と桁・単位・色が食い違う)");
        assertTrue(source.contains("COOLDOWN_MS"), "連打の間隔ガードが無い");

        String plugin = readSource("ArsPaper.java");
        assertTrue(plugin.contains("new com.arspaper.item.ThreadStatChatListener()"),
                "ThreadStatChatListener が登録されていない。lore から明細を外した以上、"
                        + "これが唯一の内訳確認手段なので未登録は機能喪失そのもの。");
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
    @DisplayName("ジェスチャー不成立のスニーク+右クリックは装備種別を問わず案内もGUIも出さない"
            + "(弓/クロスボウ/トライデント/斧/剣等の通常操作・F6 指摘3)")
    void sneakRightClickAloneNeverOpensOrHintsRegardlessOfEquipment() throws IOException {
        // 2026-08-04: ジェスチャー対象をツール限定から全装備へ広げたため、
        // 「武器だから姿勢ゲートが無い」という区別自体が無くなった。
        // ジェスチャーが成立しない限り、装備種別を問わず何も起きないことだけを固定する。
        for (String material : new String[] {
                "BOW", "CROSSBOW", "TRIDENT", "NETHERITE_AXE", "NETHERITE_SWORD", "BLAZE_ROD"}) {
            assertFalse(ThreadApplicationPolicy.isArmorSlotMaterialName(material),
                    material + " が防具扱いになっている(非防具ブランチを通らなくなる)");
        }

        String source = readSource("item", "ThreadGuiOpenListener.java");
        assertFalse(source.contains("sendHandheldHint"),
                "スニーク+右クリックの案内が復活している。スレッド枠を持つ弓5件・クロスボウ5件・"
                        + "トライデント5件・斧4件・剣類でスニーク攻撃をすると、"
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
    @DisplayName("2026-08-04: 斧・武器・杖・触媒もスレッド枠を持てばジェスチャーの対象になる"
            + "(素材カテゴリによる絞り込みを撤廃)")
    void weaponsAndToolsAreEligibleForTheGestureEntryViaThreadSlotsOnly() throws IOException {
        String source = readSource("item", "ThreadGuiOpenListener.java");
        assertFalse(source.contains("ThreadApplicationPolicy.isToolMaterial"),
                "ThreadApplicationPolicy.isToolMaterialへの参照が残っている"
                        + "(素材カテゴリによる絞り込みは撤廃済みのはず)");
        assertTrue(source.contains("private static boolean isLookingDown(Player player) {")
                        && source.contains("getPitch() >= 55f"),
                "下向き判定のピッチ閾値(+55度)が見つからない");
        assertTrue(source.contains("int slots = effectiveThreadSlots(item, player);"),
                "ジェスチャー判定の後にeffectiveThreadSlotsで枠数を解決していない"
                        + "(素材カテゴリではなく実際の枠数だけがゲートである必要がある)");
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
