package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-104（実サーバ報告「ソースリンクから近くのソースジャーにドミニオンワンドで転送できない。
 * 設定完了通知は出るが転送は開始されない」）の回帰ガード。
 *
 * <p>真因は {@link SourceStorage} の javadoc のとおり「貯蔵先のPDCキーがブロック種別で違う」こと。
 * ここで縛るのは転送処理が使う<b>判断そのもの</b>:
 * <ul>
 *   <li><b>ソースリンクは送信元になれる</b> —— ここが false に戻ると W-104 が丸ごと再発する。</li>
 *   <li><b>ソースを持たないブロックは送信先になれない</b> —— 受け取らせると、誰も読まないPDCに
 *       書くだけで送信元からは減るため<b>ソースが黙って消える</b>。</li>
 * </ul>
 */
class SourceStorageTest {

    @Test
    @DisplayName("ジャー判定が優先される(クリエイティブジャーのように両方に当たる個体があるため)")
    void jarWins() {
        assertEquals(SourceStorage.JAR, SourceStorage.of(true, false));
        assertEquals(SourceStorage.JAR, SourceStorage.of(true, true));
    }

    @Test
    @DisplayName("ソースリンクは SOURCELINK として解決される")
    void sourcelinkResolves() {
        assertEquals(SourceStorage.SOURCELINK, SourceStorage.of(false, true));
    }

    @Test
    @DisplayName("どちらでもないカスタムブロックは NONE")
    void otherBlocksHaveNoStorage() {
        assertEquals(SourceStorage.NONE, SourceStorage.of(false, false));
    }

    @Test
    @DisplayName("ソースリンクを送信元にできる(W-104 の本体)")
    void sourcelinkCanSend() {
        assertTrue(SourceStorage.SOURCELINK.canSend(),
                "ソースリンクが送信元になれないと、接続完了の通知だけ出て一度も転送されない(W-104)");
        assertTrue(SourceStorage.JAR.canSend());
    }

    @Test
    @DisplayName("ソースを持たないブロックは送信元にも送信先にもならない")
    void noneIsInert() {
        assertFalse(SourceStorage.NONE.canSend());
        assertFalse(SourceStorage.NONE.canReceive(),
                "受け取らせると、誰も読まないPDCへ書くだけで送信元からは減る＝ソースが黙って消える");
    }

    @Test
    @DisplayName("受け取れるのはジャーだけ")
    void onlyJarsReceive() {
        assertTrue(SourceStorage.JAR.canReceive());
        assertFalse(SourceStorage.SOURCELINK.canReceive(),
                "ソースリンクのバッファは隣接ジャーへ排出するだけの置き場。"
                        + "注ぐとジャー→リンク→隣接ジャーの往復が定常的に回り続ける");
    }
}
