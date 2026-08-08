package com.arspaper.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ステへ加算できるエンチャント（ダメージ増加 / 特攻 / 破壊）が魔法ダメージにも効くことの配線テスト
 * （2026-08-08）。
 *
 * <p><b>直している不具合</b>: 杖の素材を剣系へ移してダメージ増加を<b>付けられる</b>ようにはなったが、
 * 魔法ダメージ側は {@code item-stats.yml} の生の {@code attack-power} しか読んでおらず
 * {@code EnchantmentStatBridge} を一度も通っていなかった。つまり杖に付けたダメージ増加/特攻/破壊は
 * <b>近接で殴ったときだけ効き、肝心の詠唱には一切乗らない</b>という状態だった。
 * 例外もログも出ないので、実機で火力を測るまで誰も気づけない。
 *
 * <p><b>なぜソース検査なのか</b>: このフォークにはサーバ実装が無く（MockBukkit を持たない）、
 * ItemStack にエンチャントを付けて詠唱を実走させられない。係数そのもの（+5%/lv 等）は TF 側の
 * {@code EnchantmentStatBridge} が単一の定義元で、フォークは新しい数値を一切定義しないので、
 * ここで固定すべきは「その単一の定義元を確かに通しているか」だけになる。
 */
class MagicEnchantmentStatWiringTest {

    private static final Path BRIDGE = Path.of(
            "src/main/java/com/arspaper/integration/TrinityForgeBridge.java");

    /** 行コメント({@code //} 以降)を落とす。コメントアウトされた呼び出しを「有る」と誤認しないため。 */
    private static String source() throws Exception {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readString(BRIDGE).split("\n", -1)) {
            int marker = line.indexOf("//");
            out.append(marker >= 0 ? line.substring(0, marker) : line).append('\n');
        }
        return out.toString();
    }

    @Test
    @DisplayName("魔法の攻撃力はエンチャント補正を通してから基礎ダメージへ渡される")
    void magicAttackPowerGoesThroughTheEnchantmentBridge() throws Exception {
        String bridge = source();

        assertTrue(bridge.contains("EnchantmentStatBridge.adjustedAttackPower("),
                "魔法の攻撃力がエンチャント補正を通っていない。"
                        + "ダメージ増加/特攻を杖に付けても詠唱の火力が 1 も変わらない状態に戻る");
        assertTrue(bridge.contains("spellBase, enchantedAttackPower, magicalAttackPowerScale()"),
                "補正後の値(enchantedAttackPower)を基礎ダメージへ渡す必要がある。"
                        + "補正を計算しただけで生の itemAttackPower を渡すと no-op になる");
    }

    @Test
    @DisplayName("特攻の判定に victim を渡している（null だと種族一致が見られず黙って落ちる）")
    void conditionalEnchantsReceiveTheVictim() throws Exception {
        assertTrue(source().contains("EnchantmentStatBridge.bonuses(statSource, victim)"),
                "特攻(Smite/Bane/Impaling)は対象の種類が一致したときだけ乗る。victim に null を渡すと"
                        + "一般エンチャント(ダメージ増加)しか効かず、アンデッド特効などが無言で消える");
    }

    @Test
    @DisplayName("破壊(Breach)の貫通は合算マップへ merge する（近接と同じ位置）")
    void breachPenetrationIsMergedIntoTheAggregatedStats() throws Exception {
        String bridge = source();

        assertTrue(bridge.contains("attackerStats.merge(StatKeys.canonical(\"penetration\"),"),
                "Breach の貫通を TF の貫通ステへ加算していない。"
                        + "バニラ側の効果は TF が ARMOR modifier を 0 化して再導出するため既に消えており、"
                        + "ここで足さないと Breach が魔法に対して完全な死にエンチャントになる");
        assertTrue(bridge.indexOf("attackerStats.merge(StatKeys.canonical(\"penetration\"),")
                        < bridge.indexOf("return resolver.bridgeStats(attackerStats);"),
                "merge は bridgeStats より前に置くこと。後から AttackStats を組み直すと"
                        + "bridgeStats の内側にある乗算レイヤの外側になり、近接側と値がズレる");
    }
}
