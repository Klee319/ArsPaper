package com.arspaper.spell;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 詠唱による杖の耐久消費（{@code config.yml} の {@code cast-durability} / M-5）。
 *
 * <p>直している不具合: 杖の素材が {@code BLAZE_ROD}(最大耐久0)だったので、耐久という状態を
 * そもそも持てなかった。素材を剣系へ移しただけでは近接で殴ったときしか減らないため、
 * 詠唱側で消費させている。
 */
class CastDurabilityPolicyTest {

    private static final File SHIPPED = new File("src/main/resources/config.yml");

    @Test
    @DisplayName("耐久力エンチャント無しなら毎回 amount ぶん減る")
    void withoutUnbreakingEveryCastConsumesTheConfiguredAmount() {
        CastDurabilityPolicy policy = new CastDurabilityPolicy(true, 1, true);

        assertEquals(1, policy.damageFor(0, 0.0));
        assertEquals(1, policy.damageFor(0, 0.99));
    }

    @Test
    @DisplayName("耐久力 Lv3 は 1/4 の確率でしか減らない(バニラの道具と同じ)")
    void unbreakingLevelThreeOnlyConsumesAQuarterOfTheTime() {
        CastDurabilityPolicy policy = new CastDurabilityPolicy(true, 1, true);

        assertEquals(1, policy.damageFor(3, 0.0));
        assertEquals(1, policy.damageFor(3, 0.24));
        assertEquals(0, policy.damageFor(3, 0.25), "境界 1/(L+1) ちょうどは減らさない側");
        assertEquals(0, policy.damageFor(3, 0.99));
    }

    @Test
    @DisplayName("respect-unbreaking: false なら耐久力を無視して必ず減る")
    void disablingRespectUnbreakingIgnoresTheEnchantment() {
        CastDurabilityPolicy policy = new CastDurabilityPolicy(true, 2, false);

        assertEquals(2, policy.damageFor(3, 0.99));
    }

    @Test
    @DisplayName("enabled:false と amount<=0 はどちらも「減らさない」")
    void anInertConfigurationNeverConsumesDurability() {
        assertEquals(0, new CastDurabilityPolicy(false, 5, true).damageFor(0, 0.0));
        assertEquals(0, new CastDurabilityPolicy(true, 0, true).damageFor(0, 0.0));
        assertEquals(0, new CastDurabilityPolicy(true, -1, true).damageFor(0, 0.0));
    }

    @Test
    @DisplayName("セクションが無いときは無効側へ倒す")
    void aMissingSectionFallsBackToDisabled() {
        // config.yml が丸ごと別ファイルで上書きされる事故が実際に起きている(config.yml 冒頭のコメント)。
        // 既定を有効側に置くと、同じ事故のときに耐久だけが黙って減り続ける。
        assertFalse(CastDurabilityPolicy.from(null).enabled());
        assertFalse(load("cast-durability:\n  amount: 1\n").enabled(),
                "enabled を書き忘れたセクションを有効扱いしてはいけない");
    }

    @Test
    @DisplayName("出荷 config.yml の cast-durability が有効で、耐久力エンチャントを尊重する")
    void theShippedConfigEnablesTheConsumption() throws Exception {
        assertTrue(SHIPPED.isFile(), SHIPPED + " が見つからない");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new StringReader(Files.readString(Path.of(SHIPPED.getPath()))));
        CastDurabilityPolicy policy =
                CastDurabilityPolicy.from(yaml.getConfigurationSection("cast-durability"));

        assertTrue(policy.enabled(),
                "出荷設定で無効だと、素材を剣系へ移しても詠唱では一切減らない(=修正前と区別がつかない)");
        assertTrue(policy.amount() >= 1);
        assertTrue(policy.respectUnbreaking(),
                "耐久力エンチャントが効かないと、杖にだけ理不尽な消費が残る");
    }

    /**
     * ここだけソースを見るのは、このフォークに<b>サーバ実装が無く</b>詠唱を実走できないため。
     * 判定（上の5件）は挙動で固定できるが、「{@code SpellCaster} が実際に呼んでいるか」と
     * 「キャンセルされた詠唱で減らしていないか」は実機でしか出ない ——
     * どちらも外れると<b>例外もログも出さずに黙って no-op / 黙って耐久が溶ける</b>ので、
     * 位置関係だけでも固定しておく。文字列一致ではなく<b>出現順</b>で見ているのは、
     * 整形やリファクタで誤検知しないようにするため。
     *
     * <p><b>行コメントを落としてから見るのは実際にすり抜けたから</b>: 素の {@code indexOf} だと
     * 呼び出しをコメントアウトしても<b>コメントの中の同じ文字列</b>を拾ってしまい、
     * 「呼んでいないのに緑」になる（このテストを入れた直後に実装を壊して確認して見つけた）。
     */
    @Test
    @DisplayName("SpellCaster はキャンセル判定より後で耐久を消費する(キャンセル時に減らさない)")
    void spellCasterConsumesDurabilityOnlyAfterTheCancelCheck() throws Exception {
        String caster = withoutLineComments(
                Files.readString(Path.of("src/main/java/com/arspaper/spell/SpellCaster.java")));

        int consume = caster.indexOf("consumeCastDurability(caster, effectiveCastItem);");
        assertTrue(consume >= 0,
                "詠唱経路から耐久消費を呼んでいない。素材を剣系へ移しただけでは"
                        + "近接で殴ったときしか減らないので、これが無いと修正が丸ごと no-op になる");

        int cancelled = caster.indexOf("if (context.isCancelled()) {");
        assertTrue(cancelled >= 0, "キャンセル判定が見つからない。移設したならこのテストも直すこと");
        assertTrue(consume > cancelled,
                "キャンセルされた詠唱ではマナを返すのと同じ理屈で耐久も減らしてはいけない。"
                        + "呼び出しは isCancelled ブロックより後に置くこと");
    }

    /** 行コメント({@code //} 以降)を落とす。コメントアウトされた呼び出しを「有る」と誤認しないため。 */
    private static String withoutLineComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            int marker = line.indexOf("//");
            out.append(marker >= 0 ? line.substring(0, marker) : line).append('\n');
        }
        return out.toString();
    }

    private static CastDurabilityPolicy load(String yaml) {
        return CastDurabilityPolicy.from(YamlConfiguration.loadConfiguration(new StringReader(yaml))
                .getConfigurationSection("cast-durability"));
    }
}
