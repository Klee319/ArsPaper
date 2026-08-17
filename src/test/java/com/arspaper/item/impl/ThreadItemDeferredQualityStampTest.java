package com.arspaper.item.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-53(2026-08-18)の回帰ガード: 生成者不明(crafter==null)のスレッド生成で、PDC(rollSeed)を
 * 即座に刻んではいけない。
 *
 * <p><b>直っていたバグ</b>: {@code createItemStack()}(引数なし)は内部で
 * {@code createItemStack(null)} を呼び、旧実装は crafter==null でも
 * {@code TrinityForgeBridge#stampThreadIdentity} を呼んで rollSeed を新規発番しつつ quality=0固定で
 * 刻んでいた。TF の {@code ItemData#hasRollSeed()} は PDC キーの<b>有無</b>だけを見るため(値が0でも
 * 刻印済み扱い)、この時点で刻むと TF {@code PickupQualityListener} の {@code data.hasRollSeed()}
 * ガードに永久に引っかかり、開運(loot-luck)ベースの品質ロールに二度と到達できなくなっていた。
 *
 * <p>このフォークのテスト基盤は Bukkit ランタイム/MockBukkit を持たないため、
 * {@code ArmorManaListenerThreadPotionGuardTest} と同じくソースの静的走査でガード条件を固定する。
 */
class ThreadItemDeferredQualityStampTest {

    private static String readSource() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "impl", "ThreadItem.java");
        assertTrue(Files.exists(path),
                "ThreadItem.javaが見つからない(パス変更時はこのテストの相対パスも更新すること): "
                        + path.toAbsolutePath());
        return Files.readString(path);
    }

    @Test
    void doesNotStampIdentityWhenCrafterIsUnknown() throws IOException {
        String source = readSource();

        assertTrue(source.contains("threadType.hasEffect() && crafter != null"),
                "crafter==nullでもTFのrollSeedを刻んでしまう旧実装に戻っている。"
                        + "PickupQualityListenerのhasRollSeed()ガードに永久に引っかかり、"
                        + "開運(loot-luck)ベースの品質ロールに到達できなくなる(W-53)。");
    }

    @Test
    void doesNotRestoreTheOldUnconditionalStampTernary() throws IOException {
        String source = readSource();

        // 旧実装(crafterを見ずにthreadType.hasEffect()だけで刻む)への先祖返りを検知する。
        assertFalse(source.contains("ThreadIdentity identity = threadType.hasEffect()\n"
                        + "                ? TrinityForgeBridge.stampThreadIdentity(item, crafter)"),
                "crafterの有無を見ない旧三項演算子が復活している(W-53の再発)。");
    }

    @Test
    void exposesARestampEntryPointForExternallyDeterminedQuality() throws IOException {
        String source = readSource();

        assertTrue(source.contains("public boolean restampWithQuality(ItemStack item, int quality)"),
                "外部(TF GiveItemCommand / PickupQualityListener)がreflectionで呼ぶ"
                        + "restampWithQuality(ItemStack, int) エントリポイントが無い。"
                        + "これが無いと /tf give の quality 引数を、TFの汎用装備lore(スレッド専用lore"
                        + "を潰す)を経由せずに反映する手段が無くなる。");
    }

    @Test
    void restampUsesWriteItemRollNotTheGenericStampPath() throws IOException {
        String source = readSource();

        assertTrue(source.contains("TrinityForgeBridge.writeItemRoll(meta, rollSeed, quality)"),
                "restampWithQualityがTrinityForgeBridge#writeItemRoll(PDCのみ書く軽量経路)を"
                        + "使っていない。ItemFactory#stampのような汎用組み立て経路を使うと"
                        + "スレッド専用lore(効果説明/スロット案内/バックパック行)が上書きされる。");
    }
}
