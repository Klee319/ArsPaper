package com.arspaper.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCostRefPlainIngredientTest {

    @Test
    void vanillaCostRejectsEnchantedGearAndStoredEnchantBooks() {
        assertTrue(ItemCostRef.vanillaCostRejectsEnchanted(false, true, false));
        assertTrue(ItemCostRef.vanillaCostRejectsEnchanted(false, false, true));
        assertFalse(ItemCostRef.vanillaCostRejectsEnchanted(false, false, false));
    }

    @Test
    void customCostStillAcceptsEnchantedStacks() {
        assertFalse(ItemCostRef.vanillaCostRejectsEnchanted(true, true, true),
                "カスタムID一致のコストまで弾くと、意図した触媒が払えなくなる");
    }
}
