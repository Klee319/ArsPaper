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
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * <p><b>2026-08-16 ユーザー決定による再仕様化:</b>
 * <ul>
 *   <li>振り直し儀式({@code thread_reroll})は<b>内部実装(効果クラス {@code ThreadRerollRitualEffect} と
 *       {@code ArsPaper} への登録)は残すが、今回のサーバではゲーム内に出さない</b>。
 *       よって {@code items.yml} に儀式エントリが<b>無いこと</b>が正であり、A-2 はその不在を固定する
 *       (かつての「存在すること」を固定する試験から反転させた)。</li>
 *   <li>スレッド枠拡張は Ⅰ/Ⅱ/Ⅲ の3段構成が正。{@code max-slots} は「1回で足す枠数」ではなく
 *       <b>装備1個あたりの累計付与上限</b>(TF の PDC {@code ritual_thread_slot_bonus} に対する上限。
 *       {@code com.trinityforge.stats.ItemFactory#expandRitualThreadSlot} が
 *       {@code ritualBonus >= maxSlots} で失敗させる)なので、3段の値は<b>単調増加</b>でなければ
 *       上位段が永久に失敗する死に儀式になる。</li>
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

    /**
     * スレッド枠拡張の儀式Ⅰ/Ⅱ/Ⅲ(2026-08-16 ユーザー決定の3段構成)。
     * 並び順が段位順であることを前提に {@link #slotExpandMaxSlotsAreStrictlyIncreasing} が使う。
     */
    private static final List<String> SLOT_EXPAND_RITUALS = List.of(
            "thread_slot_expand_ritual",
            "thread_slot_expand_ritual_2",
            "thread_slot_expand_ritual_3");

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
    @DisplayName("A-1: スレッド枠拡張の儀式3段が items.yml に存在し、コア非消費・累計上限が正の値で書かれている")
    void slotExpandRitualsAreReachable() {
        ConfigurationSection effects = ritualEffects();
        for (String id : SLOT_EXPAND_RITUALS) {
            ConfigurationSection expand = effects.getConfigurationSection(id);
            assertNotNull(expand, id + " が items.yml に無い"
                    + "(効果クラスだけ実装済みでレシピが無いと、儀式は到達不能なまま無言で死ぬ)");
            assertEquals("thread_slot_expand", expand.getString("effect-type"),
                    id + " の effect-type が thread_slot_expand でない");

            // core-item を書くと「その素材の装備しか拡張できない」になる。任意の装備を置けるのが仕様。
            assertFalse(expand.contains("core-item"),
                    id + ": core-item を書くと拡張対象が1素材に固定され、任意の装備を置けなくなる");

            // max-slots は「1装備あたりの累計付与上限」。effect-params の外に書くと
            // ThreadSlotExpandRitualEffect#resolveMaxSlots が読めず、既定値 1 に落ちる。
            ConfigurationSection params = expand.getConfigurationSection("effect-params");
            assertNotNull(params, id + ": effect-params が無い(max-slots が既定値 1 に落ちる)");
            assertTrue(params.getInt("max-slots", -1) > 0,
                    id + ": effect-params.max-slots が正の値でない(累計上限として成立しない)");

            List<String> pedestals = expand.getStringList("pedestal-items");
            assertFalse(pedestals.isEmpty(),
                    id + ": 台座素材が空だと『何も置かずに回せる無料の儀式』になる");
            assertTrue(expand.getInt("source", 0) > 0,
                    id + ": ソース消費が 0 だとソースのシンクにならない");
        }
    }

    @Test
    @DisplayName("A-1b: 3段の max-slots は単調増加(同値だと上位段が永久に失敗する死に儀式になる)")
    void slotExpandMaxSlotsAreStrictlyIncreasing() {
        ConfigurationSection effects = ritualEffects();
        int previous = 0;
        String previousId = null;
        for (String id : SLOT_EXPAND_RITUALS) {
            ConfigurationSection params = effects.getConfigurationSection(id + ".effect-params");
            assertNotNull(params, id + " の effect-params が無い");
            int maxSlots = params.getInt("max-slots", -1);
            // max-slots は儀式ごとの加算量ではなく、装備に刻まれた累計カウンタ
            // (TF PDC `ritual_thread_slot_bonus`)の上限。3段は同じカウンタを共有するので、
            // 下位段と同値以下だと下位段を回した時点で上位段が必ず失敗するようになる。
            assertTrue(maxSlots > previous,
                    id + " の max-slots(" + maxSlots + ") が "
                            + (previousId == null ? "0" : previousId + "(" + previous + ")")
                            + " を超えていない。max-slots は累計付与上限なので、"
                            + "下位段で累計が埋まると上位段が永久に失敗する死に儀式になる");
            previous = maxSlots;
            previousId = id;
        }
    }

    @Test
    @DisplayName("A-2/K-20: 振り直し儀式はゲーム内に出さない(items.yml に儀式エントリが無い)")
    void rerollRitualIsNotPublished() {
        // 2026-08-16 ユーザー決定: 振り直し儀式は「内部実装(ThreadRerollRitualEffect と
        // ArsPaper への effect-type 登録)は残してよいが、今回のサーバではゲーム内に出さない」。
        // よって items.yml に儀式エントリが無い現状が正であり、ここでは復活を検知する。
        // Java 側に効果クラスが残っていること自体は意図どおりなので検査しない。
        ConfigurationSection effects = ritualEffects();
        assertNull(effects.getConfigurationSection("thread_reroll"),
                "thread_reroll の儀式が items.yml に復活している"
                        + "(2026-08-16 決定: 内部実装は残すがゲーム内非公開)");
        for (String id : effects.getKeys(false)) {
            ConfigurationSection entry = effects.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            assertFalse("thread_reroll".equals(entry.getString("effect-type")),
                    id + " が effect-type: thread_reroll を使っている"
                            + "(別 id でも振り直し儀式をゲーム内に出さないのが 2026-08-16 の決定)");
        }
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
