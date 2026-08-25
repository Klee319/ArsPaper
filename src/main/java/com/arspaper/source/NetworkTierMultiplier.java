package com.arspaper.source;

/**
 * 網（ドミニオンワンドで結んだ経路）で<b>送信元の階梯</b>に応じた転送量倍率を引く（2026-08-25 / W-257）。
 *
 * <p><b>なぜ要るか</b>: 網の転送は長らく「定額 100 ÷ 40tick = 毎秒2.5点」で固定されていた。
 * 隣接供給（ソースリンク→隣のジャー）には {@code sourcelinks.yml} の
 * {@code items.<id>.transfer-multiplier} が掛かるのに、<b>網には階梯倍率が1つも掛からない</b>ため、
 * 上位リンクにしても上位ジャーにしても速度が1点も変わらなかった
 * （ソース機関1個=3,000万を運ぶのに約7日）。容量だけが階梯で伸びて速度が伸びないと、
 * 上位ジャーは「大きいだけで遅い箱」になる。
 *
 * <p><b>送信元側の倍率を使う</b>。転送は送信元から引く操作なので、
 * 「どれだけ勢いよく吐けるか」は送り出す側の設備で決まる、という読みに揃えてある
 * （受信側で決めると、貧弱なジャーから上位ジャーへ吸い出すだけで最高速になる）。
 *
 * <p>倍率の出どころは送信元の種別で分かれる:
 * <ul>
 *   <li>ソースリンク … {@code sourcelinks.yml} の {@code items.<id>.transfer-multiplier}
 *       （隣接供給と同じ値。網と隣接で階梯の意味を食い違わせない）</li>
 *   <li>ソースジャー … {@code sourcejars.yml} の {@code jars.<id>.transfer-multiplier}</li>
 * </ul>
 *
 * <p>⚠ <b>infinity_source_core の補正はここでは掛けない</b>。あれは「ソースリンクの周囲」に置く
 * 設備で、網の経路はワールド内のどこにでも伸びるので「どのコアの半径内か」が定義できない。
 */
public final class NetworkTierMultiplier {

    /** 倍率が引けなかったときの値（=補正なし）。 */
    public static final double NONE = 1.0;

    private NetworkTierMultiplier() {
    }

    /**
     * 純関数版。{@code sourcelinkMultiplier} を優先し、無ければ {@code jarMultiplier}、
     * どちらも無ければ {@link #NONE}。0以下・非有限は「壊れた値」として {@link #NONE} に倒す。
     *
     * <p>ソースリンクを先に見るのは {@link SourceStorage#of} と<b>逆</b>である点に注意
     * ── あちらは「クリエイティブジャーのように別クラスでも custom_block_id がジャーとして
     * 書かれている個体」を拾うためにジャーを先に見るが、倍率については
     * <b>id がソースリンクとして定義されているならそれが正</b>（同じ id が両方に定義されることは
     * {@code registerCustomSourcelinks} が弾いているので、実際には片方しか埋まらない）。
     */
    public static double resolve(Double sourcelinkMultiplier, Double jarMultiplier) {
        Double picked = sane(sourcelinkMultiplier) ? sourcelinkMultiplier
                : (sane(jarMultiplier) ? jarMultiplier : null);
        return picked == null ? NONE : picked;
    }

    private static boolean sane(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0;
    }

    /**
     * 1周期・1リンクに送れる「定額 x 階梯倍率」。int を溢れさせない
     * （{@link InfinityCoreEffect#scaleCap} と同じクランプを通す）。
     */
    public static int scale(int flatPerTransfer, double multiplier) {
        return InfinityCoreEffect.scaleCap(flatPerTransfer, multiplier);
    }

    /**
     * 設置ブロックの {@code custom_block_id} から倍率を引く（実行時版）。
     * ArsPaper 未初期化・id 未定義では {@link #NONE}。
     */
    public static double forBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return NONE;
        }
        com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
        if (ars == null) {
            return NONE;
        }
        Double linkMultiplier = null;
        if (ars.getSourcelinkConfig() != null) {
            linkMultiplier = ars.getSourcelinkConfig().item(blockId)
                    .map(com.arspaper.source.sourcelink.SourcelinkConfig.ItemDef::transferMultiplier)
                    .orElse(null);
        }
        Double jarMultiplier = null;
        if (ars.getSourceJarConfig() != null) {
            jarMultiplier = ars.getSourceJarConfig().get(blockId)
                    .map(com.arspaper.block.SourceJarConfig.JarDef::transferMultiplier)
                    .orElse(null);
        }
        return resolve(linkMultiplier, jarMultiplier);
    }
}
