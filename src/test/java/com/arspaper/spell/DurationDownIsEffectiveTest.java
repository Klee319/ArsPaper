package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>実サーバ報告（2026-08-19 / W-152）「炸裂魔法の炸裂までの時間が短縮とかで変わってない気がする」
 * の回帰ガード。</b>
 *
 * <p><b>真因（機構レベル）</b>: 共通の短縮軸 {@code SpellContext#applyDurationDown()} は
 * 「短縮は延長の半分」という設計のため <b>2個積んで初めて {@code durationLevel} が -1</b> になる。
 * ところが {@code glyphs.yml} の {@code max-augments.duration_down} が <b>1</b> の呪文が
 * 14 件あり、炸裂もその1つだった。<b>1個しか積めない ⇒ 2個目が永久に来ない ⇒ 一度も効かない。</b>
 * グリフは装着でき、詠唱もできるので、プレイヤーからは「効いていない気がする」としか見えない。
 *
 * <p><b>直し方（ユーザー判断 2026-08-19）</b>: 短縮に実用があると棚卸しできた6呪文だけ、
 * 短縮を「1個ごとに固定 tick 縮む」専用軸へ移した（{@link SpellDurationMath}）。
 * このテストは <b>yml の上限と専用パラメータ</b> と <b>式そのもの</b> の両方を固定する。
 * 上限を 1 へ戻すか、専用パラメータを消すか、式から短縮項を落とすと落ちる。
 */
class DurationDownIsEffectiveTest {

    private static final Path GLYPHS_YML = Path.of("src", "main", "resources", "glyphs.yml");

    /** 呪文 → (基本 tick のキー, 短縮1個あたりのキー, 下限のキー)。 */
    private static final Map<String, List<String>> SHORTENABLE = Map.of(
            "burst", List.of("base-fuse-ticks", "fuse-per-duration-down", "min-fuse-ticks"),
            "conjure_water", List.of("base-water-lifetime", "water-lifetime-per-duration-down",
                    "min-water-lifetime"),
            "rune", List.of("base-lifetime", "lifetime-per-duration-down", "min-lifetime"),
            "glide", List.of("base-duration", "duration-per-duration-down", "min-duration"),
            "levitate", List.of("base-duration-ticks", "duration-ticks-per-duration-down",
                    "min-duration-ticks"),
            "phantom_block", List.of("base-removal-ticks", "removal-ticks-per-duration-down",
                    "min-removal-ticks"));

    @Test
    @DisplayName("短縮が実用の6呪文は max-augments.duration_down が 2 以上 —— 1 だと専用軸でも刻みが1段しか出ない")
    void shortenableGlyphsAllowAtLeastTwoDurationDown() throws IOException {
        String yml = Files.readString(GLYPHS_YML, StandardCharsets.UTF_8);
        for (String glyph : SHORTENABLE.keySet()) {
            String block = glyphBlock(yml, glyph);
            int cap = intValue(block, "duration_down");
            assertTrue(cap >= 2, glyph + " の max-augments.duration_down が " + cap
                    + "。短縮は最低でも2個積めないと刻みが1段しか無く、W-152 の『効いていない』へ戻る");
        }
    }

    @Test
    @DisplayName("6呪文とも専用の短縮量と下限が yml にある —— 欠けると Java 側の既定値へ黙って落ちる")
    void shortenableGlyphsDeclareTheirOwnShortenParams() throws IOException {
        String yml = Files.readString(GLYPHS_YML, StandardCharsets.UTF_8);
        for (Map.Entry<String, List<String>> entry : SHORTENABLE.entrySet()) {
            String glyph = entry.getKey();
            String block = glyphBlock(yml, glyph);
            int base = intValue(block, entry.getValue().get(0));
            int perDown = intValue(block, entry.getValue().get(1));
            int min = intValue(block, entry.getValue().get(2));
            assertTrue(perDown > 0, glyph + " の短縮量が " + perDown + "。0 だと短縮しても何も起きない");
            assertTrue(min >= 1, glyph + " の下限が " + min
                    + "。0 以下だと『置いた瞬間に消える／発射直後に足元で炸裂する』が起きる");
            assertTrue(min < base, glyph + " の下限(" + min + ")が基本値(" + base
                    + ")以上。これでは短縮しても常に下限に張り付き、やはり何も変わらない");
            // 上限まで積んだときに、下限に潰されずに実際に短くなること。
            int cap = intValue(block, "duration_down");
            int shortened = SpellDurationMath.resolve(base, 0, 0, cap, perDown, min);
            assertTrue(shortened < base, glyph + " は短縮を上限(" + cap + "個)まで積んでも "
                    + base + " tick のまま。効いていないのと同じ");
        }
    }

    @Test
    @DisplayName("式: 短縮ぶんが引かれ、延長と二重計上せず、下限で止まる")
    void resolveSubtractsShortenAndStopsAtFloor() {
        // 炸裂の出荷値: 基本20 / 延長+20per / 短縮-8per / 下限4
        assertEquals(20, SpellDurationMath.resolve(20, 0, 20, 0, 8, 4), "無装着で基本値のまま");
        assertEquals(12, SpellDurationMath.resolve(20, 0, 20, 1, 8, 4), "短縮1個で -8 tick");
        assertEquals(4, SpellDurationMath.resolve(20, 0, 20, 2, 8, 4), "短縮2個で下限4に到達");
        assertEquals(4, SpellDurationMath.resolve(20, 0, 20, 9, 8, 4), "積み過ぎても下限より下へは行かない");
        assertEquals(40, SpellDurationMath.resolve(20, 1, 20, 0, 8, 4), "延長1段で +20 tick");
        assertEquals(32, SpellDurationMath.resolve(20, 1, 20, 1, 8, 4), "延長と短縮は同時に効く");
        // 短縮1個は「一度も効かない」ではなくなったことの直接証明。
        assertNotEquals(SpellDurationMath.resolve(20, 0, 20, 0, 8, 4),
                SpellDurationMath.resolve(20, 0, 20, 1, 8, 4),
                "短縮1個で値が動かないなら W-152 の状態へ逆戻りしている");
    }

    @Test
    @DisplayName("下限は 1 未満を渡しても 1 で止まる —— 0 tick は『効かない』ではなく『即時に走る』")
    void resolveNeverReturnsZero() {
        assertEquals(1, SpellDurationMath.resolve(10, 0, 0, 100, 5, 0));
        assertEquals(1, SpellDurationMath.resolve(10, 0, 0, 100, 5, -50));
    }

    /** {@code glyphs.yml} から1グリフぶんのブロック（次の同インデントのキーまで）を切り出す。 */
    private static String glyphBlock(String yml, String glyph) {
        String header = "\n  " + glyph + ":\n";
        int start = yml.indexOf(header);
        assertTrue(start >= 0, glyph + " が glyphs.yml に無い");
        int cursor = start + header.length();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^  [a-z_]+:$", java.util.regex.Pattern.MULTILINE)
                .matcher(yml);
        int end = yml.length();
        if (matcher.find(cursor)) {
            end = matcher.start();
        }
        return yml.substring(start, end);
    }

    /** ブロック内の {@code <key>: <int>} を読む（コメント付き行も可）。 */
    private static int intValue(String block, String key) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\s*" + java.util.regex.Pattern.quote(key) + ":\\s*(-?\\d+)",
                        java.util.regex.Pattern.MULTILINE)
                .matcher(block);
        assertTrue(matcher.find(), key + " が見つからない:\n" + block);
        return Integer.parseInt(matcher.group(1));
    }
}
