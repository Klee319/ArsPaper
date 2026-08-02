package com.arspaper.item;

import com.arspaper.integration.TrinityForgeBridge;

/**
 * 防具の {@link ItemKeys#THREAD_SLOT_ROLLS} 配列1要素ぶんの符号化/復号（2026-08-03）。
 *
 * <p><b>なぜこの形式か</b>: 装着済みスレッド1個ごとの個体差（TFの rollSeed + quality）を
 * {@link ItemKeys#THREAD_SLOTS} と<b>同じ添字</b>で保持する必要がある。旧実装は
 * 旧専用型（レア度 + ステ値そのものを焼き込む形式）を格納していたが、
 * TF側が「rollSeed + quality から都度導出する」設計（{@link TrinityForgeBridge#resolveThreadStats}）
 * に統一されたため、格納する値も rollSeed/quality の2値だけで足りる。
 *
 * <p><b>キー({@link ItemKeys#THREAD_SLOT_ROLLS})は変更しない</b> ── 新キーを増やすと旧キーが
 * 永久にゴミとして残る。格納する文字列の中身だけを {@code "<rollSeed>:<quality>"} へ変える。
 *
 * <p><b>後方互換(移行挙動)</b>: 旧形式（{@code "<rarityId>|main=値|sub=値;..."}）が入っている
 * 既存装備は、{@code ":"} 区切りで long/int としてパースできないため
 * {@link #decode(String)} が必ず {@link #NONE}（rollSeed=0, quality=0）を返す。
 * 例外は投げない（fail-open） ── 個体差が「全部同じ値」に戻るだけで、ステ自体が消えたり
 * 装備の読み込みが失敗したりはしない。
 */
public record ThreadSlotIdentity(long rollSeed, int quality) {

    /** 未厳選/パース失敗時の既定値（rollSeed=0, quality=0 = 従来どおりの幅、個体差なし）。 */
    public static final ThreadSlotIdentity NONE = new ThreadSlotIdentity(0L, 0);

    private static final String SEPARATOR = ":";

    /** 空スロット/未厳選は空文字列（{@link ItemKeys#THREAD_SLOT_ROLLS} の既存規約を維持）。 */
    public String encode() {
        return rollSeed + SEPARATOR + quality;
    }

    /** 壊れている/旧形式/空文字は例外を投げず {@link #NONE} を返す（fail-open）。 */
    public static ThreadSlotIdentity decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        int at = raw.indexOf(SEPARATOR);
        if (at <= 0 || at == raw.length() - 1) {
            return NONE;
        }
        try {
            long rollSeed = Long.parseLong(raw.substring(0, at).trim());
            int quality = Integer.parseInt(raw.substring(at + 1).trim());
            return new ThreadSlotIdentity(rollSeed, quality);
        } catch (NumberFormatException malformed) {
            return NONE;
        }
    }

    public static ThreadSlotIdentity of(TrinityForgeBridge.ThreadIdentity identity) {
        return identity == null ? NONE : new ThreadSlotIdentity(identity.rollSeed(), identity.quality());
    }
}
