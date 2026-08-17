package com.arspaper.item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-54 の回帰ガード: プレイヤー自身/管理コマンドが付けた効果が、スレッドの再計算
 * (装備変更のたびに走る {@code recalculateArmorBonus})で誤って剥がされないこと。
 *
 * <p><b>第1波(2026-08-18)</b>: 旧実装の {@code isThreadGranted}(無期限かどうかだけを見る)は
 * 「自分が付けたか」を判定できず、{@code /effect give @s luck infinite} のようなプレイヤー起因の
 * 無期限効果まで「スレッドが付けたもの」と誤認して {@code removePotionEffect} していた。
 * 修正は付与記録を持つ所有権台帳({@code threadGrantedPotions})。
 *
 * <p><b>第2波(同日、ユーザー報告「幸運のエフェクトが消える不具合直ってなくない？」)</b>:
 * 台帳の照合が<b>型だけ</b>で amplifier を見ていなかったため、スレッドで LUCK が付いている状態で
 * プレイヤーが同じ型の無期限効果を上書きすると、その後の再計算で<b>プレイヤーの効果</b>を剥がしていた。
 * 判定は {@link ThreadPotionOwnership#mayRemoveGrantedPotion} へ切り出し、
 * <b>挙動そのもの</b>をここで固定する(ソース文字列の一致で縛ると実装差し替えで誤検知するため、
 * 静的走査は「台帳がある/付与時に記録している」の構造チェックだけに残す)。
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
    void removalGoesThroughTheOwnershipPolicy() throws IOException {
        String source = readSource();

        assertTrue(source.contains("ThreadPotionOwnership.mayRemoveGrantedPotion"),
                "除去判定が ThreadPotionOwnership を通っていない。判定を分岐へ直書きすると"
                        + "挙動をテストで固定できず、W-54 の再発を検知できない。");
        assertFalse(source.contains("} else if (isThreadGranted(existing)) {"),
                "isThreadGranted 単独での除去条件が復活している(W-54 第1波の再発)。");
    }

    @Test
    void unrecordedInfiniteEffectIsNeverRemoved() {
        // /effect give @s luck infinite → 台帳に記録なし。再起動を跨いだ場合も同じ形。
        assertFalse(ThreadPotionOwnership.mayRemoveGrantedPotion(null, true, true, 0),
                "付与記録が無い無期限効果を剥がしている(W-54 第1波そのもの)。");
    }

    @Test
    void overwrittenAmplifierIsNeverRemoved() {
        // スレッドが LUCK Lv1(amplifier 0)を付けている状態で、プレイヤーが
        // /effect give @s luck infinite 5 (amplifier 5)で上書きしたケース。
        assertFalse(ThreadPotionOwnership.mayRemoveGrantedPotion(0, true, true, 5),
                "同じ型・無期限のまま他ソースに上書きされた効果を剥がしている"
                        + "(ユーザー報告『幸運のエフェクトが消える』の真因)。");
    }

    @Test
    void finiteEffectIsNeverRemoved() {
        // 通常ポーションで上書きされた(=有限になった)ものは自分の効果ではない。
        assertFalse(ThreadPotionOwnership.mayRemoveGrantedPotion(0, true, false, 0),
                "有限持続に上書きされた効果を剥がしている(飲んだポーションを消す)。");
    }

    @Test
    void absentEffectIsNotTouched() {
        assertFalse(ThreadPotionOwnership.mayRemoveGrantedPotion(0, false, false, Integer.MIN_VALUE),
                "そもそも付いていない効果に対して除去へ進んでいる。");
    }

    @Test
    void ownEffectIsStillRemovedWhenTheThreadComesOff() {
        // ここが false に化けると「スレッドを外しても効果が剥がれない」再発になる。
        assertTrue(ThreadPotionOwnership.mayRemoveGrantedPotion(0, true, true, 0),
                "自分が付けた無期限効果(amplifier一致)を外したのに剥がせていない。");
        assertTrue(ThreadPotionOwnership.mayRemoveGrantedPotion(2, true, true, 2),
                "amplifier 1以上(potion-level 3 等)のスレッド効果を剥がせていない。");
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
