package com.arspaper.spell.effect;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellBreakMarkerCoverageTest {

    @Test
    void everyDestructiveSyntheticBreakEffectUsesTheSharedMarkedEventHelper() throws Exception {
        List<String> effects = List.of(
                "BreakEffect.java",
                "AdvancedBreakEffect.java",
                "ExplosionEffect.java",
                "FellEffect.java",
                "CrushEffect.java",
                "CutEffect.java",
                "HarvestEffect.java",
                "SmeltEffect.java");
        Path packageDir = Path.of("src/main/java/com/arspaper/spell/effect");
        for (String effect : effects) {
            String source = Files.readString(packageDir.resolve(effect));
            assertTrue(source.contains("SpellBreakMarker.callMarkedBreakEvent("),
                    effect + " must mark its synthetic BlockBreakEvent for TrinityForge EXP attribution");
        }
    }

    @Test
    void helperAlwaysRemovesMetadataInFinally() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/arspaper/spell/effect/SpellBreakMarker.java"));
        assertTrue(source.contains("finally"));
        assertTrue(source.contains("removeMetadata(METADATA_KEY, plugin)"));
    }
}
