package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-100(実サーバ報告「儀式で作成した魔導書が所有者名の人が使えない。
 * おそらく内部的には別の人でタグ付けされている？」)の回帰ガード。
 *
 * <p><b>真因は所有者台帳が2本あったこと。</b>
 * <ul>
 *   <li>TrinityForge: {@code ItemData#owner()} —— <b>lore の「所有者:」行が表示するのはこちら</b></li>
 *   <li>ArsPaper: {@code arspaper:spell_book_owner} —— {@code SpellBook#getOrCreateOwner} が
 *       <b>最初に右クリックした人</b>を焼き付ける</li>
 * </ul>
 * 装着/GUI の可否は後者だけを見る({@code SpellBook#isOwner})ので、儀式品を別の人が先に一度
 * 右クリックすると2本がずれ、<b>lore に自分の名前が出ているのに
 * 「この魔導書の所有者ではありません」で弾かれる</b>状態になる。
 * ユーザーの推測どおりだった。
 *
 * <p><b>なぜ挙動テストではなくソース検査なのか</b>: {@code SpellBook} は Bukkit のアイテム API を
 * 直接触るため、このフォークのテスト基盤(Bukkit ランタイム無し・MockBukkit 無し)では
 * インスタンス化できない({@link ThreadConfigPotionOverrideBackCompatTest} の javadoc に既出の制約)。
 * したがって縛れるのは「所有者の決定が TF 側の刻印に従属していること」がソース上に実在する点まで。
 * <b>この構造が崩れると W-100 が無言で再発する</b>ので、ここで固定する。
 */
class SpellBookOwnerAuthorityTest {

    private static String readSource(String... pathParts) throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper");
        for (String part : pathParts) {
            path = path.resolve(part);
        }
        assertTrue(Files.exists(path), path.toAbsolutePath() + " が見つからない(改名したならこのテストも直すこと)");
        return Files.readString(path);
    }

    private static String spellBookSource() throws IOException {
        return readSource("item", "impl", "SpellBook.java");
    }

    @Test
    @DisplayName("所有者の決定が TrinityForge の刻印に従属している")
    void ownerResolutionDefersToTrinityForge() throws IOException {
        String source = spellBookSource();

        assertTrue(source.contains("TrinityForgeBridge.tfOwnerId(item)"),
                "TF の所有者を一度も読んでいない。lore が表示する所有者(TF 側)と"
                        + "装着判定が見る所有者(Ars 側)がずれ、所有者本人が弾かれる(W-100)");

        int start = source.indexOf("public static String getOrCreateOwner(");
        assertTrue(start >= 0, "getOrCreateOwner の定義が見つからない");
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, "getOrCreateOwner の本体を切り出せない");
        String body = source.substring(start, end);

        assertTrue(body.contains("trinityForgeOwner(item)"),
                "getOrCreateOwner が TF の刻印を見ずに所有者を決めている。"
                        + "儀式品を別の人が先に右クリックした瞬間にずれる(W-100): " + body);
        int tfCheck = body.indexOf("trinityForgeOwner(item)");
        int claim = body.indexOf("player.getUniqueId()");
        assertTrue(claim < 0 || tfCheck < claim,
                "「右クリックした人を所有者にする」処理が TF 刻印の確認より前に来ている。"
                        + "これでは先に触った人が所有者として焼き付く(W-100)");
    }

    @Test
    @DisplayName("TF が所有者を持たないときは従来どおり初回使用者が所有者になる")
    void fallsBackToFirstUserWhenTrinityForgeHasNoOwner() throws IOException {
        String body = spellBookSource();

        assertTrue(body.contains("ItemKeys.SPELL_BOOK_OWNER, PersistentDataType.STRING, finalOwner"),
                "TF が居ない構成での従来のフォールバック(初回使用者を所有者にする)が消えている。"
                        + "Ars 単体運用で所有者が誰にも決まらなくなる");
    }

    @Test
    @DisplayName("ブリッジは TF 未ロード時に null を返す(fail-open)")
    void bridgeFailsOpenWithoutTrinityForge() throws IOException {
        String source = readSource("integration", "TrinityForgeBridge.java");

        int start = source.indexOf("public static java.util.UUID tfOwnerId(");
        assertTrue(start >= 0, "tfOwnerId の定義が見つからない");
        String body = source.substring(start, Math.min(source.length(), start + 500));

        assertTrue(body.contains("catch (Throwable t)") && body.contains("return null"),
                "TF 不在/例外時に null へ倒れていない。TF を外した構成で魔導書が使えなくなる: " + body);
        assertFalse(body.contains("throw "),
                "所有者解決で例外を投げている。表示・判定のためにゲームループを壊さないこと: " + body);
    }
}
