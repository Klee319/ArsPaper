package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 詠唱時EXP付与（{@code exp-per-cast} / {@code exp-per-mana}）の撤去を追跡下に置くガード。
 *
 * <p><b>なぜソース文字列の検査なのか</b>: このフォークのテスト基盤は Bukkit ランタイムを持たない
 * （{@code JavaPlugin} / {@code Bukkit.getServer()} を要求するクラスはテストから触れない）。
 * {@code SpellCaster} / {@code TrinityForgeBridge} / {@code ManaConfig} はいずれも
 * プラグイン実体を要求するため、「撤去されたままであること」を実行で確かめる手段が無い。
 * 純関数へ切り出せる部分は切り出したうえで、それ以外の不変条件はソース検査で縛るのが
 * このフォークで唯一実効的な形（TF 本体側の MockBukkit テストと同じことはできない）。
 *
 * <p><b>⚠️ コメントを検査対象から外している理由（2026-07-31 F5 指摘2 の修正）</b>:
 * 初版は素の {@code contains} でソース全文を見ていたため、{@code ManaConfig} の javadoc に残る
 * 「2026-07-25 に {@code exp-per-cast} / {@code exp-per-mana} も…」という<b>歴史的経緯の記述</b>に
 * 反応していた。しかしその javadoc 行はコミット済み HEAD には存在し、削除は別セッションの
 * 未コミット変更にしか無かったため、<b>クリーンな HEAD をチェックアウトすると必ず落ちる</b>
 * テストになっていた（＝共有ワークツリーの WIP に依存した緑）。
 * 本当に縛るべき不変条件は「<b>コードが</b>これらのキーを読まない／これらのメソッドを持たない」こと
 * だけなので、コメントを落としたうえで検査する。経緯を javadoc に書き残すことは禁止しない。
 */
class LegacyCastExperienceRemovalTest {

    /**
     * Java のブロックコメント／行コメントを落とす。対象3ファイルには {@code "//"} を含む
     * 文字列リテラルが無いことを前提にした軽量実装（URL 等を足すときは注意）。
     */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }

    private static String readCode(String relativePath) throws Exception {
        return codeOnly(Files.readString(Path.of(relativePath)));
    }

    @Test
    @DisplayName("詠唱時EXP付与の実装とconfigキー読み出しがコードから消えている(コメントは対象外)")
    void spellCasterAndBridgeNoLongerContainCastOrManaExperienceFallbacks() throws Exception {
        String caster = readCode("src/main/java/com/arspaper/spell/SpellCaster.java");
        String bridge = readCode("src/main/java/com/arspaper/integration/TrinityForgeBridge.java");
        String manaConfig = readCode("src/main/java/com/arspaper/mana/ManaConfig.java");

        for (String removed : new String[] {
                "grantArsMagicExp",
                "shouldGrantLegacyCastExperience",
                "usesCompositeMagicExperience",
                "arsMagicExpPerCast",
                "arsMagicExpPerMana"
        }) {
            assertFalse(caster.contains(removed), "SpellCaster still contains " + removed);
            assertFalse(bridge.contains(removed), "TrinityForgeBridge still contains " + removed);
            assertFalse(manaConfig.contains(removed), "ManaConfig still contains " + removed);
        }
        for (String removedKey : new String[] {"exp-per-cast", "exp-per-mana"}) {
            assertFalse(caster.contains(removedKey), "SpellCaster still contains " + removedKey);
            assertFalse(bridge.contains(removedKey), "TrinityForgeBridge still contains " + removedKey);
            assertFalse(manaConfig.contains(removedKey), "ManaConfig still contains " + removedKey);
        }
    }
}
