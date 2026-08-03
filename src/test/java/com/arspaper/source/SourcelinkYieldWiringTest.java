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
        assertTrue(src.contains(
                        "return SourceYield.of(drainBuffer(block), scaleGeneratedYield(SOURCE_PER_TICK));"),
                "受動生成 SOURCE_PER_TICK は passive 側へ入れる必要がある"
                        + "(fromBuffer に混ぜると毎周期バッファへ積み上がる = 2026-08-01 の実バグ)。"
                        + "2026-08-03: 階梯の生成量倍率 scaleGeneratedYield も通すこと"
                        + "(バイタリックは燃料を焼べないので、ここが素の値だと階梯で生成量が伸びない)");
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

    /**
     * 2026-08-03: 階梯の生成量倍率({@code items.<id>.yield-multiplier})が
     * <b>生成点すべて</b>に掛かっていること。
     */
    @ParameterizedTest(name = "{0}Sourcelink の燃料投入は生成量倍率を通す")
    @ValueSource(strings = {"Alchemical", "Mycelial", "Volcanic"})
    @DisplayName("燃料/食料/素材の投入量は scaleGeneratedYield を通してからバッファへ入る")
    void fuelBurnAppliesTheTierYieldMultiplier(String name) throws Exception {
        String src = read("sourcelink/" + name + "Sourcelink.java");
        assertTrue(src.contains("int totalAdded = scaleGeneratedYield((long) sourceValue * addCount);"),
                name + "Sourcelink#onBlockInteract は投入量に生成量倍率を掛ける必要がある"
                        + "(素の sourceValue * addCount だと上位階梯でも素材効率が無印と同じ)");
        assertFalse(src.contains("int totalAdded = sourceValue * addCount;"),
                name + "Sourcelink は倍率未適用の旧式へ戻してはいけない"
                        + "(long キャストも必須: 単価3000万×64個で int が溢れる)");
    }

    @Test
    @DisplayName("成長/撃破ボーナスとホッパー供給も生成量倍率を通す")
    void eventDrivenGenerationAppliesTheTierYieldMultiplier() throws Exception {
        String task = read("SourcelinkTickTask.java");
        assertTrue(task.contains("sourcelink.addToBuffer(block, sourcelink.scaleGeneratedYield(amount));"),
                "accumulateNear(成長/撃破ボーナス)も生成点なので倍率を掛ける必要がある"
                        + "(掛けないとボタニカル/バイタリックだけ階梯で生成量が伸びない)");
        String hopper = Files.readString(Path.of("src/main/java/com/arspaper/block/CustomBlockListener.java"));
        assertTrue(hopper.contains(
                        "sourcelink.addToBuffer(destState.getBlock(), sourcelink.scaleGeneratedYield(sourceValue));"),
                "ホッパー供給も手投入と同じ倍率を掛ける必要がある"
                        + "(片方だけだと「自動化すると素材効率が落ちる」不一致になる)");
    }

    @Test
    @DisplayName("生成量倍率は返却経路には掛からない(満杯ジャーでの無限増殖を防ぐ)")
    void theYieldMultiplierNeverTouchesTheRefundPath() throws Exception {
        String task = read("SourcelinkTickTask.java");
        assertTrue(task.contains("sourcelink.addToBuffer(block, refund);"),
                "注ぎ切れなかった分の返却は倍率を掛けずにそのまま戻す必要がある");
        assertFalse(task.contains("scaleGeneratedYield(refund)"),
                "返却に倍率を掛けると「隣接ジャーが満杯の間だけ毎周期ソースが増える」増殖になる");

        String base = read("sourcelink/Sourcelink.java");
        int start = base.indexOf("public void addToBuffer(Block block, int amount) {");
        assertTrue(start > 0, "addToBuffer の宣言が見つからない(テスト側の前提が古い)");
        int end = base.indexOf("setBuffer(tile, next);", start);
        assertTrue(end > start, "addToBuffer の本体末尾が見つからない(テスト側の前提が古い)");
        assertFalse(base.substring(start, end).contains("scaleGeneratedYield"),
                "倍率を addToBuffer の中で掛けてはいけない —— 生成と返却の両方から呼ばれる共通経路なので、"
                        + "ここで掛けると返却分まで増える(SourceGenerationScaling の javadoc 参照)");
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
