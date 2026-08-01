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
 * <h2>スニーク+右クリックの案内は廃止した(2026-07-31 F6 指摘3)</h2>
 * かつては「自分から試した操作だから毎回応答したい」という理由で、右クリック案内に
 * 短い間隔ガード({@code INTERACT_HINT_COOLDOWN_MS = 5秒})だけを持たせていた。
 * しかし<b>スニーク+右クリックは通常操作</b>である ── スレッド枠を持つ
 * 弓(5件)・クロスボウ(5件)・トライデント(5件)・斧(4件)・鍬でスニーク狙撃／スニーク耕作を
 * すると、TF の EXP/会心アクションバーを 5 秒ごとに無限に上書きし続ける。
 * 発見経路は下の「選択時の案内」(30秒 + 同一アイテム1セッション1回)と {@code /ars help} で足りるので、
 * 右クリック側の案内 API ごと削除した(残しておくと復活させたくなる)。
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

    /**
     * 「1セッション1回」の記憶をプレイヤー1人につきいくつまで持つか。
     * インベントリを漁るだけで無制限に増える種類のキーなので上限を切る
     * (溢れたら古い順に忘れる ＝ 最悪もう一度案内が出るだけ)。
     */
    public static final int MAX_REMEMBERED_ITEMS_PER_PLAYER = 256;

    /** material も CMD も読めなかったときのキー。null をキーにしないためのプレースホルダ。 */
    private static final String UNKNOWN_ITEM_KEY = "?";

    private final Map<UUID, Long> lastSelectHintAt = new ConcurrentHashMap<>();
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
     * この品を<b>「もう解決しなくてよい」と記録する</b>(負のキャッシュ。2026-07-31 F6 指摘5)。
     *
     * <p>間隔({@link #lastSelectHintAt})は<b>更新しない</b> ── 案内を出していないのに
     * 他の品の案内まで30秒黙らせてしまうため。記録するのはアイテムキーだけ。
     *
     * <p><b>なぜ必要か</b>: {@link #allowSelectHint} はスレッド枠が正のときにしか呼ばれないので、
     * 枠を1つも持たないプレイヤーでは {@link #lastSelectHintAt} が一度も書かれず
     * {@link #isSelectHintSuppressed} が永久に {@code false} を返す。結果として
     * <b>ホットバー操作・F入替・シフトクリックのたびに TF item-stats のフル解決が走り続ける</b>
     * (「高い解決の前に安く足切りする」という設計の意図が、最も多いケースで働かない)。
     * ここで評価済みとして覚えると、同一 material#CMD の解決は<b>1セッション1回</b>に収まる。
     */
    public void markSelectHintEvaluated(UUID playerId, String itemKey) {
        if (playerId == null) {
            return;
        }
        remember(playerId, normalizeKey(itemKey));
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
        remember(playerId, normalizeKey(itemKey));
        lastSelectHintAt.put(playerId, nowMs);
        return true;
    }

    /** 「この品はもう解決/案内しない」集合へ入れる(上限つき・溢れたら古い順に忘れる)。 */
    private void remember(UUID playerId, String key) {
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
    }

    /** 退出時に状態を捨てる(常駐マップにオフラインプレイヤーを溜めない)。 */
    public void forget(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastSelectHintAt.remove(playerId);
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
