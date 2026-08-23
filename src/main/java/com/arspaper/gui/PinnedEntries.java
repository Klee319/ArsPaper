package com.arspaper.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一覧GUIの「ピン止め（お気に入り）」をプレイヤーごとに保存する
 * （2026-08-23 ユーザー要望「レシピのピン止めをできるようにし、
 * お気に入り登録されたレシピだけに絞り込めるようにしてほしい」）。
 *
 * <p>保存先は<b>プレイヤーの PDC に JSON 配列の文字列</b>。既存の解放済みグリフ
 * （{@code arspaper:unlocked_glyphs}）と同じ形なので、HuskSync のプレイヤーデータ同期に
 * そのまま乗る（サーバをまたいでもピンが消えない）。ファイルや DB を新設しないのはそのため。
 *
 * <p><b>id は画面ごとの安定キーをそのまま使う。</b> レシピ一覧は {@code RecipeEntry#id}、
 * グリフは {@code SpellComponent#getId().toString()}。表示名で保存してはいけない ——
 * yml の表示名は自由記述で、書き換えた瞬間に全員のピンが無言で外れる。
 *
 * <p>Bukkit に触らない部分（符号化・トグル・上限）は static メソッドに分けてある。
 * このフォークのテスト基盤には MockBukkit が無く、{@code Player} を作れないため。
 */
final class PinnedEntries {

    /**
     * 1画面あたりのピン上限。
     *
     * <p>PDC はプレイヤーデータに丸ごと乗って HuskSync が毎回転送するので、
     * 上限なしだと「全部ピン止め」で数百件の文字列が毎ログインで往復する。
     * 128 件は 4 ページ強にあたり、お気に入りとして手で選ぶ量を十分に超えている。
     */
    static final int MAX_PINS = 128;

    private PinnedEntries() {
    }

    /**
     * PDC に入っている JSON 配列を集合へ戻す。
     *
     * <p>null / 空 / 壊れた JSON / 配列以外は<b>すべて空集合</b>に倒す。
     * ここで例外を投げると一覧GUIが開かなくなり、ピン（利便機能）のために
     * レシピが引けなくなるため。挿入順は表示順に効かないが、保存の安定のため保つ。
     */
    static Set<String> decode(String json) {
        Set<String> ids = new LinkedHashSet<>();
        if (json == null || json.isBlank()) {
            return ids;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray()) {
                return ids;
            }
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (element == null || !element.isJsonPrimitive()) continue;
                String id = element.getAsString();
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        } catch (RuntimeException ignored) {
            return new LinkedHashSet<>();
        }
        return ids;
    }

    /** 集合を PDC へ書く JSON 配列にする。 */
    static String encode(Collection<String> ids) {
        JsonArray arr = new JsonArray();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                arr.add(id);
            }
        }
        return arr.toString();
    }

    /**
     * ピンを1件だけ反転した<b>新しい集合</b>を返す（引数は変更しない）。
     *
     * <p>上限に達しているときの追加は<b>黙って無視せず、呼び出し側が
     * {@link #wouldExceedCap} で先に弾く</b>こと。ここでも安全側に倒して追加しない。
     */
    static Set<String> toggled(Set<String> current, String id) {
        Set<String> next = new LinkedHashSet<>(current);
        if (id == null || id.isBlank()) {
            return next;
        }
        if (!next.remove(id) && next.size() < MAX_PINS) {
            next.add(id);
        }
        return next;
    }

    /** 追加すると上限を超えるか（既にピン済みなら解除なので false）。 */
    static boolean wouldExceedCap(Set<String> current, String id) {
        if (id == null || id.isBlank() || current.contains(id)) {
            return false;
        }
        return current.size() >= MAX_PINS;
    }

    // ------------------------------------------------------------------
    // PDC 入出力（Bukkit 依存）
    // ------------------------------------------------------------------

    private static org.bukkit.NamespacedKey key(String prefName) {
        return new org.bukkit.NamespacedKey(com.arspaper.ArsPaper.getInstance(), prefName);
    }

    /** 保存済みのピン。読めなければ空集合（ピンが無い状態と同じ）。 */
    static Set<String> load(Player viewer, String prefName) {
        try {
            return decode(viewer.getPersistentDataContainer()
                .get(key(prefName), PersistentDataType.STRING));
        } catch (RuntimeException ex) {
            return new LinkedHashSet<>();
        }
    }

    /** ピンを保存する。書けなくても画面は動かす（ピンは利便であって機能ではない）。 */
    static void save(Player viewer, String prefName, Set<String> ids) {
        try {
            viewer.getPersistentDataContainer()
                .set(key(prefName), PersistentDataType.STRING, encode(ids));
        } catch (RuntimeException ignored) {
            // 保存できなくても一覧の表示は続ける。
        }
    }
}
