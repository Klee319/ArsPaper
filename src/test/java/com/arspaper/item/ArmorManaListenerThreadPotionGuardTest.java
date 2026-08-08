package com.arspaper.item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-07-26 錬金監査(注入グリフ × 体力増強ポーション)の回帰ガード。
 *
 * <p>{@code ArmorManaListener.updatePotionEffects} はスレッド由来のポーション効果を
 * 「無期限・振幅0」で付与する。Bukkit の {@code addPotionEffect} は同種の既存効果を
 * <b>上書きする</b>ため、素朴に無条件で呼ぶと次の2つの事故が起きる:
 *
 * <ol>
 *   <li>HEALTH_BOOST スレッドを装備したまま体力増強II(振幅1)のポーションを飲むと、
 *       次の再計算(ホットバーの持ち替えでも走る)でレベル1・無期限へ<b>格下げ</b>される。</li>
 *   <li>解除側が「無期限なら剥がす」だけだと、他ソースの無期限効果まで誤爆で剥がす。</li>
 * </ol>
 *
 * <p>このフォークのテスト基盤は Bukkit ランタイム/MockBukkit を持たない
 * ({@link ArmorManaListenerManaBonusGuardTest} と同じ制約)ため、実行ではなく
 * ソースの静的走査でガード条件の存在を固定する。
 */
class ArmorManaListenerThreadPotionGuardTest {

    private static String readSource() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "ArmorManaListener.java");
        assertTrue(Files.exists(path),
                "ArmorManaListener.javaが見つからない(パス変更時はこのテストの相対パスも更新すること): "
                        + path.toAbsolutePath());
        return Files.readString(path);
    }

    @Test
    void doesNotDowngradeStrongerExistingPotionEffect() throws IOException {
        String source = readSource();

        // 2026-08-08: threads.yml の potion-level(1以上)対応で、スレッド自身の amplifier が
        // 0固定でなくなった。amplifier固定0前提の比較(existing.getAmplifier() > 0)へ戻すと、
        // レベル2以上のスレッド(desiredAmplifier > 0)が自分自身の付与済み効果を「既存のほうが強い」
        // と誤判定して一生付かなくなる。desiredAmplifierとの比較になっていることを固定する。
        assertTrue(source.contains("existing.getAmplifier() > desiredAmplifier"),
                "スレッド効果の付与前に『既存のほうが強いか』を desiredAmplifier と比較していない。"
                        + "無条件 addPotionEffect は体力増強IIをレベル1・無期限へ格下げする。");
        assertFalse(source.contains("existing.getAmplifier() > 0"),
                "amplifier固定0前提の旧比較(existing.getAmplifier() > 0)が復活している。"
                        + "これだとレベル2以上のスレッドが自分自身の付与済み効果を『既存のほうが強い』"
                        + "と誤判定し一生付かなくなる。");
    }

    @Test
    void removesOnlyItsOwnThreadGrantedSignature() throws IOException {
        String source = readSource();

        assertTrue(source.contains("isThreadGranted("),
                "解除側が自前由来(無期限)の署名判定を使っていない。"
                        + "『無期限なら剥がす』だけだと他ソースの無期限効果まで誤爆で剥がす。");
        // 2026-08-08: potion-level対応でisThreadGrantedのamplifier<=0制約は撤廃した
        // (レベル2以上を外したときに解除できなくなるため)。無期限判定だけが残っていることを固定する。
        assertTrue(source.contains("effect.isInfinite()"),
                "isThreadGranted が無期限判定を使っていない。amplifierでの絞り込みは"
                        + "potion-level対応で撤廃済みなので、無期限であることが唯一の識別子。");
        assertFalse(source.contains("effect.getAmplifier() <= 0"),
                "isThreadGranted に amplifier<=0 制約が復活している。復活させると、"
                        + "potion-levelで2以上を指定したスレッドを外したときに"
                        + "『外しても剥がれない』(無期限効果が永久に残る)が再発する。");
    }
}
