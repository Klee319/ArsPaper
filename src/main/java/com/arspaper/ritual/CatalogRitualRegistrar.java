package com.arspaper.ritual;

import com.arspaper.ArsPaper;
import com.trinityforge.stats.ItemTemplate;
import com.trinityforge.stats.RecipeSpec;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Receives TrinityForge catalog templates that declare {@code recipe.method: ritual} and registers
 * them into {@link RitualRecipeRegistry}. Result id prefix {@code tfcatalog:} is resolved by
 * {@link com.arspaper.ritual.RitualManager} via {@link com.arspaper.integration.TrinityForgeBridge}.
 */
public final class CatalogRitualRegistrar {

    public static final String RESULT_PREFIX = "tfcatalog:";

    private CatalogRitualRegistrar() {
    }

    /**
     * Called reflectively from TrinityForge {@code CatalogRitualBridge}.
     *
     * @param tfPlugin unused (logger via ArsPaper)
     * @param templates map of catalog id → ItemTemplate
     */
    @SuppressWarnings("unused")
    public static void registerTrinityForgeCatalog(Plugin tfPlugin, Map<String, ItemTemplate> templates) {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || templates == null || templates.isEmpty()) {
            return;
        }
        RitualRecipeRegistry registry = ars.getRitualRecipeRegistry();
        if (registry == null) {
            return;
        }

        // Merge onto existing recipes (do not clear Ars-native recipes).
        List<RitualRecipe> merged = new ArrayList<>(registry.getAll());
        // Drop previous TF catalog ritual entries (idempotent reload).
        merged.removeIf(r -> r.id() != null && r.id().startsWith("tf_catalog_"));

        int added = 0;
        for (Map.Entry<String, ItemTemplate> e : templates.entrySet()) {
            ItemTemplate template = e.getValue();
            if (template == null) {
                continue;
            }
            // register フラグはエディタから廃止済み。recipe が ritual なら常に登録する
            // (登録したくない Valhalla 由来は catalog に recipe 自体を置かない)。
            // 1テンプレートが複数の ritual を持てる (例: 単発 + まとめ生産 x3)。
            int ritualIndex = 0;
            for (RecipeSpec spec : template.recipes()) {
                if (!spec.isRitual()) {
                    continue;
                }
                ritualIndex++;
                String recipeId = ritualIndex == 1
                        ? "tf_catalog_" + e.getKey()
                        : "tf_catalog_" + e.getKey() + "_" + ritualIndex;
                RitualIngredient core = parseIngredient(spec.coreItem());
                List<RitualIngredient> pedestals = new ArrayList<>();
                for (String raw : spec.pedestalItems()) {
                    int count = parseIngredientCount(raw);
                    RitualIngredient ing = parseIngredient(raw);
                    if (ing != null) {
                        for (int i = 0; i < count; i++) {
                            pedestals.add(ing);
                        }
                    }
                }
                String display = template.displayName() != null ? template.displayName() : e.getKey();
                // Strip MiniMessage tags roughly for ritual announce name.
                display = display.replaceAll("<[^>]+>", "");
                RitualRecipe ritual = new RitualRecipe(
                        recipeId,
                        display,
                        core,
                        pedestals,
                        spec.source(),
                        RESULT_PREFIX + e.getKey(),
                        null,
                        "craft",
                        Map.of("trinityforge-catalog-id", e.getKey()),
                        spec.amount());
                merged.removeIf(r -> recipeId.equals(r.id()));
                merged.add(ritual);
                added++;
            }
        }
        registry.registerRecipes(merged);
        ars.getLogger().info("[CatalogRitualRegistrar] registered " + added
                + " TrinityForge catalog ritual recipe(s)");
    }

    private static RitualIngredient parseIngredient(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = stripCount(raw);
        if (token.startsWith("custom:")) {
            return RitualIngredient.ofCustom(token.substring("custom:".length()));
        }
        Material mat = Material.matchMaterial(token);
        if (mat != null) {
            return RitualIngredient.ofMaterial(mat);
        }
        // Treat unknown as custom id (catalog / ars custom).
        return RitualIngredient.ofCustom(token);
    }

    /** {@code "NAME xN"} → count N (default 1). Mirrors UnifiedRecipeLoader. */
    private static int parseIngredientCount(String raw) {
        if (raw == null) {
            return 1;
        }
        String s = raw.trim();
        int xIdx = s.toLowerCase(Locale.ROOT).lastIndexOf(" x");
        if (xIdx <= 0) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(s.substring(xIdx + 2).trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String stripCount(String raw) {
        String s = raw.trim();
        int xIdx = s.toLowerCase(Locale.ROOT).lastIndexOf(" x");
        if (xIdx > 0) {
            return s.substring(0, xIdx).trim();
        }
        return s;
    }
}
