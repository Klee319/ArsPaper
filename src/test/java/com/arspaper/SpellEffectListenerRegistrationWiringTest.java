package com.arspaper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code SpellEffect} かつ {@code Listener} を実装するエフェクト（{@code HexEffect}/{@code ExplosionEffect}/
 * {@code BounceEffect}/{@code GlideEffect}）の {@code @EventHandler} が
 * <b>1回だけ</b>登録されることを固定する（2026-08-04）。
 *
 * <p>{@code ArsPaper#registerListeners()} は {@code spellRegistry.getAll()} から
 * {@code Listener} 実装だけを一括登録するループを持つ（「SpellEffectリスナー登録」コメントの箇所）。
 * これとは別に、エフェクト自身のコンストラクタ内で {@code Bukkit.getPluginManager().registerEvents(this, ...)}
 * を呼ぶと、同一インスタンスが2つの {@code RegisteredListener} として積まれ、
 * {@code @EventHandler} が1イベントにつき2回発火する（Bukkitは同一インスタンスの重複登録を排除しない）。
 * 実際に {@code HexEffect#onEntityDamage} がこの状態で、呪詛の追撃ダメージが2倍入っていた
 * （2026-08-04発覚、修正済み）。{@code ExplosionEffect} は今回の対称パイプライン統合で新規に
 * 同じ誤りを持ち込んでいたため合わせて修正した。
 *
 * <p>このフォークはBukkitランタイムを持たないため（{@code build.gradle.kts} は paper-api を
 * compileOnly/testImplementationするだけで実サーバ実装を含まない）、実際に
 * {@code PluginManager} へ登録して発火回数を数える統合テストは組めない。代わりに
 * (1)自己登録の再発防止、(2)一括登録ループの温存、(3)ループが機能する前提となる呼び出し順序
 * （{@code spellRegistry} が先に埋まってからループが走ること）をソーステキスト検査で固定する。
 */
class SpellEffectListenerRegistrationWiringTest {

    /**
     * {@code SpellEffect} かつ {@code Listener} を実装し、かつ自己登録してはいけないエフェクト。
     * {@code BounceEffect}/{@code GlideEffect} は元々自己登録していない「正しい形」の対照群として含める。
     */
    private static final List<String> LISTENER_EFFECTS =
            List.of("HexEffect", "ExplosionEffect", "BounceEffect", "GlideEffect");

    private static String readEffect(String effect) throws Exception {
        return Files.readString(Path.of("src/main/java/com/arspaper/spell/effect/" + effect + ".java"));
    }

    private static String readArsPaper() throws Exception {
        return Files.readString(Path.of("src/main/java/com/arspaper/ArsPaper.java"));
    }

    @Test
    @DisplayName("Listener実装のSpellEffectはコンストラクタ内で自己登録してはならない(二重登録防止)")
    void listenerEffectsDoNotSelfRegister() throws Exception {
        for (String effect : LISTENER_EFFECTS) {
            String src = readEffect(effect);
            assertFalse(src.contains("registerEvents(this"),
                    effect + " がコンストラクタ内で自己登録すると、ArsPaper#registerListeners()の"
                            + "一括登録ループと合わせて同一インスタンスが2重登録され、"
                            + "@EventHandlerが1イベントにつき2回発火する");
        }
    }

    @Test
    @DisplayName("ArsPaper#registerListenersはspellRegistry.getAll()からListener実装を一括登録する唯一の経路である")
    void arsPaperHasTheSingleBulkRegistrationLoop() throws Exception {
        String src = readArsPaper();
        assertTrue(src.contains("for (var component : spellRegistry.getAll())"),
                "SpellEffectのListener一括登録ループが見つからない(削除/リネームされた場合、"
                        + "自己登録を持たないエフェクトのリスナーが一切登録されなくなる)");
        assertTrue(src.contains("if (component instanceof org.bukkit.event.Listener listener)"),
                "一括登録ループのListener判定が見つからない");
        int loopBodyAt = src.indexOf("if (component instanceof org.bukkit.event.Listener listener)");
        int registerCallAt = src.indexOf("pluginManager.registerEvents(listener, this);");
        assertTrue(registerCallAt > loopBodyAt,
                "ループ内で実際にregisterEventsを呼んでいる箇所が見つからない");
    }

    /**
     * ループが機能するための前提: {@code onEnable()} は {@code initRegistries()}
     * （内部で {@code registerDefaultGlyphs()} を呼び、spellRegistryへ全エフェクトをregisterする）を
     * {@code registerListeners()}（一括登録ループ本体）より<b>先に</b>呼ぶ必要がある。
     * 順序が逆になると、ループ実行時点でspellRegistryが空/不完全になり、
     * 自己登録を持たないエフェクト（HexEffect/ExplosionEffect含む）のリスナーが
     * 一切登録されなくなる「無登録」の逆方向の事故になる。
     */
    @Test
    @DisplayName("onEnableはinitRegistries()をregisterListeners()より先に呼ぶ(ループ実行前にspellRegistryが埋まっている必要がある)")
    void initRegistriesRunsBeforeRegisterListeners() throws Exception {
        String src = readArsPaper();
        int initAt = src.indexOf("initRegistries();");
        int registerAt = src.indexOf("registerListeners();");
        assertTrue(initAt >= 0, "initRegistries()の呼び出しが見つからない");
        assertTrue(registerAt >= 0, "registerListeners()の呼び出しが見つからない");
        assertTrue(initAt < registerAt,
                "initRegistries()はregisterListeners()より前に呼ぶ必要がある"
                        + "(spellRegistryへの登録が一括登録ループより後になると、"
                        + "自己登録していないSpellEffectのリスナーが一切登録されない)");
    }
}
