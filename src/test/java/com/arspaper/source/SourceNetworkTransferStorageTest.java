package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-104 の配線ガード。{@link SourceStorageTest} が縛るのは「判断」だけなので、
 * <b>転送処理が実際にその判断を通っているか</b>をここで固定する。
 *
 * <p><b>なぜ挙動テストではなくソース検査なのか</b>: {@code SourceNetwork#tickTransfer} は
 * {@code TileState} と PDC を直接触る。このフォークのテスト基盤には Bukkit ランタイムも
 * MockBukkit も無い（{@code build.gradle.kts} の testImplementation は JUnit と paper-api だけ）ため、
 * ブロックを1個も作れない。したがって縛れるのは配線の実在までとする。
 */
class SourceNetworkTransferStorageTest {

    private static String sourceNetworkSource() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "source", "SourceNetwork.java");
        assertTrue(Files.exists(path), path.toAbsolutePath() + " が見つからない(改名したならこのテストも直すこと)");
        return Files.readString(path);
    }

    private static String tickTransferBody() throws IOException {
        String source = sourceNetworkSource();
        int start = source.indexOf("private void tickTransfer()");
        assertTrue(start >= 0, "tickTransfer の定義が見つからない");
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, "tickTransfer の本体を切り出せない");
        return source.substring(start, end);
    }

    @Test
    @DisplayName("転送処理が貯蔵種別を経由して残量を読んでいる")
    void transferResolvesStoragePerBlockKind() throws IOException {
        String body = tickTransferBody();

        assertTrue(body.contains("storageAt(fromTile)"),
                "送信元の貯蔵種別を判定していない。ソースリンクのバッファが読まれず"
                        + "「接続完了の通知は出るのに転送されない」に戻る(W-104): " + body);
        assertTrue(body.contains("storedSource(fromTile"),
                "残量の読み出しが貯蔵種別を通っていない(W-104)");
        assertFalse(body.contains("getOrDefault(BlockKeys.SOURCE_AMOUNT"),
                "source_amount を直接読んでいる箇所が残っている。"
                        + "ソースリンクはここに残量を持たないので必ず0になる(W-104): " + body);
    }

    @Test
    @DisplayName("貯蔵種別ごとのPDCキーが正しく割り当てられている")
    void storageKeysAreWiredToTheRightPdcKey() throws IOException {
        String source = sourceNetworkSource();
        int start = source.indexOf("amountKey(SourceStorage storage)");
        assertTrue(start >= 0, "amountKey の定義が見つからない");
        String body = source.substring(start, Math.min(source.length(), start + 400));

        assertTrue(body.contains("Sourcelink.SOURCE_BUFFER"),
                "ソースリンクの貯蔵先が sourcelink_buffer になっていない(W-104): " + body);
        assertTrue(body.contains("BlockKeys.SOURCE_AMOUNT"),
                "ジャーの貯蔵先が source_amount になっていない: " + body);
    }

    @Test
    @DisplayName("送信先はジャーに限定され、容量はジャー個体のものを見る")
    void destinationIsRestrictedToJarsWithPerJarCapacity() throws IOException {
        String body = tickTransferBody();

        assertTrue(body.contains("storageAt(toTile).canReceive()"),
                "送信先を絞っていない。ソースを保持しないブロックへ送ると"
                        + "誰も読まないPDCへ書くだけで送信元からは減る＝黙って消える: " + body);
        assertTrue(body.contains("SourceJar.maxSource(toTile)"),
                "容量に static な MAX_SOURCE(設定未読込時のフォールバック)を使っている。"
                        + "上位ジャーが網経由だけ 10,000 で頭打ちになる: " + body);
        assertFalse(body.contains("SourceJar.MAX_SOURCE"),
                "static フォールバック容量が残っている: " + body);
    }
}
