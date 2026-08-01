package com.arspaper.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SourceYieldRefundTest} が固定した規約に、<b>5実装と呼び出し側が実際に従っている</b>ことを
 * ソーステキストで担保する(2026-08-01)。
 *
 * <p>純関数のテストだけでは「規約は正しいが実装が別のことをしている」を検出できない。
 * まさにそれが今回の実バグ(呼び出し側が {@code addToBuffer(block, leftover)} と全額戻していた)
 * の正体だった。このフォークは Bukkit ランタイムを持たないため、
 * {@code MagicStatSourceWiringTest} と同じソース検査でこの種の無言の断線を止める。
 */
class SourcelinkYieldWiringTest {

    private static final String SRC = "src/main/java/com/arspaper/source/";

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(SRC + relative));
    }

    /** バッファからしか吐かない(受動生成を持たない)4実装。 */
    @ParameterizedTest(name = "{0}Sourcelink はバッファ由来だけを返す")
    @ValueSource(strings = {"Alchemical", "Botanical", "Mycelial", "Volcanic"})
    @DisplayName("受動生成を持たない4実装は SourceYield.ofBuffer(drainBuffer(block)) を返す")
    void bufferOnlySourcelinksDeclareNoPassivePart(String name) throws Exception {
        String src = read("sourcelink/" + name + "Sourcelink.java");
        assertTrue(src.contains("public SourceYield generateSource(Block block)"),
                name + "Sourcelink#generateSource は SourceYield を返す必要がある"
                        + "(int に戻すと受動生成とバッファ由来の区別が消える)");
        assertTrue(src.contains("return SourceYield.ofBuffer(drainBuffer(block));"),
                name + "Sourcelink はバッファから引いた分だけを返す必要がある");
        assertFalse(src.contains("SourceYield.of("),
                name + "Sourcelink は受動生成を持たない。passive を足すと"
                        + "「ジャーが無いだけでバッファが増える」抜け穴になる");
    }

    @Test
    @DisplayName("バイタリックだけが受動生成を passive 側へ入れる(fromBuffer に混ぜない)")
    void vitalicDeclaresItsPassiveGenerationSeparately() throws Exception {
        String src = read("sourcelink/VitalicSourcelink.java");
        assertTrue(src.contains("public SourceYield generateSource(Block block)"),
                "VitalicSourcelink#generateSource は SourceYield を返す必要がある");
        assertTrue(src.contains("return SourceYield.of(drainBuffer(block), SOURCE_PER_TICK);"),
                "受動生成 SOURCE_PER_TICK は passive 側へ入れる必要がある"
                        + "(fromBuffer に混ぜると毎周期バッファへ積み上がる = 2026-08-01 の実バグ)");
        assertFalse(src.contains("SOURCE_PER_TICK + bonus"),
                "旧実装(受動生成とバッファ由来を int で合算)へ戻してはいけない");
    }

    @Test
    @DisplayName("抽象メソッドの戻り値型が SourceYield で、5実装が規約を共有している")
    void theContractIsDeclaredOnceOnTheBaseClass() throws Exception {
        String base = read("sourcelink/Sourcelink.java");
        assertTrue(base.contains("public abstract SourceYield generateSource(Block block);"),
                "規約は基底クラスの抽象メソッドで1つに揃える");
    }

    @Test
    @DisplayName("ティックタスクは残量を全額ではなく refundToBuffer 経由でしか戻さない")
    void tickTaskRefundsOnlyTheBufferDerivedPart() throws Exception {
        String task = read("SourcelinkTickTask.java");
        assertTrue(task.contains("SourceYield yield = sourcelink.generateSource(block);"),
                "生成量は内訳付きで受け取る必要がある");
        assertTrue(task.contains("int refund = SourceYield.refundToBuffer(yield, leftover);"),
                "返却量は純関数 refundToBuffer で決める必要がある");
        assertFalse(task.contains("addToBuffer(block, leftover)"),
                "残量を全額戻すと受動生成分まで蓄積する(2026-08-01 の実バグそのもの)");
    }

    @Test
    @DisplayName("buffer-cap 到達の警告はブロックごとに1回へラッチされている")
    void bufferCapWarningIsLatchedPerBlock() throws Exception {
        String base = read("sourcelink/Sourcelink.java");
        assertTrue(base.contains("BUFFER_CAP_WARNED.add(key)"),
                "上限到達の警告は同一ブロックで1回だけにする必要がある"
                        + "(buffer-cap を有限にすると毎周期クランプするのが定常状態になるため)");
        assertTrue(base.contains("BUFFER_CAP_WARNED.remove(key)"),
                "上限を下回ったらラッチを解除する必要がある(再発時にもう一度警告するため)");
        assertTrue(read("SourcelinkTickTask.java").contains("Sourcelink.forgetBufferCapWarning("),
                "撤去/消失したソースリンクのラッチは解放する必要がある");
    }
}
