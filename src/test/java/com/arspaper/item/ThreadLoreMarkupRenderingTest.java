package com.arspaper.item;

import com.arspaper.util.DisplayText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-96(実サーバ報告「フレーバーテキストのカラーコードが反映されず生の文字列が見えてしまっている。
 * 画像の item は一例」)の回帰ガード。
 *
 * <p><b>真因</b>: {@code ThreadConfig#loreText} が {@code Component.text(生文字列, GRAY)} で
 * threads.yml の {@code lore:} をそのまま包んでいた。{@code <gold>…</gold>} /
 * {@code <color:dark_purple>…</color>} は Adventure から見ればただの文字なので、
 * <b>タグが1文字も解釈されずそのまま画面に出ていた</b>。
 * {@code gacha} / {@code role_luck} / {@code role_effeciency} / {@code blindness} など
 * {@code lore:} を持つスレッド全部が該当していた。
 *
 * <p><b>なぜ {@link ThreadConfig} を new して検証しないのか</b>: {@code ThreadConfig} は静的初期化で
 * {@code PotionEffectType.SPEED} 等のバニラ定数を触るため、このフォークのテスト基盤
 * (Bukkit ランタイム無し)ではクラスをロードした瞬間に {@code ExceptionInInitializerError} で落ちる
 * ({@link ThreadConfigPotionOverrideBackCompatTest} の javadoc に既出)。
 * よってここでは<b>同じ変換を同じ入口({@link DisplayText})で再現して挙動を固定</b>し、
 * 実装がその入口を通っていることを別途ソースで確認する。
 */
class ThreadLoreMarkupRenderingTest {

    /** {@code ThreadConfig#loreText} と同じ変換。実装を変えたらここも合わせること。 */
    private static Component render(String raw) {
        return DisplayText.component(raw)
                .colorIfAbsent(NamedTextColor.GRAY);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** 出荷 threads.yml の全 {@code lore:} 行。 */
    private static List<String> shippedLoreLines() {
        File file = Path.of("src", "main", "resources", "threads.yml").toFile();
        assertTrue(file.isFile(), "出荷 threads.yml が見つからない: " + file.getAbsolutePath());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection threads = yaml.getConfigurationSection("threads");
        assertTrue(threads != null, "threads: セクションが無い");
        List<String> lines = new ArrayList<>();
        for (String id : threads.getKeys(false)) {
            lines.addAll(threads.getStringList(id + ".lore"));
        }
        return lines;
    }

    @Test
    @DisplayName("出荷 threads.yml にマークアップ付きの lore が実在する(ガードの前提)")
    void shippedThreadsYamlActuallyUsesMarkup() {
        long withMarkup = shippedLoreLines().stream().filter(DisplayText::hasMarkup).count();

        assertTrue(withMarkup > 0,
                "色指定付きの lore が1行も無い。前提が変わったならこのテスト群ごと見直すこと"
                        + "(0件のまま通すと W-96 の再発を検出できない)");
    }

    @Test
    @DisplayName("lore のタグが解釈され、生の文字列として残らない")
    void everyShippedLoreLineRendersWithoutRawTags() {
        List<String> leaked = new ArrayList<>();
        for (String line : shippedLoreLines()) {
            String rendered = plain(render(line));
            if (rendered.contains("<") || rendered.contains(">") || rendered.contains("&")) {
                leaked.add(line + "  ->  " + rendered);
            }
        }

        assertTrue(leaked.isEmpty(),
                "lore の色タグが解釈されず生のまま表示される(W-96)。"
                        + "Component.text(生文字列) ではなく DisplayText を通すこと: " + leaked);
    }

    @Test
    @DisplayName("yml が指定した色が灰色で上書きされない")
    void explicitColorSurvivesTheGrayDefault() {
        Component gold = render("<gold>ガチャの当たり確率が上がる</gold>");

        assertFalse(plain(gold).contains("gold"), "タグが本文へ漏れている: " + plain(gold));
        assertTrue(hasColor(gold, NamedTextColor.GOLD),
                "yml が指定した色が消えている。color() で塗ると指定を潰すので colorIfAbsent を使うこと");
    }

    @Test
    @DisplayName("色指定の無い行は従来どおり灰色になる")
    void plainLineKeepsTheGrayDefault() {
        Component plain = render("被弾時マナ回復 +5");

        assertTrue(hasColor(plain, NamedTextColor.GRAY),
                "色指定の無い説明文が灰色でなくなった。TF 装備の lore と体裁が揃わなくなる");
    }

    @Test
    @DisplayName("ThreadConfig が lore を DisplayText 経由で組み立てている")
    void threadConfigRoutesLoreThroughDisplayText() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "ThreadConfig.java");
        assertTrue(Files.exists(path), "ThreadConfig.java が見つからない: " + path.toAbsolutePath());
        String source = Files.readString(path);

        int start = source.indexOf("private static net.kyori.adventure.text.Component loreText(");
        assertTrue(start >= 0, "loreText の定義が見つからない(改名したならこのテストも直すこと)");
        String body = source.substring(start, Math.min(source.length(), start + 600));

        assertTrue(body.contains("DisplayText.component(text)"),
                "loreText が DisplayText を通っていない。yml の色タグが生表示に戻る(W-96): " + body);
        assertTrue(body.contains("colorIfAbsent"),
                "loreText が色を colorIfAbsent で当てていない。color() だと yml の色指定を潰す: " + body);
    }

    /** ルートか子孫のどこかにその色が付いているか。 */
    private static boolean hasColor(Component component, NamedTextColor color) {
        if (color.equals(component.color())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasColor(child, color)) {
                return true;
            }
        }
        return false;
    }
}
