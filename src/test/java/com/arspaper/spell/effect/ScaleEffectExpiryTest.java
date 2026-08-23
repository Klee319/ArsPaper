package com.arspaper.spell.effect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * スケール魔法が「抜けて入るとずっと小さいまま」になる不具合の再発防止（2026-08-23 W-191）。
 *
 * <p>効果の実体は {@code Attribute.SCALE} の {@code AttributeModifier} で、<b>これは
 * エンティティの NBT に保存される</b>。つまり解除役が {@code runTaskLater} のタスクしか無いと、
 * ログアウト・サーバ再起動・チャンクアンロードのどれか 1 つで解除役だけが消え、
 * 縮んだ（巨大化した）まま二度と戻らない。
 *
 * <p>ここで縛るのは、その復旧判断そのもの（サーバが要らない純粋な部分）:
 * <ul>
 *   <li>期限が残っていれば<b>残り時間ぶんだけ</b>張り直す（毎回 30 秒に戻さない）</li>
 *   <li>期限切れなら剥がす</li>
 *   <li><b>PDC が無いのに修飾子だけ付いている個体も剥がす</b> —— 修正前に固定化した人は
 *       これが無いと入り直しても直らない</li>
 * </ul>
 */
class ScaleEffectExpiryTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    @DisplayName("期限が残っていれば残り時間ぶんの tick を返す")
    void remainingIsConvertedToTicks() {
        assertEquals(30L, ScaleEffect.remainingTicks(NOW + 1_500L, NOW), "1.5秒 = 30tick");
        assertEquals(600L, ScaleEffect.remainingTicks(NOW + 30_000L, NOW), "30秒 = 600tick");
    }

    @Test
    @DisplayName("1 tick 未満の端数は 1 tick へ切り上げる(0 にすると即時実行になる)")
    void subTickRemainderIsRoundedUp() {
        assertEquals(1L, ScaleEffect.remainingTicks(NOW + 10L, NOW));
        assertEquals(1L, ScaleEffect.remainingTicks(NOW + 1L, NOW));
    }

    @Test
    @DisplayName("期限切れは 0 = 今すぐ剥がす")
    void expiredMeansRemoveNow() {
        assertEquals(0L, ScaleEffect.remainingTicks(NOW, NOW));
        assertEquals(0L, ScaleEffect.remainingTicks(NOW - 60_000L, NOW));
    }

    @Test
    @DisplayName("PDC が無い個体も剥がす(修正前に固定化した被害者が入り直すだけで直る)")
    void missingExpiryMeansRemoveNow() {
        assertTrue(ScaleEffect.remainingTicks(null, NOW) <= 0L);
    }

    /**
     * 修飾子キーの接頭辞は<b>実際に付ける側と剥がす側で一致していなければならない</b>。
     * 片方だけ変えると「掛かるのに二度と外れない」に戻るが、コンパイルは通ってしまう。
     */
    @Test
    @DisplayName("付ける側の修飾子キーは剥がす側の接頭辞と同じものを使っている")
    void applyUsesTheSamePrefixAsRemoval() throws Exception {
        Path source = Path.of("src/main/java/com/arspaper/spell/effect/ScaleEffect.java");
        String src = Files.readString(source);
        assertEquals("arspaper_scale_", ScaleEffect.MODIFIER_PREFIX);
        assertTrue(src.contains("new NamespacedKey(plugin, MODIFIER_PREFIX + entityUUID)"),
                "修飾子キーは MODIFIER_PREFIX から組むこと(直書きに戻すと剥がす側とずれる)");
    }
}
