package com.arspaper.spell.effect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拘束が Jump Boost 128 で空へ飛ばす不具合と、属性修飾子の永続固定の再発防止。
 */
class SnareEffectExpiryTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    @DisplayName("期限が残っていれば残り時間ぶんの tick を返す")
    void remainingIsConvertedToTicks() {
        assertEquals(30L, SnareEffect.remainingTicks(NOW + 1_500L, NOW), "1.5秒 = 30tick");
    }

    @Test
    @DisplayName("期限切れは 0 = 今すぐ剥がす")
    void expiredMeansRemoveNow() {
        assertEquals(0L, SnareEffect.remainingTicks(NOW, NOW));
        assertEquals(0L, SnareEffect.remainingTicks(NOW - 60_000L, NOW));
    }

    @Test
    @DisplayName("PDC が無い個体も剥がす(修正前に固定化した被害者が入り直すだけで直る)")
    void missingExpiryMeansRemoveNow() {
        assertTrue(SnareEffect.remainingTicks(null, NOW) <= 0L);
    }

    @Test
    @DisplayName("JUMP_BOOST 128 と SLOWNESS 255 を使わない")
    void doesNotUseLegacyByteOverflowAmplifiers() throws Exception {
        Path source = Path.of("src/main/java/com/arspaper/spell/effect/SnareEffect.java");
        String src = Files.readString(source);
        assertFalse(src.contains("PotionEffectType.JUMP_BOOST"),
                "1.21 では amplifier 128 が Jump Boost 129 になり空へ飛ばす");
        assertFalse(src.contains("PotionEffectType.SLOWNESS, duration, 255"),
                "Slowness の極端な amplifier も 1.21 では溢れる");
        assertTrue(src.contains("MODIFIER_PREFIX + \"jump\""),
                "跳躍封じは属性修飾子。キーは剥がす側の接頭辞から組む");
        assertEquals("snare_", SnareEffect.MODIFIER_PREFIX);
    }
}
