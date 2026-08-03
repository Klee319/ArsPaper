package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「杖のCT」配線漏れの再発防止テスト（2026-08-02）。
 *
 * <p>TF側 item-stats.yml の BLAZE_ROD#400002〜400008 / #400012〜400014 には
 * item-cooldown ステが設定済みだったが、これらは spellbooks.yml の {@code catalysts:} に
 * 未登録(catalystData==null)で、かつバインド詠唱経路では catalyst 引数が魔導書に化ける
 * (D6)ため bookTierData も非nullになり得る(cooldownMs=0でも)。結果、触媒/魔導書どちらの
 * CT経路にも入らず item-cooldown ステが一本も読まれていなかった。
 *
 * <p>このフォークは Bukkit ランタイムを持たないため、{@code MagicStatSourceWiringTest} と同じ
 * ソーステキスト検査で「呼ぶべき呼び出しが含まれるか」を固定する。
 */
class ItemCooldownGenericPathWiringTest {

    private static final String SRC = "src/main/java/com/arspaper/";

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(SRC + relative));
    }

    @Test
    @DisplayName("SpellCaster は触媒/魔導書CTを持たない詠唱でも effectiveCastItem の item-cooldown を解決する")
    void spellCasterResolvesGenericItemCooldown() throws Exception {
        String caster = read("spell/SpellCaster.java");
        assertTrue(caster.contains(
                        "org.bukkit.inventory.ItemStack effectiveCastItem = (castItem != null) ? castItem : catalyst;"),
                "実際に詠唱に使ったアイテム(杖など)を castItem 優先で解決する必要がある"
                        + "(castItem が無いバインド未経由の詠唱では catalyst 自身にフォールバック)");
        assertTrue(caster.contains(
                        "com.arspaper.integration.TrinityForgeBridge.itemCooldownSeconds(effectiveCastItem)"),
                "effectiveCastItem の item-cooldown ステを TrinityForgeBridge 経由で読む必要がある");
    }

    @Test
    @DisplayName("item-cooldown 汎用パスは触媒CT/魔導書CTのどちらかが既に持っている場合は発火しない(二重適用防止)")
    void genericPathIsMutuallyExclusiveWithCatalystAndBookCt() throws Exception {
        String caster = read("spell/SpellCaster.java");
        assertTrue(caster.contains("boolean catalystOwnsCt = catalystData != null"),
                "触媒がCTを所有しているかの判定を明示変数として持つ必要がある");
        assertTrue(caster.contains("boolean bookOwnsCt = bookTierData != null && bookTierData.getCooldownMs() > 0;"),
                "魔導書がCTを所有しているかの判定を明示変数として持つ必要がある");
        assertTrue(caster.contains("if (!catalystOwnsCt && !bookOwnsCt && effectiveCastItem != null) {"),
                "item-cooldown 汎用パスは触媒/魔導書のどちらもCTを持たない場合のみ評価する必要がある"
                        + "(重ねがけ防止)");
    }

    @Test
    @DisplayName("item-cooldown 汎用パスはゲート(詠唱不可)と startItemCooldown(ゲージ開始)の両方を呼ぶ")
    void genericPathGatesAndStartsCooldown() throws Exception {
        String caster = read("spell/SpellCaster.java");
        assertTrue(caster.contains(
                        "if (genericItemCooldownSeconds > 0.0 && caster.getCooldown(effectiveCastItem) > 0) {"),
                "item-cooldown ステを持つアイテムのCT中は詠唱をゲートする必要がある"
                        + "(バニラ由来の別要因では誤ブロックしないよう秒数>0のガードを併用)");
        assertTrue(caster.contains("} else if (genericItemCooldownSeconds > 0.0 && effectiveCastItem != null) {"),
                "詠唱成功時に item-cooldown 汎用パスでもゲージ開始を呼ぶ必要がある"
                        + "(でなければゲートだけあってゲージが二度と始まらない)");
        assertTrue(caster.contains(
                        "com.arspaper.integration.TrinityForgeBridge.startItemCooldown(\n"
                                + "                caster, effectiveCastItem, 0.0);"),
                "startItemCooldown は effectiveCastItem に対して呼ぶ必要がある(触媒/魔導書経路と混同しない)");
    }

    @Test
    @DisplayName("bridge の startItemCooldown は近接と同じ規則で cooldown_reduction を適用する")
    void bridgeAppliesCooldownReductionWithSameClamp() throws Exception {
        String bridge = read("integration/TrinityForgeBridge.java");
        assertTrue(bridge.contains("double reduction = tfStatTotal(player, \"cooldown_reduction\");"),
                "startItemCooldown は cooldown_reduction の全ソース合算を読む必要がある"
                        + "(近接 CombatListener#startItemCooldown と同じ集計値であること)");
        assertTrue(bridge.contains("seconds = seconds * Math.max(0.05, 1.0 - Math.min(0.9, reduction));"),
                "CT短縮の下限クランプ(最大90%短縮・最低5%残す)は近接側と同一である必要がある"
                        + "(ここがズレるとCTが0まで削れて無限連射になる穴ができる)");
    }
}
