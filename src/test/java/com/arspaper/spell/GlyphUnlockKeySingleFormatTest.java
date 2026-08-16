package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDC キー {@code arspaper:unlocked_glyphs}（筆記台の写本解放集合）の所有権を固定する。
 *
 * <p><b>なぜ要るのか（2026-08-16 に撤去した実バグ）。</b>
 * かつて {@code com.arspaper.spell.UnlockedGlyphs} が、自前構築した
 * {@code new NamespacedKey(plugin, "unlocked_glyphs")}（プラグイン名が ArsPaper なので
 * 結果的に {@code ManaKeys.UNLOCKED_GLYPHS} と同一キー）へ <b>0x1F 区切り</b>で書いていた。
 * 他の全ての読み手は Gson の JSON 配列を期待しているため、これは次の 2 つの問題を抱えていた。
 * <ol>
 *   <li><b>装填済みのデータ破壊。</b> {@code UnlockedGlyphs#add} には呼び出し元が 1 件も無く
 *       （入口の右クリック解放は 2026-07-23 に削除済み）実害は出ていなかったが、誰かが配線した
 *       瞬間に全プレイヤーの写本集合が非 JSON に化ける。{@code ScribingTableGui} は
 *       try/catch 無しで {@code JsonParser} を呼ぶので筆記台 GUI が開かなくなり、
 *       {@code SpellCaster} は空集合へ倒れて魔法が一切撃てなくなる。</li>
 *   <li><b>直し方を間違えると権限バイパスになる。</b> 「形式を JSON に揃えれば直る」ように
 *       見えるが、{@code UsageGate#hasPermission} へ到達する経路は全て
 *       <b>写本解放済みを先に確定させてから</b>入る。よって写本集合を OR 参照させると
 *       常に true になり、UNLOCK Model Y（入手は自由・使用に perk が必要）が全経路で無効化される。
 *       正しい対処は OR 経路ごと撤去することだった。</li>
 * </ol>
 *
 * <p>このテストは「1 キー 1 形式」と「使用ゲートは写本集合を見ない」を機械的に固定する。
 * 実サーバ経路（perk 未所持で詠唱が拒否されること）は、テストクラスパスに
 * {@code libs/TrinityForge.jar} が無く {@code UsageGate} が {@code catch(Throwable) -> true} で
 * fail-open するため<b>ここでは証明できない</b>。実機確認が別途要る。
 */
class GlyphUnlockKeySingleFormatTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

    /** {@code ...getPersistentDataContainer().set(ManaKeys.UNLOCKED_GLYPHS, ...)} を検出する。 */
    private static final Pattern WRITE_CALL =
            Pattern.compile("set\\(\\s*ManaKeys\\.UNLOCKED_GLYPHS");

    @Test
    @DisplayName("このキーへ書くのは JSON を書く2箇所だけ（第3の形式を二度と生やさない）")
    void onlyTheTwoJsonWritersMayWriteTheKey() throws IOException {
        Set<String> writers = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (WRITE_CALL.matcher(source).find()) {
                    writers.add(MAIN_JAVA.relativize(file).toString().replace('\\', '/'));
                }
            }
        }

        assertEquals(
                Set.of("com/arspaper/command/handlers/GlyphCommands.java",
                       "com/arspaper/gui/ScribingTableGui.java"),
                writers,
                "書き手が増減した。増えたなら Gson の JSON 配列で書いているか確認すること。"
                        + " 非 JSON で書くと全読み手が壊れる（クラス javadoc 参照）");

        for (String writer : writers) {
            String source = Files.readString(MAIN_JAVA.resolve(writer));
            assertTrue(source.contains("toJson("),
                    writer + " は Gson の JSON 配列で書くこと（0x1F 等の独自形式は禁止）");
        }
    }

    @Test
    @DisplayName("0x1F 形式で書いていた UnlockedGlyphs は復活していない")
    void theLegacyUnitSeparatorWriterStaysDeleted() {
        assertFalse(Files.exists(MAIN_JAVA.resolve("com/arspaper/spell/UnlockedGlyphs.java")),
                "UnlockedGlyphs は 2026-08-16 に撤去した。"
                        + " 復活させるなら arspaper:unlocked_glyphs は絶対に再利用せず、"
                        + " 専用キーへ裸グリフキーで保存すること");
    }

    @Test
    @DisplayName("UsageGate は写本解放集合を参照しない（参照すると perk ゲートが全経路で素通りになる）")
    void usageGateNeverConsultsTheScribedGlyphSet() throws Exception {
        // コメントには経緯として key 名が出るので、テキストではなく型の依存で固定する。
        // 解放集合を持ち込む自然な経路はコンストラクタ引数なので、そこを1引数に閉じる。
        var constructors = UsageGate.class.getDeclaredConstructors();
        assertEquals(1, constructors.length, "UsageGate のコンストラクタは1本だけにすること");
        assertEquals(1, constructors[0].getParameterCount(),
                "UsageGate に解放集合を注入してはいけない（クラス javadoc 参照）");
        assertEquals("org.bukkit.plugin.java.JavaPlugin",
                constructors[0].getParameterTypes()[0].getName());

        // 実行コード側にキー参照が無いことも確認する（コメント行と import は除外）。
        String source = Files.readString(MAIN_JAVA.resolve("com/arspaper/spell/UsageGate.java"));
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            assertFalse(trimmed.contains("UNLOCKED_GLYPHS") || trimmed.contains("UnlockedGlyphs"),
                    "UsageGate の実行コードが解放集合に触れている: " + trimmed);
        }
    }
}
