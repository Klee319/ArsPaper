package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * バックパック容量とページ送りの純関数。Bukkit を触らないのでフォークのテスト基盤で固定できる。
 */
class BackpackCapacityTest {

    @Test
    @DisplayName("本数 × 1本あたりを上限で切る。2本ハードコードはしない")
    void capacityUsesCountTimesSlotsCappedByMax() {
        assertEquals(27, BackpackGui.capacity(1, 27, 108));
        assertEquals(54, BackpackGui.capacity(2, 27, 108));
        assertEquals(81, BackpackGui.capacity(3, 27, 108));
        assertEquals(108, BackpackGui.capacity(4, 27, 108));
        assertEquals(108, BackpackGui.capacity(8, 27, 108));
        assertEquals(0, BackpackGui.capacity(0, 27, 108));
        assertEquals(9, BackpackGui.capacity(1, 27, 9),
                "max-inventory-slots は 1本あたり枠より小さくてよい");
        assertEquals(9, BackpackGui.capacity(4, 27, 9));
    }

    @Test
    @DisplayName("54を超えたらページ送り。1ページの中身は45枠")
    void paginationStartsAfterFiftyFour() {
        assertFalse(BackpackGui.needsPagination(54));
        assertTrue(BackpackGui.needsPagination(55));
        assertEquals(1, BackpackGui.pageCount(54));
        assertEquals(2, BackpackGui.pageCount(90));
        assertEquals(3, BackpackGui.pageCount(108));
        assertEquals(54, BackpackGui.chestSize(108));
        assertEquals(45, BackpackGui.contentOnPage(108, 0, true));
        assertEquals(45, BackpackGui.contentOnPage(108, 1, true));
        assertEquals(18, BackpackGui.contentOnPage(108, 2, true));
        assertEquals(45, BackpackGui.absoluteIndex(1, 0, true));
    }

    @Test
    @DisplayName("54以下はチェストサイズを9の倍数に丸め、ページ送りしない")
    void smallCapacityUsesExactChestWithoutPagination() {
        assertEquals(27, BackpackGui.chestSize(27));
        assertEquals(36, BackpackGui.chestSize(30));
        assertEquals(54, BackpackGui.chestSize(54));
        assertEquals(27, BackpackGui.contentOnPage(27, 0, false));
        assertTrue(BackpackGui.isLockedSlot(new BackpackHolder(null, 0, 30, false), 30));
        assertFalse(BackpackGui.isLockedSlot(new BackpackHolder(null, 0, 30, false), 0));
        assertTrue(BackpackGui.isLockedSlot(new BackpackHolder(null, 0, 108, true), 45));
    }
}
