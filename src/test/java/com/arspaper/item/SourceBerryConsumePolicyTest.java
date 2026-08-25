package com.arspaper.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-258: ソースベリーが満腹時に使えず、売りのマナ回復が死んでいた件の境界。
 *
 * <p>⚠ この2本は「バニラに任せる区間」と「手動で消費する区間」がちょうど接していることを
 * 固定する。境界をずらすと 1クリック2個消費（重なる）か 永久に食べられない（隙間ができる）。
 */
class SourceBerryConsumePolicyTest {

    @Test
    void fullHungerNeedsManualConsumeBecauseVanillaRefusesToEat() {
        assertTrue(SourceBerryConsumePolicy.needsManualConsume(20));
        assertTrue(SourceBerryConsumePolicy.needsManualConsume(21)); // 想定外の値でも安全側
    }

    @Test
    void anyMissingHungerIsLeftToVanilla() {
        assertFalse(SourceBerryConsumePolicy.needsManualConsume(19));
        assertFalse(SourceBerryConsumePolicy.needsManualConsume(0));
    }
}
