package com.arspaper.source;

/**
 * 「くべる」操作で1回の右クリックが消費する個数の共通規約（2026-08-24）。
 *
 * <p><b>スニーク＋右クリック = 手持ちスタック全部 / 通常の右クリック = 1個。</b>
 * ソースリンクへの燃料投入と、ソースジャーへのソースベリー投入の<b>両方</b>がこの規約に従う。
 *
 * <p>元は3通りに割れていた —— ソースリンクは「スニーク時だけ一括」、ソースジャーは
 * 「無条件で一括」。同じ「くべる」操作なのに意味が食い違い、しかもジャー側は
 * 1個だけ入れる手段が無かった。一括をスニーク側へ揃えて1本にする。
 *
 * <p><b>1個だけ入れる手段は通常クリック側に必ず残す。</b> 上位ソースリンクの燃料は
 * 1個あたりの価値が桁違い（例 {@code custom:source_engine} = 3,000万）で、逃げ道が無いと
 * 握ったまま右クリックした瞬間にスタック全部が消える。
 *
 * <p>⚠ ここが返すのは「プレイヤーが入れたい個数」であって、入れ<b>られる</b>個数ではない。
 * 容器側の残り容量による頭打ちは各呼び出し側で掛けること
 * （ジャーは {@code SourceJar#convertibleBerries}、ソースリンクはバッファ上限が
 * 実質無制限＋クランプ時に警告ログが出るので掛けていない）。
 */
public final class BulkFeed {

    private BulkFeed() {}

    /**
     * @param handAmount 手に持っているスタックの個数
     * @param sneaking   スニーク中か
     * @return 消費してよい個数（手持ちを超えない。0 なら何も消費させない）
     */
    public static int count(int handAmount, boolean sneaking) {
        if (handAmount <= 0) {
            return 0;
        }
        return sneaking ? handAmount : 1;
    }
}
