package com.arspaper.ritual;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出荷 {@code items.yml} の儀式レシピが「書いたのに永久に発動しない」状態にならないことを固定する。
 *
 * <p>この試験が生まれた事故(2026-08-01, 設計書 0-2 / K-18 / K-20):
 * <ul>
 *   <li>{@code effect-type: thread_slot_expand} の儀式が<b>リポジトリ全体で0件</b>だった。
 *       効果クラス・{@code ArsPaper} への登録・TF 側 API・{@code RitualManager} のコア非消費登録は
 *       すべて完成していて、<b>レシピ1本を書いていないだけ</b>で「装備を育てる」軸が丸ごと死んでいた。
 *       実装済みの effect-type にレシピが無いことは<b>ログにも例外にも出ない</b>ので、
 *       ここで機械的に検出する。</li>
 *   <li>{@code thread_reroll} が汎用素材しか要求しておらず、専用触媒として用意された
 *       {@code reality_thread_core}(説明文・ドロップ配線・テクスチャ完備)を1個も使っていなかった
 *       = アイテムの説明文が嘘になっていた。</li>
 * </ul>
 *
 * <p>加えて素材表記の罠を1本で潰す: {@code UnifiedRecipeLoader#parseSingleIngredient} は
 * <b>半角スペース + 半角 x</b>({@code " x"})でしか個数を切り出さない。全角の「×」や
 * {@code "x4"}(スペース無し)で書くと、素材名側に個数の文字が残ったまま
 * <b>未知の custom id</b> として扱われ、儀式が永久に成立しなくなる。
 */
class ThreadRitualRecipeConfigTest {

    private static final Path ITEMS_YML = Path.of("src/main/resources/items.yml");
    private static final Path MATERIALS_YML = Path.of("src/main/resources/materials.yml");
    private static final Path ARS_PAPER_JAVA = Path.of("src/main/java/com/arspaper/ArsPaper.java");

    /** 台座はコアから max(|x|,|z|)==2 のリング上にしか置けない = 16 マスが物理的な上限。 */
    private static final int PEDESTAL_RING_CAPACITY = 16;

    private static YamlConfiguration load(Path path) {
        File file = path.toFile();
        assertTrue(file.isFile(), "出荷 yml が見つからない: " + file.getAbsolutePath());
        return YamlConfiguration.loadConfiguration(file);
    }

    private static ConfigurationSection ritualEffects() {
        ConfigurationSection section = load(ITEMS_YML).getConfigurationSection("ritual_effects");
        assertNotNull(section, "items.yml に ritual_effects: が無い");
        return section;
    }

    /** {@code UnifiedRecipeLoader#parseSingleIngredient} と同じ切り出し(素材名だけ)。 */
    private static String materialPartOf(String token) {
        return token.contains(" x") ? token.substring(0, token.lastIndexOf(" x")).trim() : token.trim();
    }

    /** {@code UnifiedRecipeLoader#parseIngredientCount} と同じ切り出し(個数だけ)。 */
    private static int countOf(String token) {
        if (!token.contains(" x")) {
            return 1;
        }
        return Integer.parseInt(token.substring(token.lastIndexOf(" x") + 2).trim());
    }

    private static Set<String> definedMaterialIds() {
        ConfigurationSection materials = load(MATERIALS_YML).getConfigurationSection("materials");
        assertNotNull(materials, "materials.yml に materials: が無い");
        return new LinkedHashSet<>(materials.getKeys(false));
    }

    private static String sourceOf(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new AssertionError("ソースが読めない: " + path.toAbsolutePath(), unreadable);
        }
    }

    @Test
    @DisplayName("A-1: スレッド枠拡張の儀式が items.yml に存在し、コア非消費・累計上限2で書かれている")
    void slotExpandRitualIsReachable() {
        ConfigurationSection effects = ritualEffects();
        ConfigurationSection expand = effects.getConfigurationSection("thread_slot_expand_ritual");
        assertNotNull(expand, "thread_slot_expand の儀式レシピが1本も無い(効果クラスだけ実装済みで到達不能)");
        assertEquals("thread_slot_expand", expand.getString("effect-type"));

        // core-item を書くと「その素材の装備しか拡張できない」になる。任意の装備を置けるのが仕様。
        assertFalse(expand.contains("core-item"),
                "core-item を書くと拡張対象が1素材に固定され、任意の装備を置けなくなる");

        // max-slots は「1装備あたりの累計付与上限」。effect-params の外に書くと
        // ThreadSlotExpandRitualEffect が読めず、既定値 1 に落ちて +1 しか伸びない。
        ConfigurationSection params = expand.getConfigurationSection("effect-params");
        assertNotNull(params, "effect-params が無い(max-slots が既定値 1 に落ちる)");
        assertEquals(2, params.getInt("max-slots", -1),
                "effect-params.max-slots が 2 でない(枠の伸び代が設計と食い違う)");

        List<String> pedestals = expand.getStringList("pedestal-items");
        assertFalse(pedestals.isEmpty(), "台座素材が空だと『何も置かずに回せる無料の儀式』になる");
        assertTrue(expand.getInt("source", 0) > 0, "ソース消費が 0 だとソースのシンクにならない");
    }

    @Test
    @DisplayName("A-2/K-20: 振り直し儀式が『現実の芯』を要求する(説明文と実配線が一致する)")
    void rerollRitualConsumesRealityThreadCore() {
        ConfigurationSection reroll = ritualEffects().getConfigurationSection("thread_reroll");
        assertNotNull(reroll, "thread_reroll の儀式が消えている");
        List<String> materials = new ArrayList<>();
        for (String token : reroll.getStringList("pedestal-items")) {
            materials.add(materialPartOf(token));
        }
        assertTrue(materials.contains("custom:reality_thread_core"),
                "reality_thread_core は lore もドロップ表も『スレッド再抽選の触媒』と書いているのに、"
                        + "振り直し儀式が要求していない: " + materials);
    }

    @Test
    @DisplayName("items.yml が使う effect-type は全て ArsPaper.java で登録済み(未登録は実行時まで露見しない)")
    void everyEffectTypeIsRegistered() {
        String plugin = sourceOf(ARS_PAPER_JAVA);
        ConfigurationSection effects = ritualEffects();
        for (String id : effects.getKeys(false)) {
            ConfigurationSection entry = effects.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String type = entry.getString("effect-type", "craft");
            if ("craft".equals(type)) {
                continue;
            }
            assertTrue(plugin.contains("register(\"" + type + "\""),
                    id + " の effect-type '" + type + "' が RitualEffectRegistry へ登録されていない"
                            + "(儀式は『不明な儀式タイプ』で必ず失敗する)");
        }
    }

    @Test
    @DisplayName("儀式が要求する custom: 素材は全て materials.yml に実在する(綴り違いは無言死する)")
    void everyCustomIngredientExists() {
        Set<String> defined = definedMaterialIds();
        ConfigurationSection effects = ritualEffects();
        for (String id : effects.getKeys(false)) {
            ConfigurationSection entry = effects.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            List<String> tokens = new ArrayList<>(entry.getStringList("pedestal-items"));
            String core = entry.getString("core-item");
            if (core != null) {
                tokens.add(core);
            }
            for (String token : tokens) {
                String material = materialPartOf(token);
                if (!material.startsWith("custom:")) {
                    continue;
                }
                String customId = material.substring("custom:".length());
                assertTrue(defined.contains(customId),
                        id + " が未定義の custom 素材 '" + customId + "' を要求している"
                                + "(レシピは一致せず、警告も出ない)");
            }
        }
    }

    @Test
    @DisplayName("素材の個数表記は半角 ' x' でしか解釈されない(全角×やスペース無しは素材名を壊す)")
    void ingredientCountNotationIsParseable() {
        ConfigurationSection effects = ritualEffects();
        for (String id : effects.getKeys(false)) {
            ConfigurationSection entry = effects.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            int placed = 0;
            for (String token : entry.getStringList("pedestal-items")) {
                assertFalse(token.contains("×"),
                        id + " の素材 '" + token + "' が全角の × を使っている"
                                + "(パーサは ' x' しか見ないので素材名が壊れる)");
                String material = materialPartOf(token);
                assertFalse(material.matches(".*\\s+x?\\d+$"),
                        id + " の素材 '" + token + "' の個数表記が ' x<数>' 形式でない");
                placed += countOf(token);
            }
            assertTrue(placed <= PEDESTAL_RING_CAPACITY,
                    id + " は台座 " + placed + " 個を要求しているが、コア周囲のリングは "
                            + PEDESTAL_RING_CAPACITY + " マスしかない(物理的に成立しない)");
        }
    }
}
