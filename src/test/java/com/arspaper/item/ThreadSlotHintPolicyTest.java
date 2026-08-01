package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「スレッド枠を持つ装備を持っているのに機能へ辿り着けない」を防ぐ案内のスパム防止
 * ({@link ThreadSlotHintPolicy})の判定固定 —— 2026-07-31 F3 指摘1。
 *
 * <p>案内を右クリックから独立させた代償として「ホイールを回すだけで喋る」危険が出る。
 * ここでは<b>1回目は出る / 直後の別アイテムは出ない / 同じアイテムは間隔を空けても出ない</b>
 * という三点を固定する(どれかが崩れると、うるさすぎて消されるか、静かすぎて辿れない)。
 */
class ThreadSlotHintPolicyTest {

    private static final long T0 = 1_000_000L;
    private static final String SWORD = ThreadSlotHintPolicy.itemKey("NETHERITE_SWORD", 400101);
    private static final String WAND = ThreadSlotHintPolicy.itemKey("BLAZE_ROD", 400002);

    @Test
    @DisplayName("初回の選択では案内が出る")
    void firstSelectionHints() {
        ThreadSlotHintPolicy policy = new ThreadSlotHintPolicy();
        UUID player = UUID.randomUUID();

        assertFalse(policy.isSelectHintSuppressed(player, SWORD, T0),
                "初回は抑止されないこと(抑止されると機能へ辿り着けない)");
        assertTrue(policy.allowSelectHint(player, SWORD, T0), "初回の案内が出ていない");
    }

    @Test
    @DisplayName("同じアイテムは間隔を空けても1セッション1回しか案内しない")
    void sameItemHintsOncePerSession() {
        ThreadSlotHintPolicy policy = new ThreadSlotHintPolicy();
        UUID player = UUID.randomUUID();

        assertTrue(policy.allowSelectHint(player, SWORD, T0));
        long wellPastCooldown = T0 + ThreadSlotHintPolicy.SELECT_HINT_COOLDOWN_MS * 10;
        assertTrue(policy.isSelectHintSuppressed(player, SWORD, wellPastCooldown),
                "同一アイテムは間隔を過ぎても抑止され続けること(枠の存在は一度知れば十分)");
        assertFalse(policy.allowSelectHint(player, SWORD, wellPastCooldown),
                "同じ剣を選び直すたびに案内が出ている");
    }

    @Test
    @DisplayName("別のアイテムでも間隔内なら案内しない(ホイール1周で連続して喋らせない)")
    void differentItemStillRespectsTheInterval() {
        ThreadSlotHintPolicy policy = new ThreadSlotHintPolicy();
        UUID player = UUID.randomUUID();

        assertTrue(policy.allowSelectHint(player, SWORD, T0));
        assertFalse(policy.allowSelectHint(player, WAND, T0 + 1_000L),
                "間隔内の別アイテムでも案内が出ている(ホットバー9枠を回すと9回喋る)");
        assertTrue(policy.allowSelectHint(player, WAND,
                        T0 + ThreadSlotHintPolicy.SELECT_HINT_COOLDOWN_MS),
                "間隔を過ぎた別アイテムでは案内が出ること");
    }

    @Test
    @DisplayName("プレイヤーごとに独立している")
    void stateIsPerPlayer() {
        ThreadSlotHintPolicy policy = new ThreadSlotHintPolicy();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(policy.allowSelectHint(a, SWORD, T0));
        assertTrue(policy.allowSelectHint(b, SWORD, T0),
                "他人が案内を受けたせいで自分の案内が消えている");
    }

    @Test
    @DisplayName("退出で状態を捨てる(再ログインでまた案内できる/マップに溜めない)")
    void forgetClearsState() {
        ThreadSlotHintPolicy policy = new ThreadSlotHintPolicy();
        UUID player = UUID.randomUUID();

        assertTrue(policy.allowSelectHint(player, SWORD, T0));
        policy.forget(player);
        assertTrue(policy.allowSelectHint(player, SWORD, T0),
                "forget 後も抑止が残っている(常駐マップにオフラインプレイヤーが溜まる)");
    }

    @Test
    @DisplayName("右クリック案内のAPIは廃止済み(復活させると通常操作でアクションバーを潰す・F6 指摘3)")
    void interactHintApiIsGone() {
        // スニーク+右クリックは弓/クロスボウ/トライデント/斧/鍬の通常操作なので、
        // 5秒間隔ガードでは TF の EXP/会心アクションバーを無限に上書きし続けていた。
        // 「間隔を延ばす」ではなく API ごと落としてある(残すと復活させたくなる)。
        assertTrue(Arrays.stream(ThreadSlotHintPolicy.class.getMethods())
                        .noneMatch(m -> m.getName().toLowerCase().contains("interact")),
                "interact 系の案内APIが復活している: "
                        + Arrays.stream(ThreadSlotHintPolicy.class.getMethods())
                                .map(java.lang.reflect.Method::getName).toList());
        assertTrue(Arrays.stream(ThreadSlotHintPolicy.class.getFields())
                        .noneMatch(f -> f.getName().contains("INTERACT")),
                "INTERACT 系の定数が復活している");
    }

    @Test
    @DisplayName("枠を持たない品は1セッション1回しか解決しない(F6 指摘5の負のキャッシュ)")
    void zeroSlotItemsAreResolvedOnlyOncePerSession() {
        ThreadSlotHintPolicy policy = new ThreadSlotHintPolicy();
        UUID player = UUID.randomUUID();
        String plainHoe = ThreadSlotHintPolicy.itemKey("WOODEN_HOE", null);

        // 1回目: 抑止されていない = 呼び出し側が枠数を解決する(高い処理)。
        assertFalse(policy.isSelectHintSuppressed(player, plainHoe, T0));
        // 枠が0だったので「評価済み」として記録する。
        policy.markSelectHintEvaluated(player, plainHoe);

        // 2回目以降は間隔に関係なく抑止される = フル解決が二度と走らない。
        assertTrue(policy.isSelectHintSuppressed(player, plainHoe, T0 + 1L),
                "同一tickの再評価が抑止されていない");
        assertTrue(policy.isSelectHintSuppressed(player, plainHoe,
                        T0 + ThreadSlotHintPolicy.SELECT_HINT_COOLDOWN_MS * 100),
                "間隔を過ぎたら再びフル解決が走る形に戻っている"
                        + "(スレッド枠を持たないプレイヤーのホットバー操作ごとに解決が走る)");
    }

    @Test
    @DisplayName("負のキャッシュは他の品の案内を黙らせない(間隔を更新しない)")
    void markingEvaluatedDoesNotSilenceOtherItems() {
        ThreadSlotHintPolicy policy = new ThreadSlotHintPolicy();
        UUID player = UUID.randomUUID();

        policy.markSelectHintEvaluated(player, ThreadSlotHintPolicy.itemKey("WOODEN_HOE", null));
        assertTrue(policy.allowSelectHint(player, SWORD, T0),
                "枠0の品を評価しただけで、枠のある剣の案内まで抑止されている"
                        + "(markSelectHintEvaluated が間隔を更新してしまっている)");
    }

    @Test
    @DisplayName("アイテムキーは material だけでなく CustomModelData でも分かれる")
    void itemKeySeparatesCustomModelData() {
        assertNotEquals(ThreadSlotHintPolicy.itemKey("NETHERITE_SWORD", 400101),
                ThreadSlotHintPolicy.itemKey("NETHERITE_SWORD", 400102),
                "CMD が違う品が同一扱いされている。TF カタログの剣31本が同一 material に"
                        + "相乗りしているので、material だけでは31本まとめて1回になる。");
        assertNotEquals(ThreadSlotHintPolicy.itemKey("NETHERITE_SWORD", null),
                ThreadSlotHintPolicy.itemKey("NETHERITE_SWORD", 0),
                "CMD 未設定と CMD 0 が同一扱いされている");
    }

    @Test
    @DisplayName("記憶数に上限があり、溢れても抑止が壊れない")
    void rememberedItemsAreBounded() {
        ThreadSlotHintPolicy policy = new ThreadSlotHintPolicy();
        UUID player = UUID.randomUUID();
        long now = T0;

        for (int i = 0; i <= ThreadSlotHintPolicy.MAX_REMEMBERED_ITEMS_PER_PLAYER + 5; i++) {
            now += ThreadSlotHintPolicy.SELECT_HINT_COOLDOWN_MS;
            assertTrue(policy.allowSelectHint(player,
                            ThreadSlotHintPolicy.itemKey("NETHERITE_SWORD", i), now),
                    "毎回違うアイテムなのに案内が止まった(添字 " + i + ")");
        }
        // 直近のキーは覚えているので、上限を超えても「同一アイテム1回」は効いている。
        String latest = ThreadSlotHintPolicy.itemKey("NETHERITE_SWORD",
                ThreadSlotHintPolicy.MAX_REMEMBERED_ITEMS_PER_PLAYER + 5);
        assertTrue(policy.isSelectHintSuppressed(player, latest,
                        now + ThreadSlotHintPolicy.SELECT_HINT_COOLDOWN_MS * 2),
                "上限を超えたあと直近のアイテムまで忘れている");
    }
}
