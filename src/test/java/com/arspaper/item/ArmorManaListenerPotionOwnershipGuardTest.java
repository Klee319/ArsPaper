package com.arspaper.item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-54(2026-08-18)の回帰ガード: 無期限LUCK等プレイヤー自身/管理コマンドが付けた効果が、
 * スレッドの再計算(装備変更のたびに走る{@code recalculateArmorBonus})で誤って剥がされないこと。
 *
 * <p><b>直っていたバグ</b>: 旧実装の {@code isThreadGranted}(無期限かどうかだけを見る)は
 * 「自分が付けたか」を判定できず、{@code /effect give @s luck infinite} のようなプレイヤー起因の
 * 無期限効果まで「スレッドが付けたもの」と誤認して {@code removePotionEffect} していた。
 * 修正は「実際に自分(スレッド)がそのタイプへ付与したという記録」をプレイヤーごとの所有権台帳
 * ({@code threadGrantedPotions})へ持ち、除去は「台帳に記録があり、かつ現在も無期限のまま」の
 * 場合だけに限定する。
 *
 * <p>このフォークのテスト基盤は Bukkit ランタイム/MockBukkit を持たないため、
 * {@link ArmorManaListenerThreadPotionGuardTest} と同じくソースの静的走査でガード条件を固定する。
 */
class ArmorManaListenerPotionOwnershipGuardTest {

    private static String readSource() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "ArmorManaListener.java");
        assertTrue(Files.exists(path),
                "ArmorManaListener.javaが見つからない(パス変更時はこのテストの相対パスも更新すること): "
                        + path.toAbsolutePath());
        return Files.readString(path);
    }

    @Test
    void ownsAPerPlayerGrantedPotionLedger() throws IOException {
        String source = readSource();

        assertTrue(source.contains("threadGrantedPotions"),
                "プレイヤーごとの所有権台帳(threadGrantedPotions)が無い。"
                        + "無期限かどうかだけでは『自分が付けたか』を判定できず、他人の無期限効果を"
                        + "誤って剥がしてしまう(W-54)。");
    }

    @Test
    void grantingAThreadEffectRecordsOwnershipInTheLedger() throws IOException {
        String source = readSource();

        assertTrue(source.contains("owned.put(type, desiredAmplifier)"),
                "付与時に所有権台帳へ記録していない。記録しないと、後で外したときに"
                        + "『自分が付けたものか』が判定できず除去できなくなる(スレッドを外しても"
                        + "効果が残り続ける再発)。");
    }

    @Test
    void removalRequiresBothOwnershipRecordAndStillInfinite() throws IOException {
        String source = readSource();

        // 除去分岐は「台帳から記録を取り除けた(=自分が付与した記録がある)」かつ
        // 「isThreadGranted(現在も無期限)」の両方を要求すること。
        assertTrue(source.contains("owned.remove(type) != null && isThreadGranted(existing)"),
                "除去条件が『台帳の記録』と『現在も無期限』の両方を見ていない。"
                        + "isThreadGranted(無期限判定)だけで剥がすと、他人が付けた無期限効果"
                        + "(例: /effect give @s luck infinite)まで誤って剥がしてしまう。");
    }

    @Test
    void doesNotRestoreTheOldSoleInfiniteRemovalCondition() throws IOException {
        String source = readSource();

        // 旧実装(無期限判定だけで剥がす)への先祖返りを検知する。
        assertFalse(source.contains("} else if (isThreadGranted(existing)) {"),
                "isThreadGranted 単独での除去条件が復活している(W-54の再発)。"
                        + "所有権台帳との併用に戻さないと『自分が付けたか』を判定できない。");
    }

    @Test
    void restartBehaviorIsDocumentedExplicitly() throws IOException {
        String source = readSource();

        assertTrue(source.contains("再起動") && source.contains("剥がさない"),
                "サーバ再起動/再ログインを跨いだ場合に台帳が失われたときの挙動"
                        + "(=他人のものとして扱い剥がさない)がjavadocに明記されていない。"
                        + "黙って片方を選ばないこと。");
    }
}
