package com.arspaper.item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-07-26 マナ系ステ穴埋め: {@code mana_bonus}/{@code mana_regen} は
 * {@code TrinityForge#nonItemStatTotal} 経由ではなく、{@code ArsNativeBridge}
 * (パーク general + 永続バフ + 役職バフ + base-stats の唯一の非装備供給源、TF側で一元化)
 * が担当する設計へオーケストレータが決定した(理由: {@code ArsNativeBridge} は既に
 * {@code TrinityForgeBridge.tfNativeMaxManaBonus}/{@code tfNativeManaRegenBonus} 経由で
 * {@code ManaManager.getMaxMana}/{@code getRegenRate} へ配線済みで、ここで
 * {@code TrinityForgeBridge.tfNonItemStatTotal(player, "mana_bonus"/"mana_regen")} を
 * 追加で足すとパーク general + 永続バフの分が二重計上になるため)。
 *
 * <p>このフォークのテスト基盤は Bukkit ランタイム/MockBukkit/Mockito を持たない
 * ({@link com.arspaper.integration.SourceAutoConsumeTest} と同じ制約)ため、
 * {@code ArmorManaListener.recalculateArmorBonus}(static, {@code ArsPaper.getInstance()} の
 * 複数シングルトンに深く依存)を実行するのではなく、ソースを機械的に静的走査して
 * 「{@code mana_bonus}/{@code mana_regen} をキーに {@code tfNonItemStatTotal} を呼ぶコードが
 * 復活していないか」を固定する。設計判断が再度覆って誰かが安易に4キーへ拡張してしまう
 * (=まさに今回発見した二重計上事故そのもの)ことを機械的に検知するための回帰ガード。
 */
class ArmorManaListenerManaBonusGuardTest {

    private static String readSource() throws IOException {
        // このテストクラス自身と同じソースルート配下、com/arspaper/item/ArmorManaListener.java を
        // 相対パスで読む(ビルド成果物ではなくソースそのものを走査するため、mainソースセットを直接参照)。
        Path path = Path.of("src", "main", "java", "com", "arspaper", "item", "ArmorManaListener.java");
        assertTrue(Files.exists(path), "ArmorManaListener.javaが見つからない(パス変更時はこのテストの相対パスも更新すること): " + path.toAbsolutePath());
        return Files.readString(path);
    }

    @Test
    void doesNotCallTfNonItemStatTotalForManaBonusOrManaRegen() throws IOException {
        String source = readSource();

        assertFalse(source.contains("tfNonItemStatTotal(player, \"mana_bonus\")"),
                "mana_bonusにtfNonItemStatTotalを使うとArsNativeBridge(パーク general + 永続バフ経由の"
                        + "tfNativeMaxManaBonus)と二重計上になる。mana_bonusはArsNativeBridge側だけで扱うこと。");
        assertFalse(source.contains("tfNonItemStatTotal(player, \"mana_regen\")"),
                "mana_regenにtfNonItemStatTotalを使うとArsNativeBridge(パーク general + 永続バフ経由の"
                        + "tfNativeManaRegenBonus)と二重計上になる。mana_regenはArsNativeBridge側だけで扱うこと。");
    }

    @Test
    void doesCallTfNonItemStatTotalForHitAndDamageManaRecoveryOnly() throws IOException {
        // hit_mana_recovery/damage_mana_recoveryはArsNativeBridge側に相当経路が無いため、
        // ここ(ArmorManaListener)がtfNonItemStatTotalで非装備分を補うのが正しい設計(タスク4')。
        // このアサーションが将来falseに戻ったら、穴埋め自体が消えたことを示す回帰。
        String source = readSource();

        assertTrue(source.contains("tfNonItemStatTotal(player, \"hit_mana_recovery\")"),
                "hit_mana_recoveryの非アイテム分(パーク/役職/永続バフ/base-stats)がArmorManaListenerから"
                        + "消えている(2026-07-26穴埋めの回帰)");
        assertTrue(source.contains("tfNonItemStatTotal(player, \"damage_mana_recovery\")"),
                "damage_mana_recoveryの非アイテム分がArmorManaListenerから消えている(2026-07-26穴埋めの回帰)");
    }
}
