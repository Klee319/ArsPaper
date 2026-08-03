package com.arspaper.recipe;

import com.arspaper.util.DisplayText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>出荷 yml の表示名がレシピGUIで生記号 / 内部ID にならないことを固定する。</b>
 *
 * <p>2026-08-03 の実サーバ報告: レシピ一覧に {@code &6&l無限ソース核精製} が出ていた。
 * 原因は {@link UnifiedRecipeLoader} が {@code display_name} を<b>生文字列のまま</b>
 * 儀式レシピ名へ連結していたこと(materials.yml はレガシー &記法)。
 * ここでは「出荷データ側に display_name が揃っているか」と
 * 「{@link DisplayText} を通せば記号が残らないか」を両方固定する
 * ―― どちらが欠けても画面に記号かIDが出る。
 */
class ShippedRecipeDisplayNameTest {

    /** {@code &a} / {@code §a} 形式のレガシーコード。 */
    private static final Pattern LEGACY = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");

    /** MiniMessage のタグらしき並び。 */
    private static final Pattern MINI_TAG = Pattern.compile("<[^<>]+>");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) {
        try (InputStream in = ShippedRecipeDisplayNameTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(in, resource + " が出荷リソースに存在すること");
            Object root = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return root instanceof Map ? (Map<String, Object>) root : new LinkedHashMap<>();
        } catch (Exception e) {
            throw new AssertionError("failed to read " + resource, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key) {
        Object v = root.get(key);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    /** {@code recipe:} の method を UnifiedRecipeLoader と同じ規則で判定する。 */
    private static String method(Map<String, Object> recipe) {
        if (recipe == null) {
            return null;
        }
        Object m = recipe.get("method");
        if (m != null) {
            return String.valueOf(m).toLowerCase(java.util.Locale.ROOT);
        }
        if (recipe.containsKey("core-item") || recipe.containsKey("pedestal-items")) {
            return "ritual";
        }
        if (recipe.containsKey("shape") || recipe.containsKey("ingredients")
                || recipe.containsKey("type")) {
            return "workbench";
        }
        return null;
    }

    @Test
    @DisplayName("materials.yml: 儀式レシピを持つ素材は display_name を持ち、名前に記号が残らない")
    void materialRitualNamesAreClean() {
        Map<String, Object> materials = section(load("materials.yml"), "materials");
        assertFalse(materials.isEmpty(), "materials.yml に materials: があること");

        List<String> missingName = new ArrayList<>();
        List<String> rawMarkup = new ArrayList<>();
        int ritualCount = 0;

        for (Map.Entry<String, Object> e : materials.entrySet()) {
            Map<String, Object> mat = asMap(e.getValue());
            if (mat == null) {
                continue;
            }
            Map<String, Object> recipe = asMap(mat.get("recipe"));
            if (!"ritual".equals(method(recipe))) {
                continue;
            }
            ritualCount++;
            Object dn = mat.get("display_name");
            if (dn == null || String.valueOf(dn).isBlank()) {
                missingName.add(e.getKey());
                continue;
            }
            // UnifiedRecipeLoader が組み立てるレシピ名と同じ形にする。
            String recipeName = DisplayText.plain(String.valueOf(dn)) + "精製";
            if (LEGACY.matcher(recipeName).find() || MINI_TAG.matcher(recipeName).find()) {
                rawMarkup.add(e.getKey() + " -> " + recipeName);
            }
        }

        assertTrue(ritualCount > 0, "materials.yml に儀式レシピが1件以上あること");
        assertTrue(missingName.isEmpty(),
                "display_name が無いと儀式レシピ名が内部IDになる: " + missingName);
        assertTrue(rawMarkup.isEmpty(),
                "レシピ名に色記号が残っている(GUIに生表示される): " + rawMarkup);
    }

    @Test
    @DisplayName("sourcejars/sourcelinks/threads: display-name が揃い、DisplayText で記号が消える")
    void configuredItemNamesAreClean() {
        record Target(String file, String sectionKey, String nameKey) {
        }
        List<Target> targets = List.of(
                new Target("sourcejars.yml", "jars", "display-name"),
                new Target("sourcelinks.yml", "items", "display-name"),
                new Target("threads.yml", "threads", "display_name"));

        List<String> missing = new ArrayList<>();
        List<String> residue = new ArrayList<>();

        for (Target t : targets) {
            Map<String, Object> sec = section(load(t.file()), t.sectionKey());
            assertFalse(sec.isEmpty(), t.file() + " に " + t.sectionKey() + ": があること");
            for (Map.Entry<String, Object> e : sec.entrySet()) {
                Map<String, Object> def = asMap(e.getValue());
                if (def == null) {
                    continue;
                }
                Object dn = def.get(t.nameKey());
                if (dn == null || String.valueOf(dn).isBlank()) {
                    // display-name が無いと config 側フォールバックで内部IDが表示名になる。
                    missing.add(t.file() + ":" + e.getKey());
                    continue;
                }
                String plain = DisplayText.plain(String.valueOf(dn));
                if (plain.isBlank()) {
                    residue.add(t.file() + ":" + e.getKey() + " -> (空)");
                } else if (LEGACY.matcher(plain).find() || MINI_TAG.matcher(plain).find()) {
                    residue.add(t.file() + ":" + e.getKey() + " -> " + plain);
                }
            }
        }

        assertTrue(missing.isEmpty(), "display-name 未設定は内部IDが表示名になる: " + missing);
        assertTrue(residue.isEmpty(), "DisplayText を通しても色記号が残る: " + residue);
    }

    @Test
    @DisplayName("ritual_effects: name が揃っている(無いと儀式名が内部IDになる)")
    void ritualEffectNamesExist() {
        Map<String, Object> effects = section(load("items.yml"), "ritual_effects");
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Object> e : effects.entrySet()) {
            Map<String, Object> def = asMap(e.getValue());
            if (def == null) {
                continue;
            }
            Object name = def.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                missing.add(e.getKey());
            }
        }
        assertTrue(missing.isEmpty(), "name 未設定の ritual_effect は内部IDが出る: " + missing);
    }
}
