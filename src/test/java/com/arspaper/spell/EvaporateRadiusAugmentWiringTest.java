package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 半径増加が蒸発のブロック除去範囲へ届くことの回帰ガード。 */
class EvaporateRadiusAugmentWiringTest {

    @Test
    @DisplayName("蒸発は半径増加を自前で読み、外側の3軸ブロックAOEへ依存しない")
    void evaporateHandlesRadiusInternally() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/arspaper/spell/effect/EvaporateEffect.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("context.getAoeRadiusLevel()"),
                "蒸発が aoe_radius の値を読んでいないため、積んでも1ブロックのままになる");
        assertTrue(source.contains("boolean handlesAoeInternally() { return true; }"),
                "外側ブロックAOEは aoeRadiusLevel を読まない。蒸発は自前の半径走査を使うこと");
    }
}
