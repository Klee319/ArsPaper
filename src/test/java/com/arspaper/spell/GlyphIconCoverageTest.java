package com.arspaper.spell;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * グリフのアイコンが<b>1個残らず・重複なく</b>定義されていることを固定する
 * （2026-08-22 ユーザー報告「現状どれがどの魔法かぱっと見で分からない」）。
 *
 * <p><b>何が壊れていたか。</b> アイコンが種類ごとの3種類しかなく
 * （形態=ダイヤ / 効果=エメラルド / 増強=アメジスト）、120 個のグリフが 3 種類の絵に潰れていた。
 *
 * <p><b>なぜ全件で縛るのか。</b> {@link GlyphIcons#resolve} は未定義のグリフを
 * 種類ごとの既定へ<b>黙って</b>落とす（そうしないとグリフを1個足しただけで筆記台が
 * 開かなくなる）。つまり定義漏れは実機でも例外にならず、「その1個だけ昔の絵」に戻るだけで
 * 誰も気づけない。ここで落とすのがその唯一の検出点。
 */
class GlyphIconCoverageTest {

    /**
     * 意図的に材質を共有しているグリフ。<b>今は1件も無い</b>。
     *
     * <p>空のまま固定しておくのは、重複が増えたときに黙って見分けがつかなくなるのを防ぐため。
     * 「見分けられるようにする」のがこの機能の目的なので、重複はレビューを通すこと。
     */
    private static final Set<Material> ALLOWED_DUPLICATE_ICONS = Set.of();

    private static Set<String> shippedGlyphKeys() {
        File file = new File("src/main/resources/glyphs.yml");
        assertTrue(file.isFile(), "出荷 glyphs.yml が見つからない: " + file.getAbsolutePath());
        ConfigurationSection glyphs = YamlConfiguration.loadConfiguration(file)
                .getConfigurationSection("glyphs");
        assertNotNull(glyphs, "glyphs.yml に glyphs: が無い");
        return glyphs.getKeys(false);
    }

    @Test
    @DisplayName("出荷 glyphs.yml のグリフは全部アイコンを持ち、余計な定義も無い")
    void everyShippedGlyphHasAnIcon() {
        Set<String> shipped = shippedGlyphKeys();
        assertTrue(shipped.size() > 100,
                "glyphs.yml の読み込みが空振りしている(" + shipped.size() + "件)");

        assertEquals(new TreeSet<>(shipped), new TreeSet<>(GlyphIcons.defaults().keySet()),
                "アイコン定義と出荷 glyphs.yml のグリフ集合がずれている。"
                        + "足りない分は種類ごとの既定アイコンへ黙って落ちる(＝元の『全部同じ絵』に戻る)");
    }

    @Test
    @DisplayName("アイコンの材質は重複しない(重複すると見分けられない)")
    void iconsAreDistinct() {
        Map<Material, List<String>> byMaterial = new HashMap<>();
        GlyphIcons.defaults().forEach((key, material) ->
                byMaterial.computeIfAbsent(material, m -> new ArrayList<>()).add(key));

        List<String> collisions = new ArrayList<>();
        byMaterial.forEach((material, keys) -> {
            if (keys.size() > 1 && !ALLOWED_DUPLICATE_ICONS.contains(material)) {
                collisions.add(material + " -> " + keys);
            }
        });
        assertEquals(List.of(), collisions, "同じ材質を使っているグリフがある");
    }

    @Test
    @DisplayName("未定義のグリフは種類ごとの既定へ落ちる(例外にしない)")
    void unknownGlyphFallsBackPerType() {
        assertEquals(Material.DIAMOND,
                GlyphIcons.resolve("not_a_glyph", SpellComponent.ComponentType.FORM, null));
        assertEquals(Material.EMERALD,
                GlyphIcons.resolve(null, SpellComponent.ComponentType.EFFECT, null));
        assertEquals(Material.AMETHYST_SHARD,
                GlyphIcons.resolve("not_a_glyph", SpellComponent.ComponentType.AUGMENT, null));
    }

    @Test
    @DisplayName("glyphs.yml の icon: は既定より優先。綴り違いは既定へ落とす")
    void iconOverrideWinsButBadNamesAreIgnored() {
        assertEquals(Material.FURNACE,
                GlyphIcons.resolve("smelt", SpellComponent.ComponentType.EFFECT, null),
                "前提: 既定が引けること");
        assertEquals(Material.BLAZE_ROD,
                GlyphIcons.resolve("smelt", SpellComponent.ComponentType.EFFECT, "blaze_rod"),
                "yml の icon: が効かない(配備先の glyphs.yml でしか直せない画面がある)");
        assertEquals(Material.FURNACE,
                GlyphIcons.resolve("smelt", SpellComponent.ComponentType.EFFECT, "NOT_A_MATERIAL"),
                "綴り違いで落ちると、yml の打ち間違い1個で筆記台が開かなくなる");
    }

    @Test
    @DisplayName("代表的なグリフが『それらしい』アイコンになっている")
    void spotCheckThematicIcons() {
        Map<String, Material> icons = GlyphIcons.defaults();
        assertEquals(Material.FURNACE, icons.get("smelt"), "精錬");
        assertEquals(Material.IRON_PICKAXE, icons.get("break"), "破壊");
        assertEquals(Material.GOLDEN_APPLE, icons.get("heal"), "回復");
        assertEquals(Material.LIGHTNING_ROD, icons.get("lightning"), "落雷");
        assertEquals(Material.ARROW, icons.get("projectile"), "投射");
        // 3種類だけだった頃の名残が残っていないこと。
        assertNotEquals(Material.EMERALD, icons.get("harm"), "効果グリフが一律エメラルドに戻っている");
        assertNotEquals(Material.DIAMOND, icons.get("touch"), "形態グリフが一律ダイヤに戻っている");
    }
}
