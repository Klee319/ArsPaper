package com.arspaper.mana;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * マナ基礎値3項目({@code mana.default-max} / {@code mana.default-regen-rate} /
 * {@code mana.regen-interval-ticks})の読み口を固定するテスト。
 *
 * <p><b>なぜ要るか(2026-08-16)</b>: この3項目は TrinityForge の {@code combat/base-stats.yml}
 * ({@code mana-max-base} / {@code mana-regen-base} / {@code mana-regen-interval-ticks})から
 * ArsPaper の {@code config.yml} へ移設した。既存のテスト
 * ({@link ManaBaseStatsResolveTest} は純関数 {@code resolve} だけ、
 * {@code SourceAutoConsumeTest} は内側 record だけを見ている)は
 * <b>移設が丸ごと no-op でも1本も落ちない</b>ため、「テストが緑だから移設できた」が成立しない。
 * 新しい読み口を直接叩いて、修正前へ戻すと落ちる状態を作るのがこのクラスの役目。
 *
 * <p>実行時サーバは無いので {@link ManaManager} は生成できない。ここで叩けるのは
 * {@link ManaConfig#fromConfig} まで(それより先の配線は
 * {@link #theMigratedKeysAreNoLongerReadFromTrinityForge()} でソース検査として固定する)。
 */
class ManaBaseValuesConfigTest {

    private static final File SHIPPED_CONFIG = new File("src/main/resources/config.yml");
    private static final Path MAIN_JAVA = Path.of("src/main/java");

    // ---- 読み取り ----

    @Test
    @DisplayName("config.yml の3キーを変えると最大マナ/回復量/周期がその値になる")
    void configuredValuesWin() {
        ManaConfig config = load("""
                mana:
                  default-max: 250
                  default-regen-rate: 12
                  regen-interval-ticks: 40
                """);

        assertEquals(250, config.defaultMax());
        assertEquals(12, config.defaultRegenRate());
        assertEquals(40, config.regenIntervalTicks());
    }

    @Test
    @DisplayName("3キーが1行も無くても移設前と同じ 100 / 5 / 20 へ落ちる")
    void missingKeysFallBackToTheValuesUsedBeforeTheMigration() {
        // 稼働中サーバの plugins/ArsPaper/config.yml には新キーが降ってこない
        // (ArsPaper#updateResourceFiles は config.yml を再抽出しない)。
        // つまり「キー無し」が既存サーバの通常状態で、ここがバランスの実効値になる。
        ManaConfig empty = load("");

        assertEquals(100, empty.defaultMax(), "既定が変わるとマナ上限が全プレイヤーで変わる");
        assertEquals(5, empty.defaultRegenRate());
        assertEquals(20, empty.regenIntervalTicks());

        // mana: セクションはあるが3キーだけ無い、という中途半端な形でも同じ。
        ManaConfig partial = load("mana:\n  per-glyph-unlock-bonus: 5\n");
        assertEquals(100, partial.defaultMax());
        assertEquals(5, partial.defaultRegenRate());
        assertEquals(20, partial.regenIntervalTicks());
    }

    @Test
    @DisplayName("数値以外を書いたら既定値へ落ちる(設定ミスで0扱いにしない)")
    void nonNumericValuesFallBackToTheDefaults() {
        ManaConfig config = load("""
                mana:
                  default-max: "たくさん"
                  default-regen-rate: fast
                  regen-interval-ticks: 1s
                """);

        assertEquals(100, config.defaultMax());
        assertEquals(5, config.defaultRegenRate());
        assertEquals(20, config.regenIntervalTicks());
    }

    // ---- 不正値のガード ----

    @Test
    @DisplayName("regen-interval-ticks: 0 以下は 1 tick へクランプする")
    void theRegenIntervalIsClampedToAtLeastOneTick() {
        // 0 を runTaskTimer の period に渡すと周期タスクが壊れる。移設前は TF 側が 0 を捨てていたので
        // 原理的に到達しなかったが、config.getInt は 0 をそのまま返すため設定エディタから保存できる。
        assertEquals(1, load("mana:\n  regen-interval-ticks: 0\n").regenIntervalTicks());
        assertEquals(1, load("mana:\n  regen-interval-ticks: -40\n").regenIntervalTicks());
    }

    @Test
    @DisplayName("default-max: 0 以下は 1 へクランプする(既定値100へ戻さない)")
    void theBaseMaxIsClampedToAtLeastOne() {
        // 最大マナ0だと魔法が一切撃てず、マナバーの割合表示も意味を失う。
        // ただし「土台0＋グリフ加算だけで伸ばす」構成は潰したくないので、100 へ戻すのではなく 1 で止める。
        assertEquals(1, load("mana:\n  default-max: 0\n").defaultMax());
        assertEquals(1, load("mana:\n  default-max: -100\n").defaultMax());
    }

    @Test
    @DisplayName("default-regen-rate: 0 は「自然回復なし」として許可し、負値だけ 0 へ寄せる")
    void aZeroRegenRateIsAValidSettingButNegativesAreNot() {
        assertEquals(0, load("mana:\n  default-regen-rate: 0\n").defaultRegenRate(),
                "0 は自然回復を切る正当な設定。既定値5へ戻すと切れなくなる");
        assertEquals(0, load("mana:\n  default-regen-rate: -5\n").defaultRegenRate(),
                "負の回復量は毎周期マナが減るという事故にしかならない");
    }

    // ---- 出荷 config.yml ----

    @Test
    @DisplayName("出荷 config.yml が3キーを 100 / 5 / 20 で宣言している")
    void theShippedConfigDeclaresAllThreeKeys() throws IOException {
        YamlConfiguration yaml = shippedYaml();

        // 「書かれていること」自体が要件: 設定エディタの ArsPaper 全体設定 (config) 画面は
        // config.yml を開いて編集するので、キーが無いと編集対象として出てこない。
        assertTrue(yaml.contains("mana.default-max"), "出荷 config.yml に mana.default-max が無い");
        assertTrue(yaml.contains("mana.default-regen-rate"),
                "出荷 config.yml に mana.default-regen-rate が無い");
        assertTrue(yaml.contains("mana.regen-interval-ticks"),
                "出荷 config.yml に mana.regen-interval-ticks が無い");

        ManaConfig config = ManaConfig.fromConfig(yaml);
        assertEquals(100, config.defaultMax(), "移設でバランスを変えてはいけない");
        assertEquals(5, config.defaultRegenRate(), "移設でバランスを変えてはいけない");
        assertEquals(20, config.regenIntervalTicks(), "移設でバランスを変えてはいけない");
    }

    @Test
    @DisplayName("出荷 config.yml のヘッダが3キーを「死にキー」と宣言したままになっていない")
    void theShippedConfigHeaderNoLongerCallsTheseKeysDead() throws IOException {
        // 2026-07-25 の移設時に「ここに書いても効かない死にキー」と明記されていた。
        // コードだけ直してこの宣言を残すと、次のセッションが逆方向へ直す。
        String header = Files.readString(SHIPPED_CONFIG.toPath());
        int manaSection = header.indexOf("\nmana:");
        assertTrue(manaSection > 0, "mana: セクションが見つからない");
        String comments = header.substring(0, manaSection);

        assertTrue(comments.contains("2026-08-16"),
                "移設し直した経緯(日付)がヘッダに無い。死にキー宣言だけが残ると誤読される");
        assertTrue(comments.contains("mana.default-max"),
                "3キーがこのファイルの真源であることをヘッダに書くこと");
        assertTrue(comments.contains("mana-onhit-percent"),
                "残り5項目が今も TrinityForge 側であることをヘッダに残すこと"
                        + "(「マナ設定は全部 config.yml」と誤読させない)");
    }

    @Test
    @DisplayName("regen-interval-ticks に「再起動が必要」と書いてある")
    void theRegenIntervalKeyWarnsThatARestartIsRequired() throws IOException {
        // ManaManager は起動時に周期を焼き込むので /ars reload では張り替わらない。
        // 設定エディタから編集できるようにした以上、「保存したのに効かない」を防ぐ唯一の手段がこの注記。
        String yml = Files.readString(SHIPPED_CONFIG.toPath());
        int key = yml.indexOf("regen-interval-ticks:");
        assertTrue(key > 0, "regen-interval-ticks が出荷 config.yml に無い");

        String comment = yml.substring(Math.max(0, key - 400), key);
        assertTrue(comment.contains("再起動"),
                "regen-interval-ticks の直前コメントに再起動が必要である旨が無い");
    }

    // ---- 移設の回帰ガード ----

    /**
     * 旧経路(TrinityForge {@code combat/base-stats.yml})へ戻っていないことのガード。
     *
     * <p><b>これは実走ではなくソース検査</b>。このフォークのテストにはサーバ実装が無く
     * ({@code Bukkit.getServer()} が null)、{@link ManaManager} も
     * {@code StatusCommands} も生成できないため、「実際にどちらの読み口を呼んでいるか」を
     * 挙動では固定できない。放置すると<b>例外もログも出さずに二重管理へ戻る</b>
     * (両方に値があると、どちらが効いているのか実機でしか分からなくなる)ので、
     * せめて「TF側のキー名を文字列として持っていないこと」だけは機械的に止めておく。
     *
     * <p>コメントを落としてから見るのは、移設の経緯を説明する javadoc に旧キー名が
     * 出てくるため(素の {@code contains} だと説明文を実装と誤認して常に落ちる)。
     */
    @Test
    @DisplayName("移設した3キーを main のどのソースも base-stats から読んでいない")
    void theMigratedKeysAreNoLongerReadFromTrinityForge() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = withoutComments(Files.readString(file));
                for (String key : List.of("\"mana-max-base\"", "\"mana-regen-base\"",
                        "\"mana-regen-interval-ticks\"")) {
                    if (code.contains(key)) {
                        offenders.add(file + " → " + key);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "TrinityForge combat/base-stats.yml の旧キーを読み直している: " + offenders);
    }

    @Test
    @DisplayName("ManaManager は回復周期を引数の config から取る(起動時NPEの回帰ガード)")
    void theRegenTaskTakesItsIntervalFromTheInjectedConfig() throws IOException {
        // ArsPaper#manaManager への代入はコンストラクタが返った後なので、
        // ArsPaper.getInstance().getManaManager().getConfig() 経由で周期を引くと起動時に必ずNPEになる。
        String code = withoutComments(
                Files.readString(Path.of("src/main/java/com/arspaper/mana/ManaManager.java")));

        assertTrue(code.contains("config.regenIntervalTicks()"),
                "回復周期を ManaConfig から取っていない");
        assertTrue(!code.contains("getManaManager().getConfig()"),
                "コンストラクタ経路で自分自身を getManaManager() から引くと起動時NPEになる");
    }

    // ---- ヘルパ ----

    private static ManaConfig load(String yaml) {
        return ManaConfig.fromConfig(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static YamlConfiguration shippedYaml() throws IOException {
        assertTrue(SHIPPED_CONFIG.isFile(), SHIPPED_CONFIG + " が見つからない");
        return YamlConfiguration.loadConfiguration(
                new StringReader(Files.readString(SHIPPED_CONFIG.toPath())));
    }

    /**
     * ブロックコメントと行コメントを落とす。
     * 文字列リテラルの中に {@code //} や {@code /*} を書いているソースは対象外(現状存在しない)。
     */
    private static String withoutComments(String source) {
        String noBlocks = source.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder out = new StringBuilder(noBlocks.length());
        for (String line : noBlocks.split("\n", -1)) {
            int marker = line.indexOf("//");
            out.append(marker >= 0 ? line.substring(0, marker) : line).append('\n');
        }
        return out.toString();
    }
}
