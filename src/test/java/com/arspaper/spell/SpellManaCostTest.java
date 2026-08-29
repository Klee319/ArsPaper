package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellManaCostTest {

    @Test
    @DisplayName("分数もパーセントポイントも同じ 15%")
    void toPercentAcceptsFractionAndPoints() {
        assertEquals(15, SpellManaCost.toPercent(0.15));
        assertEquals(15, SpellManaCost.toPercent(15.0));
        assertEquals(0, SpellManaCost.toPercent(0.0));
        assertEquals(100, SpellManaCost.toPercent(1.0));
    }

    @Test
    @DisplayName("200 マナの 15% 軽減は 170")
    void afterPercentMatchesUserExpectation() {
        assertEquals(170, SpellManaCost.afterPercent(200, 15));
    }
}
