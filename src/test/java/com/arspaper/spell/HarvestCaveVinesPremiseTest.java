package com.arspaper.spell;

import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 収穫グリフが洞窟のつた（グロウベリー）を扱う際の<b>前提</b>を固定する（2026-08-24）。
 *
 * <p>{@code HarvestEffect#processBlock} は洞窟のつたの分岐を
 * <b>Ageable 作物の分岐より前</b>に置いている。順番を入れ替えると、実を摘む代わりに
 * {@code breakNaturally()} でつるごと壊れる。その「前より後ろだと壊れる」根拠は
 * <b>Bukkit の型階層</b>にあり、コードを読んだだけでは見えない:
 *
 * <ul>
 *   <li>{@code CaveVines}（つるの先端）は {@link Ageable} でもある。
 *       その {@code age} は「つるがどこまで伸びたか」であって実の熟度ではないので、
 *       Ageable 分岐が先に当たると最大 age のつるが収穫対象として壊される。</li>
 *   <li>{@code CaveVines} は {@link CaveVinesPlant} でもあるので、実の有無の判定は
 *       {@code CaveVinesPlant} 1本で先端と本体の両方を拾える。</li>
 * </ul>
 *
 * <p>この2つは Bukkit 側の都合でいつでも変わりうる。変わったときに
 * 「なぜか収穫でつるが消える」を実サーバで踏む前に、ここで落ちるようにしておく。
 *
 * <p>※このフォークのテストは素の JUnit + paper-api で、MockBukkit が無い。
 * ブロックを実際に置いて摘む経路そのものは自動テストできないため、
 * <b>壊れやすい前提だけ</b>を型で押さえている。
 */
class HarvestCaveVinesPremiseTest {

    @Test
    @DisplayName("つるの先端は Ageable でもある(=洞窟のつた分岐を作物分岐より前に置く根拠)")
    void caveVinesHeadIsAlsoAgeable() {
        assertTrue(Ageable.class.isAssignableFrom(CaveVines.class),
                "CaveVines が Ageable でなくなった。HarvestEffect の分岐順の根拠が消えたので"
                        + " processBlock の Case 0 / Case 1 の並びを見直すこと");
    }

    @Test
    @DisplayName("先端も CaveVinesPlant として実の有無を読める(判定を1本にできる根拠)")
    void caveVinesHeadIsAlsoCaveVinesPlant() {
        assertTrue(CaveVinesPlant.class.isAssignableFrom(CaveVines.class),
                "CaveVines が CaveVinesPlant でなくなった。HarvestEffect は先端を取りこぼすので"
                        + " CaveVines 用の分岐を足すこと");
    }
}
