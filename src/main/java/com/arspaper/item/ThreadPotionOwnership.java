package com.arspaper.item;

/**
 * 「スレッドが付けたポーション効果を剥がしてよいか」の判定(W-54 / 2026-08-18 第2波)。
 *
 * <p>Bukkit へ一切依存しない純関数にしてあるのは、このフォークのテスト基盤が
 * Bukkit ランタイム/MockBukkit を持たないため。挙動そのものをテストで固定できるようにして、
 * ソース文字列の一致で規約を縛る(実装を差し替えると誤検知する)方式から脱している。
 *
 * <h2>直したバグ</h2>
 * <ol>
 *   <li><b>W-54 第1波</b>: 「無期限かどうか」だけで自分のものだと判定していたため、
 *       {@code /effect give @s luck infinite} のようなプレイヤー起因の無期限効果まで
 *       装備変更のたびに剥がしていた → 付与記録(所有権台帳)を必須にした。</li>
 *   <li><b>W-54 第2波(ユーザー報告「幸運のエフェクトが消える不具合直ってなくない？」)</b>:
 *       台帳の照合が<b>型だけ</b>で amplifier を見ていなかった。スレッドで LUCK Lv1 が付いている状態で
 *       プレイヤーが {@code /effect give @s luck infinite 5} を撃つと、同じ型・無期限のまま
 *       amplifier だけが他ソースの値に上書きされる。その後スレッドを外す(クリエでアイテムを
 *       取り出してメインハンドが入れ替わるだけでも起きる)と、台帳に型の記録があるので
 *       <b>プレイヤーが付けた Lv6 の効果を剥がしていた</b>。amplifier の一致も要求すれば、
 *       上書きされた効果は「自分のものではない」と判定できる。</li>
 * </ol>
 */
public final class ThreadPotionOwnership {

    private ThreadPotionOwnership() {
    }

    /**
     * 除去してよいのは以下を<b>すべて</b>満たす場合だけ。どれか1つでも欠けたら
     * 「自分のものではない/もう自分の効果ではない」として<b>触らない</b>(安全側=消さない方向)。
     *
     * @param recordedAmplifier 所有権台帳に残っている amplifier。{@code null} = 付与記録が無い
     *                          (再起動を跨いだ / プレイヤーや他プラグインが付けた)
     * @param present           対象の型の効果が今もプレイヤーに付いているか
     * @param infinite          その効果が今も無期限か(有限になっていれば他ソースのポーションに上書きされた)
     * @param currentAmplifier  その効果の現在の amplifier
     */
    public static boolean mayRemoveGrantedPotion(Integer recordedAmplifier, boolean present,
                                                  boolean infinite, int currentAmplifier) {
        if (recordedAmplifier == null) {
            return false;
        }
        if (!present || !infinite) {
            return false;
        }
        return currentAmplifier == recordedAmplifier;
    }
}
