package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * threads.yml の {@code potion-effect}/{@code potion-level}/{@code flight}(2026-08-08 追加)が、
 * 新キーを1つも書かない既存45スレッドの効果・レベルを一切変えないことを固定する回帰ガード。
 *
 * <p><b>なぜ {@link ThreadConfig} を実際に {@code new} して検証しないのか</b>: {@link ThreadType} と
 * 同じ理由で、{@code ThreadConfig.ALLOWED_POTION_EFFECTS} の静的初期化が
 * {@code PotionEffectType.SPEED} 等のバニラ定数を触るため、このフォークのテスト基盤(Bukkit
 * ランタイム無し・MockBukkit無し)では {@code ThreadConfig} クラスをロードした瞬間に
 * {@code ExceptionInInitializerError} で落ちる(2026-08-08 実機確認: {@code PotionEffectType.SPEED}
 * を1行参照するだけの最小テストで再現した)。したがって縛れるのは
 * (1)「出荷 threads.yml が新キーを1つも使っていないこと」(=そのままロードすれば全スレッドが
 * enum 既定値へフォールバックすることが構造的に保証される)と、
 * (2)「フォールバック経路がソース上に実在すること」の2点で、他のスレッド系テスト
 * ({@link ArmorManaListenerThreadPotionGuardTest} 等)と同じ静的検査の流儀に揃えてある。
 */
class ThreadConfigPotionOverrideBackCompatTest {

    private static YamlConfiguration loadShippedThreadsYaml() {
        File file = Path.of("src", "main", "resources", "threads.yml").toFile();
        assertTrue(file.isFile(), "出荷 threads.yml が見つからない: " + file.getAbsolutePath());
        return YamlConfiguration.loadConfiguration(file);
    }

    private static String readThreadConfigSource() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "ThreadConfig.java");
        assertTrue(Files.exists(path), "ThreadConfig.javaが見つからない: " + path.toAbsolutePath());
        return Files.readString(path);
    }

    @Test
    @DisplayName("出荷threads.ymlは新キー(potion-effect/potion-level/flight)を1つも使っていない"
            + "(=既存45スレッドは全部enum既定値のまま)")
    void shippedThreadsYamlUsesNoneOfTheNewKeysYet() {
        YamlConfiguration config = loadShippedThreadsYaml();
        ConfigurationSection threads = config.getConfigurationSection("threads");
        assertNotNull(threads, "threads.yml の threads: セクションが見つからない");

        for (String id : threads.getKeys(false)) {
            ConfigurationSection section = threads.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            assertFalse(section.contains("potion-effect"),
                    "スレッド '" + id + "' に potion-effect が書かれている。"
                            + "既存スレッドの後方互換テストの前提(新キー0件)が崩れている。"
                            + "新キーを実際に使い始めたら、このテストの前提そのものを見直すこと。");
            assertFalse(section.contains("potion-level"),
                    "スレッド '" + id + "' に potion-level が書かれている(前提が崩れている)");
            assertFalse(section.contains("flight"),
                    "スレッド '" + id + "' に flight が書かれている(前提が崩れている)");
        }
    }

    @Test
    @DisplayName("getPotionEffect/getPotionLevel/isFlightThreadは、config未記載ならThreadType enumの"
            + "既定値へフォールバックする経路をソース上に持っている")
    void configAccessorsFallBackToEnumDefaultsWhenKeysAreAbsent() throws IOException {
        String source = readThreadConfigSource();

        // getPotionEffect: potionNone/override どちらも無ければ type.getPotionEffect()(enum既定値)。
        assertTrue(source.contains("return type.getPotionEffect();"),
                "getPotionEffect がenum既定値へフォールバックしていない"
                        + "(config未記載の既存スレッドの効果が変わってしまう)");
        // getPotionLevel: 未記載なら1(=amplifier 0 = 効果I。既存45スレッドは全部これだった)。
        assertTrue(source.contains("if (level == null) {\n            return 1;"),
                "getPotionLevel が未記載時に1へフォールバックしていない"
                        + "(既存スレッドは全部amplifier0=レベル1だったので、これが崩れるとレベルが変わる)");
        // isFlightThread: 未記載なら type.isFlightThread()(enumでFLIGHT一択)。
        assertTrue(source.contains("return type.isFlightThread();"),
                "isFlightThread がenum既定値へフォールバックしていない"
                        + "(config未記載の既存スレッドの飛行判定が変わってしまう)");
    }

    @Test
    @DisplayName("許可される有益効果18種が仕様どおりに定義されている(有害/即時系を含まない)")
    void allowedPotionEffectsMatchTheSpecifiedEighteen() throws IOException {
        String source = readThreadConfigSource();
        String[] expectedIds = {
                "speed", "haste", "strength", "jump_boost", "regeneration", "resistance",
                "fire_resistance", "water_breathing", "invisibility", "night_vision",
                "health_boost", "absorption", "saturation", "luck", "slow_falling",
                "conduit_power", "dolphins_grace", "hero_of_the_village"
        };
        for (String id : expectedIds) {
            assertTrue(source.contains("map.put(\"" + id + "\","),
                    "許可リストに '" + id + "' が無い(仕様の有益効果18種と食い違う)");
        }
        // 有害効果・即時系が紛れ込んでいないことも明示的に固定する
        // (instant_health等を常時付け直すと無限回復になるので対象外のはず)。
        for (String forbidden : new String[] {
                "instant_health", "instant_damage", "poison", "wither", "slowness",
                "weakness", "blindness", "nausea", "hunger", "levitation",
                "bad_omen", "unluck", "darkness"}) {
            assertFalse(source.contains("map.put(\"" + forbidden + "\","),
                    "許可リストに有害/即時系効果 '" + forbidden + "' が紛れ込んでいる"
                            + "(常時付与すると事故になるため対象外のはず)");
        }
    }
}
