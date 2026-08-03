package com.arspaper.ritual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-08-03 実サーバ報告「Ars鍛冶の経験値が入らない」の回帰テスト。
 *
 * <p><b>真因</b>: {@code RitualManager}/{@code TrinityForgeBridge} は「品質を刻めるか」と
 * 「EXPを付与するか」を<b>同じ門</b>(装備 or {@code isQualityStamped()})に相乗りさせていた。
 * ソースジェムの系譜(source_gem→…→singularity_proof)・エンチャント本
 * (mana_regen/mana_boost/share/soulbound)・ウェイストーン・テレポートコンパスは品質を刻む
 * 対象ではない({@code ConfigurableMaterial}/{@code Waystone}/{@code TeleportCompass} はいずれも
 * {@code BaseCustomItem#isQualityStamped()} を{@code true}へ上書きしない)ため、
 * これらの儀式は<b>一度もEXP付与へ到達しなかった</b>。
 *
 * <p><b>なぜ無限EXPにならないか</b>: {@code RitualManager}/{@code RitualRecipe} には分解・逆儀式の
 * 概念が無い(grep確認済み — {@code reversible} は別系統の {@code RecipeManager}(バニラ作業台
 * レシピ)にしか存在しない)。よって「品質を刻まない結果にも常にEXPを払う」へ緩めても、
 * 儀式の結果を再び儀式の素材へ戻す経路が無いため往復ファームは成立しない。
 *
 * <p>MockBukkit を使わず {@code RitualMaterialTokenTest} と同じソーステキスト走査で固定する
 * (このフォークの既存テスト方針を踏襲)。
 */
class RitualQualityExpGateSeparationTest {

    @Test
    @DisplayName("品質を刻めない儀式結果でも grantArsSmithingExpOnly でEXPを付与している(RitualManager)")
    void ritualManagerGrantsExpEvenWithoutQualityStamp() throws Exception {
        String source = flattened("src/main/java/com/arspaper/ritual/RitualManager.java");

        assertTrue(source.contains("grantArsSmithingExpOnly(result, player, consumedTokens)"),
                "品質を刻めない儀式結果(isQualityStamped=false)のEXP付与経路が無い。"
                        + "isQualityStamped で弾いた先で TrinityForgeBridge#grantArsSmithingExpOnly "
                        + "を呼ぶこと。");
    }

    @Test
    @DisplayName("品質を刻めないtfcatalog儀式結果でも grantArsSmithingExpOnly でEXPを付与している"
            + "(TrinityForgeBridge)")
    void catalogBridgeGrantsExpEvenWithoutQualityStamp() throws Exception {
        String source = flattened("src/main/java/com/arspaper/integration/TrinityForgeBridge.java");

        assertTrue(source.contains("grantArsSmithingExpOnly(item, crafter, materialTokens)"),
                "finalizeCatalogRitualResult の else 分岐(装備でも刻印済みArsアイテムでもない)"
                        + "でEXP付与を呼んでいない。");
    }

    @Test
    @DisplayName("grantArsSmithingExpOnly のTF呼び出しは例外を握り潰さず警告ログを出す")
    void grantArsSmithingExpOnlyLogsOnFailure() throws Exception {
        String source = flattened("src/main/java/com/arspaper/integration/TrinityForgeBridge.java");

        int methodStart = source.indexOf("public static void grantArsSmithingExpOnly(");
        assertTrue(methodStart >= 0, "grantArsSmithingExpOnly メソッドが見つからない");
        String methodBody = source.substring(methodStart, Math.min(source.length(), methodStart + 800));

        assertTrue(methodBody.contains("catch (Throwable t)"),
                "TF側APIの不整合(フォークの libs/TrinityForge.jar が古い等)を吸収するcatchが無い");
        // flattened()で空白は1個に潰されているので、空catchは必ず "{ }" の形になる。
        assertFalse(methodBody.contains("catch (Throwable t) { }"),
                "例外を完全に握り潰している(空catch)。原理的に観測不能になるので"
                        + "最低限の警告ログを残すこと。");
        assertTrue(methodBody.contains(".warning("),
                "grantArsSmithingExpOnly の catch 節が警告ログを出していない");
    }

    private static String flattened(String relativePath) throws Exception {
        String source = Files.readString(Path.of(relativePath));
        return source.replaceAll("\\s+", " ");
    }
}
