package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    @DisplayName("上限値は glyphs.yml から読む(ハードコードしない)")
    void capsComeFromGlyphConfig() throws Exception {
        for (String effect : DEFENSE_IGNORING_EFFECTS) {
            String src = read(effect);
            String glyph = effect.equals("SolarEffect") ? "solar" : "lunar";
            assertTrue(src.contains("getParam(\"" + glyph + "\", \"max-damage-percent-of-max-health\","),
                    effect + " の1発上限は glyphs.yml の params から読む必要がある");
            assertTrue(src.contains("getParam(\"" + glyph + "\", \"max-cast-damage-percent-of-max-health\","),
                    effect + " の1詠唱累計上限は glyphs.yml の params から読む必要がある");
        }
        String glyphs = Files.readString(Path.of("src/main/resources/glyphs.yml"));
        Pattern shipped = Pattern.compile("max-damage-percent-of-max-health: 0\\.25");
        Matcher matcher = shipped.matcher(glyphs);
        int hits = 0;
        while (matcher.find()) {
            hits++;
        }
        assertEquals(2, hits, "出荷 glyphs.yml の solar/lunar 両方に既定25%が書かれている必要がある");
    }
}
