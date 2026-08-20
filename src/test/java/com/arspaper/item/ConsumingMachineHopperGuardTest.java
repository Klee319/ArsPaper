package com.arspaper.item;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ホッパー／ドロッパー経由の搬入をどこで止めるか（2026-08-19 / W-132 回帰）。
 *
 * <h2>なぜ必要か（実バグ）</h2>
 * {@code CustomItemListener} は {@code InventoryClickEvent} で かまど系／醸造台への
 * <b>クリック経路だけ</b>を塞いでいて、{@code InventoryMoveItemEvent} の購読は
 * コンポスター行きしか無かった。そのため<b>ホッパーからは素通りで入れられ</b>、
 * ベース材質が精錬可能な圧縮素材（{@code beef_1x} のベースは {@code BEEF}）は
 * 入った先で実際に焼かれて 9 個ぶんが 1 個に化けていた。
 *
 * <p>ここで固定するのは 3 点:
 * <ol>
 *   <li>止める装置の集合が「素材を消費・変換するものだけ」であること
 *       —— チェスト／樽／ホッパー同士まで止めると保管の自動化が死ぬ。</li>
 *   <li>クリック経路とホッパー経路が<b>同じ集合</b>を見ていること
 *       —— 片方だけ足すと、また同じ非対称が生まれる。</li>
 *   <li>最後の一点（{@code BlockCookEvent}）の購読が残っていること。</li>
 * </ol>
 *
 * <p>判定を {@code InventoryType} の定数ではなく名前集合で持っているのは、定数へ触れると
 * {@code MenuType} のレジストリ初期化が走り、サーバの無いこのテスト環境では参照した瞬間に
 * {@code ExceptionInInitializerError} になるため（このフォークに MockBukkit は入っていない）。
 */
class ConsumingMachineHopperGuardTest {

    /**
     * 入れた素材を消費・変換してしまう装置（＝止めるべき搬入先）。
     *
     * <p>2026-08-20 W-172 で石切台／製図台／機織り機を追加した。かまど系と全く同じ穴で、
     * 石切台へ {@code stone_5x}(59049倍圧縮石)を入れると石レンガ1個になり圧縮倍率ごと消える。
     * 製図台は {@code base_material: PAPER} のガチャ券8種({@code gacha_ticket_*})を地図の拡張で食う。
     */
    private static final List<String> MUST_BLOCK =
            List.of("FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING", "COMPOSTER",
                    "STONECUTTER", "CARTOGRAPHY", "LOOM", "BEACON");

    /** ただの保管／中継。ここを止めると自動仕分けが丸ごと死ぬ。 */
    private static final List<String> MUST_ALLOW =
            List.of("CHEST", "BARREL", "HOPPER", "DROPPER", "DISPENSER", "SHULKER_BOX", "ENDER_CHEST");

    /**
     * 消費はするが、専用の {@code Prepare*} ガードが「正当な用途だけ通す」判断をしている装置。
     * ここへ足すとその判断ごと潰れる(カスタム防具のアーマートリムが打てなくなる等)。
     */
    private static final List<String> MUST_STAY_OUT =
            List.of("ANVIL", "GRINDSTONE", "SMITHING", "MERCHANT", "CRAFTING", "WORKBENCH");

    @Test
    void dedicatedlyGuardedStationsStayOut() {
        for (String type : MUST_STAY_OUT) {
            assertFalse(CustomItemListener.CONSUMING_MACHINES.contains(type),
                    type + " は専用の Prepare* ガードの担当。ここへ足すと正当な用途まで塞がる");
        }
    }

    @Test
    void consumingMachinesAreBlocked() {
        for (String type : MUST_BLOCK) {
            assertTrue(CustomItemListener.CONSUMING_MACHINES.contains(type),
                    type + " への搬入を止めていない。ホッパー経由で素材が焼かれる/堆肥化される");
        }
    }

    @Test
    void storageContainersStayOpen() {
        for (String type : MUST_ALLOW) {
            assertFalse(CustomItemListener.CONSUMING_MACHINES.contains(type),
                    type + " まで止めると、カスタム素材の自動仕分け・搬送が全部死ぬ");
        }
    }

    @Test
    void theSetHasNoSurprises() {
        assertEquals(MUST_BLOCK.size(), CustomItemListener.CONSUMING_MACHINES.size(),
                "止める装置を増減させたなら、その意図をこのテストにも書くこと: "
                        + CustomItemListener.CONSUMING_MACHINES);
    }

    /**
     * 各経路のハンドラが全部残っていること。どれか1本でも @EventHandler を失うと、
     * 例外も警告も出ないまま<b>その経路だけ素通り</b>に戻る（今回の実バグそのもの）。
     *
     * <p>搬入を止める {@code onVanillaMachineClick} / {@code onHopperToVanillaMachine} に加えて、
     * 「最後の一点」側（既に入力スロットに居る個体を守る）も本数で固定する:
     * かまどの {@code onBlockCook} と、W-172 で足した石切台／製図台／機織り機の3本。
     */
    @Test
    void allGuardPathsStayWired() throws Exception {
        List<Method> guards = List.of(
                CustomItemListener.class.getMethod("onVanillaMachineClick",
                        org.bukkit.event.inventory.InventoryClickEvent.class),
                CustomItemListener.class.getMethod("onHopperToVanillaMachine",
                        org.bukkit.event.inventory.InventoryMoveItemEvent.class),
                CustomItemListener.class.getMethod("onBlockCook",
                        org.bukkit.event.block.BlockCookEvent.class),
                CustomItemListener.class.getMethod("onStonecutterSelect",
                        io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent.class),
                CustomItemListener.class.getMethod("onCartography",
                        io.papermc.paper.event.player.CartographyItemEvent.class),
                CustomItemListener.class.getMethod("onLoomSelect",
                        io.papermc.paper.event.player.PlayerLoomPatternSelectEvent.class),
                CustomItemListener.class.getMethod("onPiglinPickup",
                        org.bukkit.event.entity.EntityPickupItemEvent.class));
        for (Method m : guards) {
            assertTrue(m.isAnnotationPresent(org.bukkit.event.EventHandler.class),
                    m.getName() + " が @EventHandler を失っている（購読されない＝無言で無効）");
            assertFalse(Modifier.isStatic(m.getModifiers()), m.getName() + " はインスタンスメソッドであること");
        }
    }
}
