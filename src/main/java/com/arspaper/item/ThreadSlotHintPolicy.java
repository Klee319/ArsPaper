package com.arspaper.item;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「スレッド枠を持つ装備を持っているのに機能へ辿り着けない」を防ぐ案内の送出判定。
 *
 * <h2>なぜ右クリックだけでは足りないのか(2026-07-31 F3 指摘1)</h2>
 * 手持ち装備の案内はもともと「スニーク+右クリック」({@link ThreadGuiOpenListener})だけで出していたが、
 * <b>スペルをバインドした杖・武器では 1 度も出なかった</b>。
 * {@code SpellBindListener}(NORMAL 優先度)は {@code bookUuid}/{@code spellSlot} を持つアイテムの
 * 右クリックを<b>スニーク判定より前に無条件で {@code setCancelled(true)}</b> するため、
 * 案内側が「キャンセル済み(=呪文が出た)なら黙る」条件を持っていると永久に沈黙する。
 * 杖はバインドして使うものなので、これがユーザーが報告した触媒の<b>最も普通の状態</b>だった。
 *
 * <p>そこで案内を右クリックイベントに依存させず、<b>スレッド枠を持つ装備をメインハンドに
 * 選択した時点</b>(持ち替え/オフハンド入れ替え/ホットバースワップ)でも出す。
 * 代わりに以下の二重ガードでスパムを止める ── ホイールを1周回すだけで9回喋るのは事故に見える。
 * <ol>
 *   <li><b>間隔</b>: プレイヤーごとに {@link #SELECT_HINT_COOLDOWN_MS} 以内は再送しない。</li>
 *   <li><b>同一アイテム 1 セッション 1 回</b>: material + CustomModelData のキーで既に案内した品は
 *       ログイン中もう案内しない(枠の存在は一度知れば十分)。</li>
 * </ol>
 *
 * <p>スニーク+右クリックの案内は<b>自分から試した操作</b>なので「1セッション1回」の対象にしない
 * (知りたくて何度も試した人に無反応を返すのが一番悪い)。短い間隔ガードだけを持つ。
 *
 * <p>Bukkit ランタイムを必要としない純粋な状態機械だけを置く(このフォークのテスト基盤は
 * MockBukkit を持たないため)。時刻は呼び出し側から渡す。
 */
public final class ThreadSlotHintPolicy {

    /**
     * 装備を選択(持ち替え)したときの案内の最小間隔(ms)。
     * ホットバーを1周スクロールしても1回しか喋らない程度に長く取る。
     */
    public static final long SELECT_HINT_COOLDOWN_MS = 30_000L;

    /** スニーク+右クリックの案内の最小間隔(ms)。自分から試した操作なので短くてよい。 */
    public static final long INTERACT_HINT_COOLDOWN_MS = 5_000L;

    /**
     * 「1セッション1回」の記憶をプレイヤー1人につきいくつまで持つか。
     * インベントリを漁るだけで無制限に増える種類のキーなので上限を切る
     * (溢れたら古い順に忘れる ＝ 最悪もう一度案内が出るだけ)。
     */
    public static final int MAX_REMEMBERED_ITEMS_PER_PLAYER = 256;

    /** material も CMD も読めなかったときのキー。null をキーにしないためのプレースホルダ。 */
    private static final String UNKNOWN_ITEM_KEY = "?";

    private final Map<UUID, Long> lastSelectHintAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractHintAt = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> selectHintedItems = new ConcurrentHashMap<>();

    /**
     * <b>状態を変えずに</b>「今この品の案内は抑止されるか」を返す。
     * 呼び出し側は枠数の解決(TF item-stats のフル解決)より前にこれで足切りできる ──
     * ホットバーを往復するだけで毎回フル解決が走るのを避けるため。
     * 抑止されていないことを確認したうえで {@link #allowSelectHint} を呼ぶ。
     */
    public boolean isSelectHintSuppressed(UUID playerId, String itemKey, long nowMs) {
        if (playerId == null) {
            return true;
        }
        Set<String> seen = selectHintedItems.get(playerId);
        if (seen != null && seen.contains(normalizeKey(itemKey))) {
            return true;
        }
        Long previous = lastSelectHintAt.get(playerId);
        return previous != null && nowMs - previous < SELECT_HINT_COOLDOWN_MS;
    }

    /**
     * 装備を選択したときの案内を出してよいか。出してよい場合は送出済みとして記録する。
     *
     * @param playerId プレイヤー
     * @param itemKey  {@link #itemKey(String, Integer)} で作ったアイテム識別キー
     * @param nowMs    現在時刻(ms)
     */
    public boolean allowSelectHint(UUID playerId, String itemKey, long nowMs) {
        if (isSelectHintSuppressed(playerId, itemKey, nowMs)) {
            return false;
        }
        String key = normalizeKey(itemKey);
        Set<String> seen = selectHintedItems.computeIfAbsent(playerId,
                id -> Collections.synchronizedSet(new LinkedHashSet<>()));
        synchronized (seen) {
            if (seen.size() >= MAX_REMEMBERED_ITEMS_PER_PLAYER) {
                var iterator = seen.iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            seen.add(key);
        }
        lastSelectHintAt.put(playerId, nowMs);
        return true;
    }

    /**
     * スニーク+右クリックの案内を出してよいか。出してよい場合は送出済みとして記録する。
     * こちらは「1セッション1回」を課さない(意図した操作なので毎回応答したい)。
     */
    public boolean allowInteractHint(UUID playerId, long nowMs) {
        if (playerId == null) {
            return false;
        }
        Long previous = lastInteractHintAt.get(playerId);
        if (previous != null && nowMs - previous < INTERACT_HINT_COOLDOWN_MS) {
            return false;
        }
        lastInteractHintAt.put(playerId, nowMs);
        return true;
    }

    /** 退出時に状態を捨てる(常駐マップにオフラインプレイヤーを溜めない)。 */
    public void forget(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastSelectHintAt.remove(playerId);
        lastInteractHintAt.remove(playerId);
        selectHintedItems.remove(playerId);
    }

    /**
     * 「同一アイテム」の識別キー。material + CustomModelData で作る
     * ── TF カタログの装備は同一 material に CMD で何十本も相乗りしているため、
     * material だけでは剣31本がまとめて1回扱いになってしまう。
     */
    public static String itemKey(String materialName, Integer customModelData) {
        if (materialName == null) {
            return UNKNOWN_ITEM_KEY;
        }
        return materialName + "#" + (customModelData == null ? "-" : customModelData);
    }

    private static String normalizeKey(String itemKey) {
        return (itemKey == null || itemKey.isBlank()) ? UNKNOWN_ITEM_KEY : itemKey;
    }
}
