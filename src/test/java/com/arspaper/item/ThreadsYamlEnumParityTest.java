package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code threads.yml} のキーと {@link ThreadType} の定数が1:1で対応していることを固定する
 * (2026-08-18)。
 *
 * <h2>何が起きていたか</h2>
 * {@code threads.yml} の末尾に {@code blindness: {}} と {@code translate: {}} が
 * <b>{@link ThreadType} 定数を伴わずに</b>置かれていた。装着可否を決める
 * {@code ThreadGui#isEffectThread} は {@code arspaper:thread_item_type} PDC を
 * {@code ThreadType.fromId} に通すので、定数が無い id は必ず {@code null} になり
 * <b>「ドロップするのに防具へ永久に挿せない」</b>状態になる。
 * yml にキーがあるので設定した側からは入れたつもりになり、<b>ログにも何も出ない</b>。
 * 逆向き(enum にあるのに yml に無い)も、説明文が既定値だけになって静かに劣化する。
 *
 * <h2>なぜソースを正規表現で読むのか</h2>
 * {@link ThreadType} は定数の初期化で {@code PotionEffectType.SPEED} 等のバニラ定数を触るため、
 * Bukkit ランタイムを持たないこのフォークのテスト基盤では<b>クラスをロードした瞬間に
 * {@code ExceptionInInitializerError} で落ちる</b>({@link ThreadConfigPotionOverrideBackCompatTest}
 * の javadoc に実機確認の経緯あり)。よって {@code ThreadType.fromId} を呼べない。
 * 読み取るのは enum 定数の第1引数(id 文字列)という<b>データ</b>だけなので、実装を書き換えても
 * 誤検知しない。
 */
class ThreadsYamlEnumParityTest {

    /** {@code public static final ThreadType NAME = new ThreadType("id", ...)} の id を拾う。 */
    private static final Pattern ENUM_CONSTANT = Pattern.compile(
            "^\\s{4}public static final ThreadType [A-Z][A-Z0-9_]* = new ThreadType\\(\"([a-z0-9_]+)\"",
            Pattern.MULTILINE);

    /**
     * <b>2026-08-18(W-102)で片方向だけの検査に変えた。</b>
     * {@code ThreadType} が実行時登録を受け付けるようになったので、
     * <b>「threads.yml にあるが組み込み定数に無い」は正常</b>（{@code ThreadConfig#registerIfUnknown}
     * が起動時に登録する。これが「エディタで設定したらスレッドになる」の実装）。
     * 逆向き（組み込み定数にあるのに yml に無い）だけは今も静かな劣化なので落とす。
     * 実行時登録の配線そのものは {@link #configRegistersUnknownThreadIds()} が縛る。
     */
    @Test
    @DisplayName("組み込み定数はすべて threads.yml にも書かれている")
    void everyBuiltInThreadHasAYamlEntry() throws IOException {
        Set<String> builtInIds = enumIds();
        Set<String> yamlKeys = yamlKeys();

        assertTrue(builtInIds.size() > 40,
                "ThreadType.java から id を " + builtInIds.size() + " 件しか読めていない(空振りしている)");

        List<String> builtInOnly = new ArrayList<>(builtInIds);
        builtInOnly.removeAll(yamlKeys);
        assertEquals(List.of(), builtInOnly,
                "ThreadType にあるが threads.yml に無い ── display_name / lore が既定値だけになる");
    }

    @Test
    @DisplayName("組み込み定数に無い threads.yml の id は実行時登録される(W-102 の配線)")
    void configRegistersUnknownThreadIds() throws IOException {
        // ThreadConfig は Bukkit の設定APIを直接触るのでこの基盤では動かせない(このクラスの
        // javadoc と同じ制約)。したがって縛れるのは「登録経路が読み込みループ内に実在すること」まで。
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "ThreadConfig.java");
        assertTrue(Files.exists(path), "ThreadConfig.java が見つからない: " + path.toAbsolutePath());
        String source = Files.readString(path);

        assertTrue(source.contains("registerIfUnknown(key, section)"),
                "threads.yml の読み込みループが未知idの登録を呼んでいない。"
                        + "エディタで足したスレッドが ThreadType.fromId で null になり、"
                        + "防具に挿せず品質も乗らない状態へ逆戻りする(W-102)");
        assertTrue(source.contains("ThreadType.register("),
                "ThreadType.register を一度も呼んでいない(W-102)");

        String threadType = Files.readString(
                Path.of("src", "main", "java", "com", "arspaper", "item", "ThreadType.java"));
        assertTrue(threadType.contains("public static ThreadType register("),
                "ThreadType に実行時登録の入口が無い(W-102)");
        assertTrue(threadType.contains("BY_ID.put(id, this)"),
                "組み込み定数と実行時登録が同じ台帳に載っていない。"
                        + "台帳が分かれると fromId がどちらか片方しか見なくなる(W-102)");
    }

    @Test
    @DisplayName("数値キーを1つも持たないスレッドは threads.yml に lore を書いている")
    void effectlessThreadsCarryExplicitLore() {
        // ThreadType#getEffectLore は regen-bonus/mana-bonus 等の数値からしか行を作らないので、
        // 効果の実体が別所(thread-sets.yml のセット効果 / TF の item-stats.yml)にあるスレッドは
        // lore: を書かないと説明文が0行になる。
        ConfigurationSection threads = threadsSection();
        List<String> missing = new ArrayList<>();
        for (String key : threads.getKeys(false)) {
            ConfigurationSection entry = threads.getConfigurationSection(key);
            if (entry == null) {
                missing.add(key + "(中身が空)");
                continue;
            }
            if (entry.getString("display_name") == null) {
                missing.add(key + "(display_name が無い)");
            }
        }
        assertEquals(List.of(), missing, "threads.yml のエントリに最低限の情報が無い");
    }

    private static Set<String> enumIds() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "ThreadType.java");
        assertTrue(Files.exists(path), "ThreadType.java が見つからない: " + path.toAbsolutePath());
        Matcher m = ENUM_CONSTANT.matcher(Files.readString(path));
        Set<String> out = new LinkedHashSet<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static ConfigurationSection threadsSection() {
        File file = Path.of("src", "main", "resources", "threads.yml").toFile();
        assertTrue(file.isFile(), "出荷 threads.yml が見つからない: " + file.getAbsolutePath());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection threads = yaml.getConfigurationSection("threads");
        assertTrue(threads != null, "threads.yml に threads: セクションが無い");
        return threads;
    }

    private static Set<String> yamlKeys() {
        return new LinkedHashSet<>(threadsSection().getKeys(false));
    }
}
