package com.arspaper.source;

/**
 * ソースリレー網（ドミニオンワンドで結ぶ経路）の端点が、ソースを<b>どのPDCキーに</b>持っているか。
 *
 * <p><b>W-104</b>（実サーバ報告「ソースリンクから近くのソースジャーにドミニオンワンドで転送できない。
 * 設定完了通知は出るが転送は開始されない」）の真因は、<b>貯蔵先がブロック種別で違う</b>のに
 * 転送処理がそれを知らなかった点にある。
 * <ul>
 *   <li>ソースジャー … {@code arspaper:source_amount}
 *       （{@link com.arspaper.block.BlockKeys#SOURCE_AMOUNT}）</li>
 *   <li>ソースリンク … {@code arspaper:sourcelink_buffer}
 *       （{@link com.arspaper.source.sourcelink.Sourcelink#SOURCE_BUFFER}。生成したソースの蓄積先）</li>
 * </ul>
 * {@code SourceNetwork#tickTransfer} は送信元の {@code source_amount} だけを見ていたため、
 * ソースリンクを送信元にすると<b>残量が常に 0 と判定されて毎周期黙って読み飛ばされる</b>。
 * 接続自体は成立している（{@code connect} が true を返す）ので、
 * <b>「接続完了！ の緑文字は出るのに、何も起きない」</b>という見え方になる。
 *
 * <p>この enum を経由することで「どのキーを読むか」の判断が1箇所に集まる。
 * 新しいソース保持ブロックを足すときは、ここに定数を足せば転送処理は追随する。
 */
public enum SourceStorage {

    /** ソースジャー（上位ジャーを含む）。 */
    JAR,

    /** ソースリンク。生成したソースはバッファに溜まる。 */
    SOURCELINK,

    /** ソースを保持しないカスタムブロック（祭壇・儀式コア・ウェイストーン等）。 */
    NONE;

    /**
     * ブロックidの判定結果から貯蔵種別を決める。
     *
     * <p>ジャーを先に見るのは、クリエイティブジャーのように
     * 「別クラスだが custom_block_id はジャーとして書かれている」個体があるため。
     */
    public static SourceStorage of(boolean sourceJar, boolean sourcelink) {
        if (sourceJar) {
            return JAR;
        }
        if (sourcelink) {
            return SOURCELINK;
        }
        return NONE;
    }

    /**
     * この端点を<b>送信元</b>にできるか。
     * ソースリンクを送信元にできることが W-104 の修正そのもの。
     */
    public boolean canSend() {
        return this != NONE;
    }

    /**
     * この端点を<b>送信先</b>にできるか。<b>ジャーだけ</b>。
     *
     * <p>ソースを保持しないブロックへ送ると、<b>誰も読まないPDCに書き込むだけ</b>なのに
     * 送信元からは減るので、ソースが黙って消える（修正前はこれが素通りしていた）。
     * ソースリンクのバッファへ注ぐのも禁止する —— バッファは「生成した分」の置き場で、
     * 隣接ジャーへ排出するだけなので、注いでも意味がないうえに
     * ジャー→ソースリンク→隣接ジャーの往復が定常的に回り続ける。
     */
    public boolean canReceive() {
        return this == JAR;
    }
}
