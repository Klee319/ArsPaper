package com.arspaper.spell;

import com.google.gson.*;

import java.util.ArrayList;
import java.util.List;

/**
 * SpellRecipeをJSON文字列にシリアライズ/デシリアライズする。
 * PDCのSTRING型で保存する。
 */
public final class SpellSerializer {

    private static final Gson GSON = new Gson();

    private SpellSerializer() {}

    /**
     * SpellRecipe → JSON文字列。
     * 形式: {"name":"Fire Bolt","components":["arspaper:projectile","arspaper:harm","arspaper:amplify"]}
     */
    public static String serialize(SpellRecipe recipe) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", recipe.getName());
        JsonArray arr = new JsonArray();
        for (SpellComponent comp : recipe.getComponents()) {
            arr.add(comp.getId().toString());
        }
        obj.add("components", arr);
        return GSON.toJson(obj);
    }

    /**
     * JSON文字列 → SpellRecipe。
     * 不明なグリフIDはスキップされる。
     * 不正なJSONの場合はnullを返す。
     */
    public static SpellRecipe deserialize(String json, SpellRegistry registry) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String name = obj.get("name").getAsString();
            JsonArray arr = obj.getAsJsonArray("components");
            List<SpellComponent> components = new ArrayList<>();
            for (JsonElement el : arr) {
                SpellComponent comp = registry.get(el.getAsString());
                if (comp != null) {
                    components.add(comp);
                }
            }
            return new SpellRecipe(name, components);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 複数スロットのスペルをJSON配列としてシリアライズ。
     */
    public static String serializeSlots(List<SpellRecipe> slots) {
        JsonArray arr = new JsonArray();
        for (SpellRecipe recipe : slots) {
            if (recipe != null) {
                arr.add(JsonParser.parseString(serialize(recipe)));
            } else {
                arr.add(JsonNull.INSTANCE);
            }
        }
        return GSON.toJson(arr);
    }

    /**
     * JSON配列 → 複数スロットのスペルリスト。
     */
    public static List<SpellRecipe> deserializeSlots(String json, SpellRegistry registry) {
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        List<SpellRecipe> slots = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonNull()) {
                slots.add(null);
            } else {
                slots.add(deserialize(GSON.toJson(el), registry));
            }
        }
        return slots;
    }
}
