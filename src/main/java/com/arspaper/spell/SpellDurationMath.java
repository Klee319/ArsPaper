package com.arspaper.spell;

/**
 * 「延長で伸び、短縮で縮み、下限で止まる」持続時間の算術（2026-08-19 / W-152）。
 *
 * <p><b>なぜ独立させたか。</b> 実サーバ報告「炸裂魔法の炸裂までの時間が短縮とかで変わってない」の
 * 真因は、共通の短縮軸 {@link SpellContext#applyDurationDown()} が<b>2個積んで初めて -1 レベル</b>
 * なのに対し、{@code glyphs.yml} の {@code max-augments.duration_down} が <b>1</b> の呪文が
 * 14 件あり、そこでは短縮が<b>一度も効かない</b>（付けられるのに何も起きない無言死）ことだった。
 *
 * <p>ユーザー判断（2026-08-19）で、短縮に実用があると棚卸しできた6呪文
 * （炸裂・仮想ブロック・水生成・罠術・滑空・浮遊）だけを「短縮1個ごとに固定 tick 縮む」
 * 専用の軸へ移した。その6箇所が同じ式を各自で書くと
 * <ul>
 *   <li><b>二重計上</b>（{@link SpellContext#getDurationLevel()} は短縮の -1 も含むので、
 *       専用の短縮量と一緒に使うと短縮2個目で2回引かれる）と</li>
 *   <li><b>下限の入れ忘れ</b>（信管0＝発射直後に足元で炸裂、仮想ブロックは
 *       {@code runTaskLater} に負値が渡って置いた瞬間に消える）</li>
 * </ul>
 * を各自でやり直すことになる。式を1つにして、ここだけをテストで固定する。
 */
public final class SpellDurationMath {

    private SpellDurationMath() {
    }

    /**
     * 持続 tick を解決する。
     *
     * @param base        基本 tick
     * @param extendLevel 延長ぶんの段数。<b>{@link SpellContext#getExtendOnlyDurationLevel()} を渡すこと</b>
     *                    （素の {@code getDurationLevel()} は短縮の -1 を含むので二重計上になる）
     * @param perExtend   延長1段あたりの加算 tick
     * @param downStacks  積まれた短縮の個数（{@link SpellContext#getDurationDownStacks()}）
     * @param perDown     短縮1個あたりの減算 tick
     * @param min         下限 tick。1 未満を渡しても 1 で止める（0 tick は「効果が無い」ではなく
     *                    「即時に走る」なので、呼び出し側の想定と食い違う）
     */
    public static int resolve(int base, int extendLevel, int perExtend,
                              int downStacks, int perDown, int min) {
        long value = (long) base
                + (long) Math.max(0, extendLevel) * Math.max(0, perExtend)
                - (long) Math.max(0, downStacks) * Math.max(0, perDown);
        long floor = Math.max(1L, min);
        if (value < floor) {
            return (int) floor;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
