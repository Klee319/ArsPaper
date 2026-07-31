package com.arspaper.spell;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LegacyCastExperienceRemovalTest {

    @Test
    void spellCasterAndBridgeNoLongerContainCastOrManaExperienceFallbacks() throws Exception {
        String caster = Files.readString(Path.of(
                "src/main/java/com/arspaper/spell/SpellCaster.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/com/arspaper/integration/TrinityForgeBridge.java"));
        String manaConfig = Files.readString(Path.of(
                "src/main/java/com/arspaper/mana/ManaConfig.java"));

        for (String removed : new String[] {
                "grantArsMagicExp",
                "shouldGrantLegacyCastExperience",
                "usesCompositeMagicExperience",
                "arsMagicExpPerCast",
                "arsMagicExpPerMana"
        }) {
            assertFalse(caster.contains(removed), "SpellCaster still contains " + removed);
            assertFalse(bridge.contains(removed), "TrinityForgeBridge still contains " + removed);
        }
        for (String removedKey : new String[] {"exp-per-cast", "exp-per-mana"}) {
            assertFalse(caster.contains(removedKey), "SpellCaster still contains " + removedKey);
            assertFalse(bridge.contains(removedKey), "TrinityForgeBridge still contains " + removedKey);
            assertFalse(manaConfig.contains(removedKey), "ManaConfig still contains " + removedKey);
        }
    }
}
