package com.arspaper.spell;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * <b>未解放グリフは1種類の絵（石炭）に潰す</b>ことを固定する
 * （2026-08-22 ユーザー報告「解放したのか解放してないのか直感的にわからなくなった。
 * 今まで解放してるやつだけカーソル移動すればよかったのに、今は一個ずつ見ないといけない」）。
 *
 * <p><b>何が壊れていたか。</b> 同日の別報告「どれがどの魔法かぱっと見で分からない」に対して
 * 120 種すべてへ個別アイコンを付けたところ、<b>解放状態が絵から完全に消えた</b>。
 * 名前の色（緑/赤）と lore には出ているが、36 個/ページの一覧を見渡すのには使えない
 * ＝ 1 個ずつカーソルを当てて確かめることになった。
 *
 * <p><b>なぜ「未解放だけ」潰すのか。</b> 実際に選んで使うのは解放済みの側なので、
 * 個別アイコンが要るのはそちら。未解放は「まだ持っていない」と分かれば足りる。
 * この非対称のおかげで両方の報告を同時に満たせる。
 */
class GlyphLockedIconTest {

    @Test
    @DisplayName("未解放は全グリフが石炭になる")
    void lockedGlyphsCollapseToOneIcon() {
        assertEquals(Material.COAL, GlyphIcons.LOCKED_ICON);
        for (Map.Entry<String, Material> entry : GlyphIcons.defaults().entrySet()) {
            for (SpellComponent.ComponentType type : SpellComponent.ComponentType.values()) {
                assertEquals(Material.COAL,
                        GlyphIcons.resolveForState(entry.getKey(), type, null, false),
                        entry.getKey() + " の未解放アイコンが石炭でない");
            }
        }
    }

    @Test
    @DisplayName("解放済みは従来どおりグリフごとの個別アイコンのまま")
    void unlockedGlyphsKeepTheirOwnIcon() {
        for (Map.Entry<String, Material> entry : GlyphIcons.defaults().entrySet()) {
            assertEquals(entry.getValue(),
                    GlyphIcons.resolveForState(entry.getKey(), SpellComponent.ComponentType.EFFECT, null, true),
                    entry.getKey() + " の解放済みアイコンが個別アイコンでない");
        }
    }

    @Test
    @DisplayName("未解放アイコンはどのグリフの個別アイコンとも被らない")
    void lockedIconIsNotAnyGlyphsOwnIcon() {
        assertFalse(GlyphIcons.defaults().containsValue(GlyphIcons.LOCKED_ICON),
                "未解放の絵と同じ材質を持つグリフがあると、解放済みなのに未解放に見える");
    }

    @Test
    @DisplayName("yml の icon: 上書きも未解放には効かない(解放状態の表示が最優先)")
    void iconOverrideDoesNotLeakIntoLockedState() {
        Material overridden = GlyphIcons.resolveForState("projectile", SpellComponent.ComponentType.FORM,
                "NETHER_STAR", true);
        assertEquals(Material.NETHER_STAR, overridden, "解放済みでは icon: の上書きが効くこと");
        assertEquals(Material.COAL,
                GlyphIcons.resolveForState("projectile", SpellComponent.ComponentType.FORM, "NETHER_STAR", false),
                "未解放で上書きが効くと、そのグリフだけ解放済みに見える");
        assertNotEquals(overridden,
                GlyphIcons.resolveForState("projectile", SpellComponent.ComponentType.FORM, "NETHER_STAR", false));
    }
}
