package com.arspaper.spell;

import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 召喚馬の鞍が抜き取れないことの回帰テスト —— U14「召喚した馬から鞍が取れる」。
 *
 * <p>{@code SummonSteedEffect} は騎乗を操縦可能にするために実物の {@code SADDLE} を装備させる。
 * tamed な馬はシフト右クリックで装備枠を開けるので、召喚するたびに鞍を抜き取れた（＝複製）。
 * 死亡ドロップは {@code SummonedMobListener#onEntityDeath} が既に潰しており、
 * 残っていた唯一の経路が装備枠GUIだった。
 */
class SummonedSteedSaddleGuardTest {

    @Test
    @DisplayName("召喚モブの装備枠を直接クリックする経路は塞ぐ")
    void clickInsideProtectedInventoryIsBlocked() {
        assertTrue(SummonedMobListener.touchesProtectedInventory(true, true, false),
                "装備枠そのもののクリックが通っている。ここが鞍の抜き取り経路そのもの。");
    }

    @Test
    @DisplayName("プレイヤー側をクリックしても shift/数字キーは上段を書き換えるので塞ぐ")
    void crossInventoryMovesAreBlocked() {
        assertTrue(SummonedMobListener.touchesProtectedInventory(false, true, true),
                "shift クリックや数字キーで上段（装備枠）から鞍を引き抜ける");
    }

    @Test
    @DisplayName("上段が保護対象でないなら何も塞がない")
    void unrelatedInventoriesStayUsable() {
        assertFalse(SummonedMobListener.touchesProtectedInventory(false, false, true),
                "無関係なインベントリまで塞ぐと通常の shift クリックが死ぬ");
        assertFalse(SummonedMobListener.touchesProtectedInventory(false, true, false),
                "装備枠が開いているだけでプレイヤー側の単純クリックまで塞いでいる");
    }

    @Test
    @DisplayName("上下段をまたぐアクションを取りこぼしていない")
    @SuppressWarnings("removal") // HOTBAR_MOVE_AND_READD は削除予告済みだが現行 API では今も飛ぶ
    void crossInventoryActionsAreEnumerated() {
        assertTrue(SummonedMobListener.movesAcrossInventories(InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertTrue(SummonedMobListener.movesAcrossInventories(InventoryAction.HOTBAR_SWAP));
        assertTrue(SummonedMobListener.movesAcrossInventories(InventoryAction.HOTBAR_MOVE_AND_READD));
        assertTrue(SummonedMobListener.movesAcrossInventories(InventoryAction.COLLECT_TO_CURSOR),
                "カーソルに鞍を持った状態のダブルクリックで上段の鞍を吸い出せる");
        assertFalse(SummonedMobListener.movesAcrossInventories(InventoryAction.PICKUP_ALL));
        assertFalse(SummonedMobListener.movesAcrossInventories(InventoryAction.NOTHING));
    }

    @Test
    @DisplayName("開く・クリック・ドラッグの3経路すべてにハンドラがある")
    void allThreeInventoryEntryPointsAreGuarded() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/arspaper/spell/SummonedMobListener.java"));
        assertTrue(source.contains("InventoryOpenEvent"),
                "開く経路を塞いでいない");
        assertTrue(source.contains("InventoryClickEvent"),
                "クリック経路を塞いでいない");
        assertTrue(source.contains("InventoryDragEvent"),
                "ドラッグ経路を塞いでいない");
    }

    @Test
    @DisplayName("召喚馬は実物の鞍を装備し続ける（操縦可能性を壊さない）")
    void steedKeepsRealSaddle() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/arspaper/spell/effect/SummonSteedEffect.java"));
        assertTrue(source.contains("setSaddle(new ItemStack(Material.SADDLE))"),
                "鞍を外すと騎乗しても操縦できず、再騎乗の右クリックも装備枠を開く方に化ける");
    }
}
