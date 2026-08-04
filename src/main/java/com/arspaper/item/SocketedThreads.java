package com.arspaper.item;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 装備PDCに刻まれた「装着済みスレッド」の唯一の読み出し口。
 *
 * <p>{@link ItemKeys#THREAD_SLOTS}(種類のJSON配列)と {@link ItemKeys#THREAD_SLOT_ROLLS}
 * (厳選のJSON配列)を<b>同じ添字</b>で突き合わせ、(スロット番号, 種類, rollSeed, quality) を返す。
 * 厳選配列が短い/欠落している添字は {@link ThreadSlotIdentity#NONE}
 * (rollSeed=0, quality=0 = 個体差なしの従来どおりの幅)にフォールバックするので、
 * 厳選導入前の装備・旧単一スレッド形式({@link ItemKeys#THREAD_TYPE})のどちらもこの経路で扱える。
 *
 * <p><b>なぜ共通化したか(2026-08-04)</b>: ステを実際に適用する
 * {@link ArmorManaListener} と、その内訳をプレイヤーへ見せる
 * {@link ThreadStatChatListener} が別々にPDCを読むと、
 * <b>「表示されているスレッドと効いているスレッドが違う」</b>という、このコードベースが繰り返し
 * 踏んできた表示と実装の食い違いがそのまま再生産される。特に {@code effectiveSlots} による
 * 上限打ち切り(拡張枠perkを失うと5枠目以降は効かないがPDCには残る)は片方だけ実装すると
 * 気づけない。読みは1箇所に閉じる。
 */
public final class SocketedThreads {

    private static final Gson GSON = new Gson();

    /** 装着スレッド1個ぶん: PDC上のスロット番号(0始まり) + 種類 + そのスロットの厳選(rollSeed/quality)。 */
    public record Entry(int slotIndex, ThreadType type, long rollSeed, int quality) {
    }

    private SocketedThreads() {
    }

    /**
     * 効果を持つ装着済みスレッドを、PDC格納順(=GUIのスロット順)で返す。
     *
     * @param pdc            装備アイテムのPDC(防具4部位／メインハンド／オフハンドいずれも)
     * @param effectiveSlots 装着者に対する実効スレッド枠上限(基本枠+拡張枠perk保有時のみ拡張分)。
     *                       先頭からこの枠数までのみ対象とし、上限超過分は無視する
     *                       (PDCデータ自体は保持したまま、適用と表示の両方から除外する)。
     */
    public static List<Entry> read(PersistentDataContainer pdc, int effectiveSlots) {
        List<Entry> result = new ArrayList<>();
        if (pdc == null || effectiveSlots <= 0) {
            return result;
        }

        String threadSlotsJson = pdc.get(ItemKeys.THREAD_SLOTS, PersistentDataType.STRING);
        if (threadSlotsJson == null) {
            // 旧単一スレッド形式(THREAD_SLOTS導入以前)にはロール配列が無い ──
            // この経路が生きている装備自体、個体差の概念がまだ無かった時代のものなので0/0で扱う。
            String oldThreadId = pdc.get(ItemKeys.THREAD_TYPE, PersistentDataType.STRING);
            if (oldThreadId != null) {
                ThreadType thread = ThreadType.fromId(oldThreadId);
                if (thread != null && thread.hasEffect()) {
                    result.add(new Entry(0, thread, 0L, 0));
                }
            }
            return result;
        }

        try {
            List<String> slots = GSON.fromJson(threadSlotsJson, new TypeToken<List<String>>(){}.getType());
            if (slots == null) {
                return result;
            }
            List<String> rolls = readRolls(pdc);
            int cappedSize = Math.min(slots.size(), effectiveSlots);
            for (int i = 0; i < cappedSize; i++) {
                String threadId = slots.get(i);
                if (threadId == null) {
                    continue;
                }
                ThreadType thread = ThreadType.fromId(threadId);
                if (thread == null || !thread.hasEffect()) {
                    continue;
                }
                ThreadSlotIdentity identity = i < rolls.size()
                        ? ThreadSlotIdentity.decode(rolls.get(i)) : ThreadSlotIdentity.NONE;
                result.add(new Entry(i, thread, identity.rollSeed(), identity.quality()));
            }
        } catch (Exception malformed) {
            // 壊れたJSONは「装着スレッド無し」として扱う(fail-open)。装備自体は使える。
        }
        return result;
    }

    /**
     * 装着スレッド1件の要約行 {@code ・<スレッド名>【品質】}。
     *
     * <p>装備の lore({@code ThreadGui#buildThreadLore})とチャット出力
     * ({@link ThreadStatChatListener})の<b>共通の1行</b>。品質表記は
     * {@link com.arspaper.integration.TrinityForgeBridge#qualityTierLabel}
     * (= TF の {@code stats/quality-tiers.yml})が唯一の供給元で、ここで「品質3」等と
     * 数値化しないこと(TF 装備の品質行と食い違う)。ティアが解決できない場合は名前だけ。
     */
    public static net.kyori.adventure.text.Component summaryLine(ThreadType type, int quality) {
        net.kyori.adventure.text.Component line =
                net.kyori.adventure.text.Component.text("・" + type.getDisplayName(), type.getColor());
        net.kyori.adventure.text.Component tier =
                com.arspaper.integration.TrinityForgeBridge.qualityTierLabel(quality).orElse(null);
        if (tier != null) {
            line = line.append(tier);
        }
        return line.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /** {@link ItemKeys#THREAD_SLOT_ROLLS} の生JSON配列を読む。壊れている/未設定なら空リスト。 */
    private static List<String> readRolls(PersistentDataContainer pdc) {
        String json = pdc.get(ItemKeys.THREAD_SLOT_ROLLS, PersistentDataType.STRING);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> rolls = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
            return rolls != null ? rolls : List.of();
        } catch (Exception malformed) {
            return List.of();
        }
    }
}
