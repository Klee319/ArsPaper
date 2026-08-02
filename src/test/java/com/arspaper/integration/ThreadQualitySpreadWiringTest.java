package com.arspaper.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * スレッド厳選の quality-spread（依頼B、2026-08-02）の配線を固定する。
 *
 * <p>{@code TrinityForgeBridge.currentArsSmithingQuality} 自体は生きたテストで検証できる
 * （{@code crafter == null} は Bukkit に一切触れず即 0 を返す ── これが「品質情報が取れない経路
 * (ルートチェスト/ダンジョンドロップ/管理コマンド付与等)は従来どおりの抽選にフォールバックする」
 * 契約の入口）。それ以外の配線（RitualManager が player を resolveResult 経由で ThreadItem まで
 * 運んでいるか等）は、このフォークが Bukkit ランタイムを持たずライブ駆動できないため、
 * {@code MagicStatSourceWiringTest}/{@code LegacyCastExperienceRemovalTest} と同じソーステキスト
 * 検査で固定する。
 */
class ThreadQualitySpreadWiringTest {

    private static final String SRC = "src/main/java/com/arspaper/";

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(SRC + relative));
    }

    @Test
    @DisplayName("crafter==nullは例外もTF参照も無しで即0を返す(fail-open: 品質不明経路の唯一の入口)")
    void nullCrafterIsFailOpenZero() {
        assertEquals(0, TrinityForgeBridge.currentArsSmithingQuality(null, null));
    }

    @Test
    @DisplayName("ThreadItem#createItemStack()は生成者不明のフォールバックとしてcreateItemStack(null)へ委譲する")
    void noArgCreateItemStackDelegatesToNullCrafter() throws Exception {
        String source = read("item/impl/ThreadItem.java");
        assertTrue(source.contains("return createItemStack(null);"),
                "無引数版はplayer不明のフォールバック(quality=0扱い)としてcreateItemStack(null)へ委譲する必要がある");
        assertTrue(source.contains("public ItemStack createItemStack(Player crafter)"),
                "生成者ありの版(ritual craft専用)が無い");
        assertTrue(source.contains("TrinityForgeBridge.stampThreadIdentity(item, crafter)"),
                "createItemStack(Player)がTFのrollSeed/quality刻印(stampThreadIdentity)を呼んでいない"
                        + "(2026-08-03: 厳選はTFのItemData(rollSeed+quality)駆動へ統合済み)");
    }

    @Test
    @DisplayName("RitualManagerはresolveResultへplayerを渡し、ThreadItemだけplayer付きで生成する")
    void ritualManagerThreadsPlayerToThreadItem() throws Exception {
        String source = read("ritual/RitualManager.java");
        assertTrue(source.contains("resolveResult(recipe, player)"),
                "craftResult = resolveResult(recipe, player) の形で player を渡す必要がある");
        assertTrue(source.contains("private ItemStack resolveResult(RitualRecipe recipe, Player player)"),
                "resolveResultがplayerを受け取っていない");
        assertTrue(source.contains("if (item instanceof ThreadItem threadItem)"),
                "ThreadItemだけplayer付きcreateItemStackへ分岐していない");
        assertTrue(source.contains("threadItem.createItemStack(player)"),
                "ThreadItem生成でplayerを渡していない");
    }

    @Test
    @DisplayName("スレッド振り直し儀式(thread_reroll)はqualityを据え置いてrollSeedだけ刻み直す")
    void rerollRitualRerollsSeedOnly() throws Exception {
        String source = read("ritual/effect/ThreadRerollRitualEffect.java");
        assertTrue(source.contains("TrinityForgeBridge.rerollThreadIdentity(core)"),
                "振り直し儀式がTrinityForgeBridge#rerollThreadIdentityへ委譲していない"
                        + "(2026-08-03: 厳選はTFのItemData(rollSeed+quality)駆動へ統合済み)");
    }
}
