package com.arspaper.item;

import com.arspaper.spell.SpellManaCost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 詠唱効率スレッドの単位変換。分数 0.15 を ×100 して 15% にするのは正しいが、
 * すでに 15（パーセントポイント）を ×100 すると 1500 → キャップ 100% → 消費マナ 1 になる。
 */
class ThreadManaStatRoutingTest {

    @Test
    @DisplayName("分数 0.15 は整数 15%")
    void fractionBecomesFifteenPercent() {
        Map<String, Double> stats = new LinkedHashMap<>();
        stats.put(ThreadManaStatRouting.KEY_MANA_COST_REDUCTION_PERCENT, 0.15);
        assertEquals(15, ThreadManaStatRouting.extract(stats).costReductionPercent());
        assertFalse(stats.containsKey(ThreadManaStatRouting.KEY_MANA_COST_REDUCTION_PERCENT));
    }

    @Test
    @DisplayName("パーセントポイント 15 を二度掛けしない")
    void percentPointsAreNotMultipliedAgain() {
        Map<String, Double> stats = new LinkedHashMap<>();
        stats.put(ThreadManaStatRouting.KEY_MANA_COST_REDUCTION_PERCENT, 15.0);
        assertEquals(15, ThreadManaStatRouting.extract(stats).costReductionPercent());
    }

    @Test
    @DisplayName("ハイフンキーでも取り除いて 15% にする")
    void hyphenKeyIsRouted() {
        Map<String, Double> stats = new LinkedHashMap<>();
        stats.put("mana-cost-reduction-percent", 0.15);
        assertEquals(15, ThreadManaStatRouting.extract(stats).costReductionPercent());
        assertTrue(stats.isEmpty());
    }

    @Test
    @DisplayName("合計 15% の 200 マナは 170")
    void fifteenPercentOfTwoHundredIsOneSeventy() {
        assertEquals(170, SpellManaCost.afterPercent(200, 15));
        assertEquals(1, SpellManaCost.afterPercent(200, 100));
    }

    @Test
    @DisplayName("詠唱効率の ThreadType 既定は 0（item-stats が唯一の%源）")
    void spellCostDownBuiltinPercentIsZero() throws Exception {
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/arspaper/item/ThreadType.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        int at = src.indexOf("\"spell_cost_down\"");
        assertTrue(at > 0, "spell_cost_down 定数が無い");
        String ctor = src.substring(at, at + 280);
        assertTrue(ctor.contains("null, 0, 0, 0, 0, Material.STRING"),
                "costReduction 既定が 0 でないと item-stats と二重に乗る: " + ctor);
        assertFalse(ctor.contains("null, 0, 10, 0, 0"),
                "旧既定 10% が残っている");
    }

    @Test
    @DisplayName("マナ系 ThreadType の数値既定は全部 0（yml 未記載なら乗らない）")
    void builtinManaNumericsAreZero() throws Exception {
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/arspaper/item/ThreadType.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        for (String id : java.util.List.of("mana_regen", "mana_boost", "hit_mana_recovery",
                "damage_mana_recovery", "spell_cost_down")) {
            int at = src.indexOf("\"" + id + "\"");
            assertTrue(at > 0, id + " 定数が無い");
            String ctor = src.substring(at, at + 280);
            assertTrue(ctor.contains("null, 0, 0, 0, 0, Material.STRING"),
                    id + " の数値既定が 0 でない: " + ctor);
        }
        String config = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/arspaper/item/ThreadConfig.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(config.contains("type.getRegenBonus()"),
                "getRegenBonus が enum へフォールバックすると editor に無い値が乗る");
        assertFalse(config.contains("type.getManaBonus()"),
                "getManaBonus が enum へフォールバックすると editor に無い値が乗る");
        assertFalse(config.contains("type.getCostReductionPercent()"),
                "getCostReduction が enum へフォールバックすると editor に無い値が乗る");
        assertFalse(config.contains("loreText(\"マナコスト"),
                "グレーのマナコスト行は item-stats と二重になる");
    }
}
