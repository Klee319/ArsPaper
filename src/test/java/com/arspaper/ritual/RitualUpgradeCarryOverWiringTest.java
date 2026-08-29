package com.arspaper.ritual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 儀式でコア装備が別の装備になるとき、品質／ロール以外が落ちないこと（2026-08-29）。
 *
 * <p>旧実装は結果 ID が {@code mage_} / {@code spell_book_} / {@code wand_} のときだけ
 * ホワイトリスト転写しており、武器・触媒やホワイトリスト外の PDC・エンチャントが消えた。
 */
class RitualUpgradeCarryOverWiringTest {

    @Test
    @DisplayName("RitualManager は接頭辞分岐せず carryOverUpgradePersistent を呼ぶ")
    void ritualManagerCopiesPersistentFromAnyCore() throws Exception {
        String source = flattened("src/main/java/com/arspaper/ritual/RitualManager.java");

        assertTrue(source.contains("carryOverUpgradePersistent(oldCore, result)"),
                "コア装備の個体データ転写が RitualManager から消えている");
        assertTrue(source.contains("carryOverUpgradeEnchantments(oldCore, result)"),
                "エンチャント転写が品質 stamp の後に無い");
        assertFalse(source.contains("rid.startsWith(\"mage_\")"),
                "mage_ 接頭辞だけの転写に戻すと武器・触媒のエンチャントがまた落ちる");
        assertFalse(source.contains("rid.startsWith(\"spell_book_\")"),
                "spell_book_ 接頭辞だけの転写に戻すとエンチャントがまた落ちる");
    }

    @Test
    @DisplayName("TrinityForgeBridge が ItemUpgradeCarryOver へ委譲している")
    void bridgeDelegatesToItemUpgradeCarryOver() throws Exception {
        String source = flattened("src/main/java/com/arspaper/integration/TrinityForgeBridge.java");

        assertTrue(source.contains("ItemUpgradeCarryOver.copyPersistent"),
                "carryOverUpgradePersistent が TF の ItemUpgradeCarryOver を呼んでいない");
        assertTrue(source.contains("ItemUpgradeCarryOver.copyEnchantments"),
                "carryOverUpgradeEnchantments が TF の ItemUpgradeCarryOver を呼んでいない");
    }

    private static String flattened(String relative) throws Exception {
        Path path = Path.of(relative);
        String raw = Files.readString(path);
        return raw.replaceAll("\\s+", " ");
    }
}
