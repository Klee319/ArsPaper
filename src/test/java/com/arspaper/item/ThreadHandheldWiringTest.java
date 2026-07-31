package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F2「武器・触媒のスレッド枠が機能しない」(2026-07-31)の配線の回帰ガード。
 *
 * <p>報告された症状は「lore に『スレッド枠 N枠』と出るのに何も起きない」で、原因は 3 つ:
 * <ol>
 *   <li>装着 GUI の入口が防具にしか無かった({@code ThreadGuiOpenListener} の {@code isArmorPiece} ゲート、
 *       {@code new ThreadGui} はリポジトリ全体で 1 箇所だけ = 代替入口なし)。</li>
 *   <li>スレッドの収集が {@code getArmorContents()} ループの中だけにあった。</li>
 *   <li>Java 既定の枠上限(weapon/tool/other=0)と出荷 yml(=5)の drift。</li>
 * </ol>
 *
 * <p>このフォークは Bukkit ランタイム/MockBukkit を持たないため、実行では検証できない
 * 「どのスロットからスレッドを集めているか」「入口がどこにあるか」をソースの静的走査で固定する
 * ({@link ArmorManaListenerManaBonusGuardTest} と同じ流儀)。判断そのものの検証は
 * {@link ThreadApplicationPolicyTest}。
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
    @DisplayName("右クリックの GUI 起動は防具のみ。手持ちは案内だけ出して開かない(呪文との二重発火防止)")
    void rightClickOpensGuiForArmorOnly() throws IOException {
        String source = readSource("item", "ThreadGuiOpenListener.java");

        assertTrue(source.contains("if (!isArmorPiece(item)) {"),
                "手持ち装備でも右クリックで GUI を開こうとしている。SpellBindListener(NORMAL) が"
                        + "同じ右クリックで呪文を発動させるので、バインド済み触媒で"
                        + "『呪文が飛びつつ GUI が開く』二重発火になる。");
        // 案内は「呪文が出た(=既にキャンセルされた)」ときには出さない。
        assertTrue(source.contains("if (!event.isCancelled()) {"),
                "他リスナーがキャンセル済みかを見ずに案内を出している(呪文発動と同時に喋る)");
        assertTrue(source.contains("/ars thread"),
                "手持ち装備の入口(/ars thread)をプレイヤーへ案内していない。"
                        + "辿れない機能は無いのと同じ。");
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
        assertTrue(handler.contains("new ThreadGui(player, held, plugin).open()"),
                "/ars thread が ThreadGui を開いていない");
        assertTrue(handler.contains("スレッド枠がありません"),
                "枠 0 のときの理由が日本語で返っていない");
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
}
