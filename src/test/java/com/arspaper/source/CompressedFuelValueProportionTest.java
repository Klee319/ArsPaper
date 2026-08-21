package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出荷 {@code sourcelinks.yml} の<b>圧縮品</b>が、素の1個あたりの値へ厳密に比例していることを固定する
 * (2026-08-21 ユーザー要望「ソースリンク系の燃料に圧縮が実装されているものはそれにも対応してほしい」)。
 *
 * <p><b>なぜ「値そのもの」ではなく「比」を固定するのか</b>: 比例から外れた瞬間、
 * <b>圧縮するだけでソースが増える／減る</b>経路になる。増える側なら無限ループ、減る側なら
 * 「圧縮すると損をする」という気付きにくい罰則になり、どちらもエラーもログも出さない。
 * 素の値（例: {@code COAL_BLOCK: 45}）は今後バランス調整で動くので、そのときに圧縮側だけ
 * 置き去りになるのを止めるには、数値 pin ではなく<b>関係</b>を縛るしかない。
 *
 * <p>倍率は {@code materials.yml} のレシピ形状から決まる ── 3x3 なら 9 倍、2x2 なら 4 倍。
 * ここでも形状を読んで倍率を出すので、あちらの形状を変えたらこのテストが落ちる。
 */
class CompressedFuelValueProportionTest {

    private static final Path SOURCELINKS = Path.of("src/main/resources/sourcelinks.yml");
    private static final Path MATERIALS = Path.of("src/main/resources/materials.yml");

    /** 圧縮品 id の接頭辞 → その素になるバニラ Material 名（{@code materials.yml} の 1x のレシピ材料）。 */
    private static Map<String, String> compressionBases() throws IOException {
        Map<String, String> bases = new LinkedHashMap<>();
        String current = null;
        boolean inRecipe = false;
        for (String line : Files.readAllLines(MATERIALS, StandardCharsets.UTF_8)) {
            Matcher id = Pattern.compile("^ {2}([A-Za-z0-9_]+):\\s*$").matcher(line);
            if (id.matches()) {
                current = id.group(1);
                inRecipe = false;
                continue;
            }
            if (current == null || !current.endsWith("_1x")) {
                continue;
            }
            if (line.startsWith("    recipe:")) {
                inRecipe = true;
                continue;
            }
            Matcher ing = Pattern.compile("^ {8}[A-Za-z]:\\s*([A-Z_]+)\\s*$").matcher(line);
            if (inRecipe && ing.matches()) {
                bases.put(current.substring(0, current.length() - 3), ing.group(1));
                inRecipe = false;
            }
        }
        return bases;
    }

    /** 圧縮品 id の接頭辞 → 1 段あたりの倍率（3x3 = 9 / 2x2 = 4）。 */
    private static Map<String, Integer> compressionFactors() throws IOException {
        Map<String, Integer> factors = new LinkedHashMap<>();
        String current = null;
        int shapeRows = 0;
        int shapeCols = 0;
        for (String line : Files.readAllLines(MATERIALS, StandardCharsets.UTF_8)) {
            Matcher id = Pattern.compile("^ {2}([A-Za-z0-9_]+):\\s*$").matcher(line);
            if (id.matches()) {
                store(factors, current, shapeRows, shapeCols);
                current = id.group(1);
                shapeRows = 0;
                shapeCols = 0;
                continue;
            }
            Matcher row = Pattern.compile("^ {8}- ([A-Za-z ]{1,3})\\s*$").matcher(line);
            if (current != null && current.endsWith("_1x") && row.matches()) {
                shapeRows++;
                shapeCols = Math.max(shapeCols, row.group(1).trim().length());
            }
        }
        store(factors, current, shapeRows, shapeCols);
        return factors;
    }

    private static void store(Map<String, Integer> factors, String id, int rows, int cols) {
        if (id != null && id.endsWith("_1x") && rows > 0 && cols > 0) {
            factors.put(id.substring(0, id.length() - 3), rows * cols);
        }
    }

    /** {@code sourcelinks.yml} の materials セクションを「キー→値」で読む（節ごと）。 */
    private static Map<String, Map<String, Integer>> materialTables() throws IOException {
        Map<String, Map<String, Integer>> tables = new LinkedHashMap<>();
        String section = null;
        boolean inMaterials = false;
        for (String line : Files.readAllLines(SOURCELINKS, StandardCharsets.UTF_8)) {
            Matcher top = Pattern.compile("^([a-z_]+):\\s*$").matcher(line);
            if (top.matches()) {
                section = top.group(1);
                inMaterials = false;
                continue;
            }
            if (line.equals("  materials:")) {
                inMaterials = true;
                tables.put(section, new LinkedHashMap<>());
                continue;
            }
            if (!inMaterials || section == null) {
                continue;
            }
            Matcher entry = Pattern.compile("^ {4}\"?([A-Za-z0-9_:]+)\"?:\\s*(\\d+)\\s*$").matcher(line);
            if (entry.matches()) {
                tables.get(section).put(entry.group(1), Integer.parseInt(entry.group(2)));
            }
        }
        return tables;
    }

    @Test
    @DisplayName("圧縮品の値は「素の値 × 圧縮倍率^段数」に厳密に一致する")
    void compressedEntriesAreExactlyProportionalToTheirPlainCounterpart() throws IOException {
        Map<String, String> bases = compressionBases();
        Map<String, Integer> factors = compressionFactors();
        Map<String, Map<String, Integer>> tables = materialTables();

        List<String> checked = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> table : tables.entrySet()) {
            Map<String, Integer> values = table.getValue();
            for (Map.Entry<String, Integer> row : values.entrySet()) {
                Matcher compressed =
                        Pattern.compile("^custom:([a-z0-9_]+)_(\\d)x$").matcher(row.getKey());
                if (!compressed.matches()) {
                    continue;
                }
                String prefix = compressed.group(1);
                int tier = Integer.parseInt(compressed.group(2));
                String plain = bases.get(prefix);
                Integer factor = factors.get(prefix);
                if (plain == null || factor == null) {
                    // ソース階梯の触媒（custom:source_shard など）は圧縮品ではないので対象外。
                    continue;
                }
                Integer plainValue = values.get(plain);
                assertTrue(plainValue != null && plainValue > 0,
                        table.getKey() + ": 圧縮品 " + row.getKey() + " が居るのに素の " + plain
                                + " がこの表に無い。素0/圧縮ありは「圧縮しないと燃えない」歪みになる");
                long expected = plainValue;
                for (int i = 0; i < tier; i++) {
                    expected *= factor;
                }
                assertEquals(expected, row.getValue().longValue(),
                        table.getKey() + "." + row.getKey() + " は " + plain + "(" + plainValue
                                + ") × " + factor + "^" + tier + " でなければならない。"
                                + "比例から外すと圧縮するだけでソースが増減する");
                checked.add(row.getKey());
            }
        }
        assertFalse(checked.isEmpty(), "圧縮品が1件も読めていない（正規表現かインデントの想定違い）");
    }
}
