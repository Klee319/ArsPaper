package com.arspaper.spell;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防御無視ダメージ（{@code setHealth} で直接HPを削る経路）が
 * {@link DefenseIgnoringDamagePolicy} の上限を<b>必ず通ること</b>を固定する（2026-07-31 F4）。
 *
 * <p>{@link DefenseIgnoringDamagePolicyTest} は算術だけを検証するので、
 * 「policy は正しいのに効果側が呼んでいない」状態を検出できない — それはまさに
 * G5（公開APIがあるのに呼び出し元ゼロ）で踏んだ形。このフォークは Bukkit ランタイムを持たないため、
 * {@code MagicStatSourceWiringTest} と同じソーステキスト検査で無言の断線を止める。
 */
class DefenseIgnoringDamageWiringTest {

    /** {@code setHealth} で防御無視ダメージを与えるエフェクト。 */
    private static final List<String> DEFENSE_IGNORING_EFFECTS = List.of("SolarEffect", "LunarEffect");

    /** 1発上限・1詠唱累計上限のキー（順序に意味あり: [0]=1発、[1]=1詠唱累計）。 */
    private static final List<String> PERCENT_CAP_KEYS = List.of(
            "max-damage-percent-of-max-health",
            "max-cast-damage-percent-of-max-health");

    private static String read(String effect) throws Exception {
        return Files.readString(Path.of("src/main/java/com/arspaper/spell/effect/" + effect + ".java"));
    }

    @Test
    @DisplayName("日輪/月輪は setHealth の直前に必ず cappedDamage を通す")
    void bothEffectsClampBeforeSettingHealth() throws Exception {
        for (String effect : DEFENSE_IGNORING_EFFECTS) {
            String src = read(effect);
            assertTrue(src.contains("DefenseIgnoringDamagePolicy.cappedDamage("),
                    effect + " は上限(最大体力比)を通さずにHPを削ってはならない");
            assertTrue(src.contains("target.setHealth(finalHP)"),
                    effect + " の防御無視ダメージ適用箇所が見つからない(リネームしたなら本テストも更新すること)");
            int clampAt = src.indexOf("DefenseIgnoringDamagePolicy.cappedDamage(");
            int setAt = src.indexOf("target.setHealth(finalHP)");
            assertTrue(clampAt < setAt,
                    effect + " は setHealth より前にクランプする必要がある");
            assertTrue(src.contains("target.getHealth() - applied"),
                    effect + " はクランプ後の値(applied)をHPから引く必要がある(生の damage を引くと上限が無意味)");
        }
    }

    @Test
    @DisplayName("1詠唱あたりの累計は対象ごとに積算され、上限に達したら撃たない")
    void bothEffectsTrackPerCastBudgetPerTarget() throws Exception {
        for (String effect : DEFENSE_IGNORING_EFFECTS) {
            String src = read(effect);
            assertTrue(src.contains("Map<UUID, Double> dealtPerTarget = new HashMap<>();"),
                    effect + " は1詠唱(召喚1体)ごとに累計マップを新規作成する必要がある"
                            + "(static/共有にすると詠唱をまたいで持ち越し、逆に永久に撃てなくなる)");
            assertTrue(src.contains("dealtPerTarget.merge(target.getUniqueId(), applied, Double::sum)"),
                    effect + " は実際に適用した分だけを累計へ足す必要がある");
            assertTrue(src.contains("if (applied <= 0) return;"),
                    effect + " は累計上限に達したら弾を撃たない(演出だけ出して何も起きない状態を作らない)必要がある");
        }
    }

    /**
     * 上限値は glyphs.yml から読む（ハードコードしない）。
     *
     * <p><b>2026-07-31 F5 指摘5 の修正</b>: 初版は出荷 yml 内の
     * {@code max-damage-percent-of-max-health: 0.25} の出現回数を厳密に 2 と assert していた。
     * これは「弱すぎたら glyphs.yml の2キーで緩められる」という設計上の逃げ道と<b>矛盾</b>する —
     * 運用者が 0.35 へ書き換えるだけでフォークのテストが RED になり、レバーとして使えない。
     * 出荷 yml を<b>パースして</b>「両グリフに両キーが在り、上限として意味を持つ値であること」
     * だけを縛る形へ直した（1発上限と1詠唱累計上限を<b>対称に</b>見るので、初版の非対称も解消）。
     */
    @Test
    @DisplayName("上限値は glyphs.yml から読む(値はハードコードせずパースして意味だけ縛る)")
    void capsComeFromGlyphConfig() throws Exception {
        for (String effect : DEFENSE_IGNORING_EFFECTS) {
            String src = read(effect);
            String glyph = effect.equals("SolarEffect") ? "solar" : "lunar";
            assertTrue(src.contains("getParam(\"" + glyph + "\", \"max-damage-percent-of-max-health\","),
                    effect + " の1発上限は glyphs.yml の params から読む必要がある");
            assertTrue(src.contains("getParam(\"" + glyph + "\", \"max-cast-damage-percent-of-max-health\","),
                    effect + " の1詠唱累計上限は glyphs.yml の params から読む必要がある");
        }

        YamlConfiguration glyphs = new YamlConfiguration();
        glyphs.loadFromString(Files.readString(Path.of("src/main/resources/glyphs.yml")));
        for (String glyph : List.of("solar", "lunar")) {
            ConfigurationSection params = glyphs.getConfigurationSection("glyphs." + glyph + ".params");
            assertNotNull(params, glyph + " の params セクションが出荷 glyphs.yml に無い");
            for (String key : PERCENT_CAP_KEYS) {
                assertTrue(params.isSet(key),
                        glyph + " の " + key + " は出荷 yml に必ず書く(未記載だと既定値に依存して"
                                + "『上限が入っているか』が読み手に分からなくなる)");
                double value = params.getDouble(key);
                assertTrue(Double.isFinite(value) && value > 0.0,
                        glyph + " の " + key + " は正の値でなければ上限として機能しない(実測 " + value
                                + ")。0以下は『上限なし』=ワンショットの穴が再び開く");
            }
            double perHit = params.getDouble(PERCENT_CAP_KEYS.get(0));
            double perCast = params.getDouble(PERCENT_CAP_KEYS.get(1));
            assertTrue(perCast >= perHit,
                    glyph + " の1詠唱累計上限(" + perCast + ")が1発上限(" + perHit
                            + ")を下回ると1発目から削られ、値の意味が読めなくなる");
        }
    }

    /**
     * 累計上限に達した対象が発射スロットを占有し続けない（2026-07-31 F5 指摘3）。
     *
     * <p>旧実装は「距離順に {@code limit(発射数)} で切ってから撃つ」順序だったので、
     * 分裂拡張なし（発射数=1、既定）では最近接の1体が上限に到達した時点で
     * <b>召喚の残り時間ぜんぶが不発</b>になっていた（回復する対象がその召喚に対して無敵になる）。
     * 判断そのものは {@link DefenseIgnoringDamagePolicy#volleyOutcome} で純関数として検証しているので、
     * ここでは「エフェクト側が本当にその順序と分岐を使っているか」だけをソース検査で縛る。
     */
    @Test
    @DisplayName("累計上限に達した対象は発射スロットを占有せず、全員上限なら召喚を終了する")
    void exhaustedTargetsDoNotOccupyFiringSlots() throws Exception {
        for (String effect : DEFENSE_IGNORING_EFFECTS) {
            String src = read(effect);
            assertTrue(src.contains("DefenseIgnoringDamagePolicy.volleyOutcome("),
                    effect + " は斉射の判断を volleyOutcome(純関数)へ委譲する必要がある");
            int budgetFilterAt = src.indexOf(".filter(e -> remainingBudget(");
            int limitAt = src.indexOf(".limit(shotsPerVolley)");
            assertTrue(budgetFilterAt >= 0,
                    effect + " は累計上限の残りで対象を絞る必要がある(remainingBudget フィルタが無い)");
            assertTrue(limitAt >= 0, effect + " の発射数制限が見つからない");
            assertTrue(budgetFilterAt < limitAt,
                    effect + " は limit(発射数) より前に累計上限で絞る必要がある"
                            + "(順序が逆だと上限に達した対象がスロットを占有する)");
            assertTrue(src.contains("case END_SUMMON ->"),
                    effect + " は『範囲内の対象が全員上限』のとき召喚を終了する必要がある");
            assertTrue(src.contains("sendActionBar("),
                    effect + " は召喚終了をアクションバーで1回知らせる必要がある(無言終了にしない)");
            int endSummonAt = src.indexOf("case END_SUMMON ->");
            int cancelAt = src.indexOf("cancel();", endSummonAt);
            assertTrue(cancelAt > endSummonAt,
                    effect + " は END_SUMMON 分岐で cancel() する必要がある");
        }
    }
}
