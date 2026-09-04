package com.arspaper.spell;

/**
 * グリフ配置（呪文編集）画面で、パレットのグリフを「今は置けない」とした理由の種別。
 *
 * <p><b>なぜ種別が要るか。</b> 置けない理由は 2 種類に割れる:
 *
 * <ul>
 *   <li><b>そのグリフ固有</b>（{@link #glyphSpecific()} が true）—— 形態と噛み合わない、
 *       直前の効果に対応していない、同じ効果の重複、増強の上限、本のティア超過。
 *       <b>隣のグリフなら置ける</b>ので、どれが駄目なのかを一覧上で見分けられる必要がある。</li>
 *   <li><b>そのタブの全グリフに同時に当てはまる</b>（false）—— まだ形態を選んでいない、
 *       スペルが満杯、先頭スロットが埋まっている、パーク未所持。
 *       これは<b>グリフの選び方の問題ではない</b>ので、一覧の絵を潰しても情報が増えない。</li>
 * </ul>
 *
 * <p>前者だけを灰色の染料（{@link GlyphIcons#INCOMPATIBLE_ICON}）へ潰す。
 * 後者まで潰すと一覧が一色になり、「どれを解放済みか」が絵から消える ——
 * 2026-08-22 に全グリフへ個別アイコンを付けて解放状態が読めなくなったのと同じ壊れ方になる。
 *
 * <p>パーク未所持だけは例外的に「固有の理由」に見えるが false にしてある。
 * 絵は鍵（{@link GlyphIcons#PERK_LOCKED_ICON}）が担当しており、
 * 灰色で上書きすると<b>スキルツリーへ行けという指示が消える</b>ため。
 */
public enum GlyphBlockReason {

    /** スキルパークの使用ゲート未達。絵は鍵が担当するので灰色にはしない。 */
    PERK_MISSING(false),

    /** 構成が満杯。どのグリフも置けないので潰さない。 */
    SPELL_FULL(false),

    /** まだ形態を選んでいない。効果・増強タブの全グリフに当てはまるので潰さない。 */
    NO_FORM(false),

    /** 既に形態がある。形態タブの全グリフに当てはまるので潰さない。 */
    FORM_ALREADY_SET(false),

    /** 形態を置く先頭スロットが埋まっている。形態タブの全グリフに当てはまる。 */
    HEAD_SLOT_TAKEN(false),

    /** この本で扱えるティアを超えている。グリフごとに違うので潰す。 */
    TIER_TOO_HIGH(true),

    /** 同じ効果が既に入っている。 */
    DUPLICATE_EFFECT(true),

    /** 選んでいる形態と噛み合わない効果（シナジー無し）。 */
    FORM_INCOMPATIBLE(true),

    /** 直前の効果／形態に対応していない増強（シナジー無し）。 */
    AUGMENT_INCOMPATIBLE(true),

    /** 増強の重ねがけ上限に達している。 */
    AUGMENT_LIMIT(true);

    private final boolean glyphSpecific;

    GlyphBlockReason(boolean glyphSpecific) {
        this.glyphSpecific = glyphSpecific;
    }

    /** そのグリフ固有の理由なら true（＝一覧で灰色の染料へ潰す対象）。 */
    public boolean glyphSpecific() {
        return glyphSpecific;
    }
}
