package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ThreadGui} 表示中に対象装備を動かせないことの判定固定 —— 2026-07-31 F3 指摘5。
 *
 * <p>{@link GuiListener} は ThreadGui に限りプレイヤーインベントリ側のクリックを意図的に通す
 * (カーソルにスレッドを載せるため)。しかし {@code /ars thread} の対象は<b>ホットバーのスタック</b>で、
 * そのスロットは開いている GUI の下段に描画されていてクリックできる。対象を拾ってカーソルへ
 * 載せたまま空き枠を押すと、在庫のスレッドが 1 個消費される一方で装備には書き込まれない。
 *
 * <p>ドロップ({@code Q})・オフハンド入れ替え({@code F})・数字キーはいずれも
 * 「そのスロットをクリックした」形で来るので、スロット一致だけで塞げる。
 */
class ThreadTargetSlotGuardTest {

    private static final int TARGET_SLOT = 3;
    private static final int NOT_APPLICABLE = -1;

    @Test
    @DisplayName("対象スロット自体のクリックは通さない(拾う/捨てる/Fキー入替を全部含む)")
    void clickOnTargetSlotIsBlocked() {
        assertTrue(GuiListener.touchesTargetSlot(TARGET_SLOT, TARGET_SLOT, NOT_APPLICABLE),
                "対象スロットのクリックが通っている。ここを通すと『スレッドだけ溶ける』が成立する。");
    }

    @Test
    @DisplayName("数字キーの交換先が対象スロットなら通さない")
    void hotbarSwapIntoTargetSlotIsBlocked() {
        assertTrue(GuiListener.touchesTargetSlot(TARGET_SLOT, 20, TARGET_SLOT),
                "別スロットを触りながら数字キーで対象スロットと交換する経路が通っている");
    }

    @Test
    @DisplayName("対象以外のスロットは通す(スレッドをカーソルへ載せる操作を殺さない)")
    void otherSlotsStayClickable() {
        assertFalse(GuiListener.touchesTargetSlot(TARGET_SLOT, 20, NOT_APPLICABLE),
                "対象以外まで塞ぐと、カーソルにスレッドを載せる本来の操作ができなくなる");
        assertFalse(GuiListener.touchesTargetSlot(TARGET_SLOT, 0, NOT_APPLICABLE));
    }

    @Test
    @DisplayName("対象スロットが不明(レガシー経路)なら何も塞がない")
    void unknownTargetSlotBlocksNothing() {
        assertFalse(GuiListener.touchesTargetSlot(ThreadGui.UNKNOWN_TARGET_SLOT, 0, NOT_APPLICABLE),
                "対象スロットを渡していない経路でスロット0が固定的に塞がれている");
        assertFalse(GuiListener.touchesTargetSlot(ThreadGui.UNKNOWN_TARGET_SLOT,
                        NOT_APPLICABLE, NOT_APPLICABLE),
                "getSlot() が -1(ウィンドウ外クリック)のときに『一致』へ倒れている");
    }
}
