package com.arspaper.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-08-13 レーンD監査(ArsPaperフォークのマナ系ステ総合合算監査)で発見: {@code tfArsTierUnlockBonus}
 * は以前 {@code tfEffectValue(player, EFFECT_ARS_TIER)}(dedicated-effectsの {@code ars-tier}
 * チャネル、perk保有のみを合算)と {@code tfNativeArsDouble(player, "unlockedTier")}
 * ({@code ars_tier_bonus} stat語彙、{@link com.trinityforge.integration.ars.ArsNativeBridge} が
 * パーク general + 永続バフ + 役職バフ + base-stats を合算する現行の唯一の正規チャネル)の
 * <b>両方</b>を加算していた。ところが {@code ars_magic.yml} のノードA・Eは
 * {@code buffs: {ars-tier-bonus: 1}} と {@code dedicated-effects: [id: ars-tier, value: 1]} を
 * 同じ値で両方置いていたため、該当ノード保有プレイヤーの実効tier加算が意図(+1/ノード)の
 * 2倍(該当2ノードなら合計+4、正しくは+2)になっていた。
 *
 * <p>{@link com.arspaper.item.ArmorManaListenerManaBonusGuardTest}(mana_bonus/mana_regenの
 * 二重計上再発防止)と同型の回帰ガード。このフォークのテスト基盤はBukkitランタイム/MockBukkit/
 * Mockitoを持たないため、{@code TrinityForgeBridge.java} をソーステキストとして機械的に走査する。
 */
class TrinityForgeBridgeArsTierDoubleCountGuardTest {

    private static String readSource() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "integration", "TrinityForgeBridge.java");
        assertTrue(Files.exists(path),
                "TrinityForgeBridge.javaが見つからない(パス変更時はこのテストの相対パスも更新すること): "
                        + path.toAbsolutePath());
        return Files.readString(path);
    }

    @Test
    void arsTierUnlockBonusDoesNotDoubleCountDedicatedEffectChannel() throws IOException {
        String source = readSource();
        int methodStart = source.indexOf("public static int tfArsTierUnlockBonus(Player player)");
        assertTrue(methodStart >= 0, "tfArsTierUnlockBonus(Player) が見つからない(シグネチャ変更時はこのテストも更新すること)");
        int methodEnd = source.indexOf("\n    }", methodStart);
        assertTrue(methodEnd > methodStart, "tfArsTierUnlockBonus の終端(\"    }\")が見つからない");
        String methodBody = source.substring(methodStart, methodEnd);

        // 完全一致 "tfEffectValue(player, EFFECT_ARS_TIER)" ではなく "tfEffectValue" の部分一致で判定する。
        // 理由: tfArsTierUnlockBonus は dedicated-effects チャネル(tfEffectValue)を
        // 一切参照しないのが正しい姿であり、これは呼び出しの綴り・引数の空白・定数名の変更
        // (例: "ars-tier" 直書き、EFFECT_ARS_TIER の空白なし表記、別の定数/enumへの置き換え等)に
        // 依存しない不変条件である。完全一致ガードは実装の書き方が変わるだけで無言に失効するため、
        // メソッド名(=チャネル)そのものを禁止する部分一致に緩めた。
        assertFalse(methodBody.contains("tfEffectValue"),
                "tfArsTierUnlockBonus が dedicated-effects の ars-tier チャネル(tfEffectValue)を"
                        + "再び加算している(呼び出し形が変わっていても検知される)。ars_magic.yml の"
                        + "ノードA・Eは buffs: ars-tier-bonus と dedicated-effects: id: ars-tier を"
                        + "同じ値で両方置いているため、両チャネルを合算すると実効tier加算が意図の"
                        + "2倍になる(2026-08-13に発見・修正した二重計上の再発)。");
        assertTrue(methodBody.contains("tfNativeArsDouble(player, \"unlockedTier\")"),
                "tfArsTierUnlockBonus は ars_tier_bonus stat語彙チャネル(ArsNativeBridge経由、パーク"
                        + " general + 永続バフ + 役職バフ + base-stats合算)を読む必要がある");
    }
}
