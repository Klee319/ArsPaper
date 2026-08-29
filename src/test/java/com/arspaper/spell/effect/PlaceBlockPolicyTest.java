package com.arspaper.spell.effect;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceBlockPolicyTest {

    @Test
    void refusesForeignPluginBarrels() {
        assertTrue(PlaceBlockPolicy.refuseVanillaPlaceholder(
                Material.BARREL, List.of("functionalstorage")));
        assertTrue(PlaceBlockPolicy.refuseVanillaPlaceholder(
                Material.CHEST, List.of("morechests", "trinityforge")));
    }

    @Test
    void allowsOwnedOrNonContainerBlocks() {
        assertFalse(PlaceBlockPolicy.refuseVanillaPlaceholder(
                Material.BARREL, List.of("arspaper")));
        assertFalse(PlaceBlockPolicy.refuseVanillaPlaceholder(
                Material.STONE, List.of("functionalstorage")));
        assertFalse(PlaceBlockPolicy.refuseVanillaPlaceholder(
                Material.BARREL, List.of()));
    }
}
