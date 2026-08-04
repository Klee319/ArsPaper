package com.arspaper.spell.effect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ignite/Hex/Explosion の3エフェクトが、TFの対称パイプライン
 * ({@link com.arspaper.spell.SpellContext#dealSpellDamage}) を必ず経由してダメージを与えることを固定する。
 *
 * <p>2026-08-04 以前、この3エフェクトはバニラの {@code LivingEntity#damage(...)} を直接呼んでいたため
 * TFの守備力・耐性・PvP抑制などを一切通らず「炎上/爆発/呪詛の追撃だけバニラスケールのまま」になっていた
 * （combat.md「魔法基礎ダメージは…」節参照）。このフォークは Bukkit ランタイムを持たないため、
 * {@code DefenseIgnoringDamageWiringTest} と同じソーステキスト検査で無言の断線を止める。
 */
class MagicDamagePipelineWiringTest {

    private static String read(String effect) throws Exception {
        return Files.readString(Path.of("src/main/java/com/arspaper/spell/effect/" + effect + ".java"));
    }

    @Test
    @DisplayName("IgniteEffectの継続火炎ダメージはdealSpellDamage経由で、生のtarget.damageを直接呼ばない")
    void igniteUsesDealSpellDamageAndCheckedFireResistance() throws Exception {
        String src = read("IgniteEffect");
        assertTrue(src.contains("context.dealSpellDamage(target, finalDamage, id.getKey(), false)"),
                "IgniteEffect は継続ダメージを dealSpellDamage 経由(增幅二重計上防止のためfalse)で与える必要がある");
        assertFalse(src.contains("target.damage(finalDamage"),
                "IgniteEffect が生の target.damage(...) を直接呼ぶと守備力/耐性/PvP抑制を素通りする");
        assertTrue(src.contains("PotionEffectType.FIRE_RESISTANCE"),
                "TFパイプラインはDamageType.ON_FIREのバニラ自動免疫判定を持たない(cause=MAGIC固定)ため、"
                        + "IgniteEffect 自身が火炎耐性を明示チェックしないと耐性が無言で無効化される");
    }

    @Test
    @DisplayName("HexEffectの追撃ダメージはdealSpellDamage経由で、生のtarget.damage(double)を直接呼ばない")
    void hexUsesDealSpellDamage() throws Exception {
        String src = read("HexEffect");
        assertTrue(src.contains("hexContext.dealSpellDamage(target, bonusDamage, id.getKey(), false)"),
                "HexEffect の追撃ダメージは dealSpellDamage 経由(增幅は詠唱時に既に織り込み済みのためfalse)で与える必要がある");
        assertFalse(src.contains("target.damage(bonusDamage)"),
                "HexEffect が生の target.damage(bonusDamage) を直接呼ぶと守備力/耐性/PvP抑制を素通りする");
    }

    @Test
    @DisplayName("ExplosionEffectはバニラのENTITY_EXPLOSIONダメージをキャンセルし、TFパイプラインで二重にならず与える")
    void explosionCancelsVanillaDamageAndUsesDealSpellDamage() throws Exception {
        String src = read("ExplosionEffect");
        assertTrue(src.contains("event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION"),
                "ExplosionEffect は自前の爆発が発生させるENTITY_EXPLOSIONダメージを判定してキャンセルする必要がある"
                        + "(でないとバニラの生ダメージがTFスケールをバイパスしたまま残る)");
        assertTrue(src.contains("event.setCancelled(true)"),
                "ExplosionEffect はバニラのENTITY_EXPLOSIONダメージを実際にキャンセルする必要がある");
        assertTrue(src.contains("context.dealSpellDamage(entity, damage, id.getKey(), false)"),
                "ExplosionEffect はエンティティダメージを dealSpellDamage 経由で与える必要がある");

        int depthIncrementAt = src.indexOf("activeExplosionDepth++;");
        int createExplosionAt = src.indexOf("createExplosion(center, power, false, false, caster)");
        int depthDecrementAt = src.indexOf("activeExplosionDepth--;");
        assertTrue(depthIncrementAt >= 0 && createExplosionAt >= 0 && depthDecrementAt >= 0,
                "ExplosionEffect の深度カウンタとcreateExplosion呼び出しが見つからない"
                        + "(リネームしたなら本テストも更新すること)");
        assertTrue(depthIncrementAt < createExplosionAt && createExplosionAt < depthDecrementAt,
                "深度カウンタは createExplosion 呼び出しを完全に囲む必要がある"
                        + "(でないと合成ダメージイベントの判定窓を取りこぼす)");
    }
}
