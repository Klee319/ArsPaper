package com.arspaper.spell;

/**
 * 詠唱1回で杖の耐久をいくつ減らすかの判定（M-5 / 2026-08-08）。
 *
 * <p><b>直している不具合</b>: 杖は素材が {@code BLAZE_ROD}(最大耐久0)だったので、そもそも
 * 「耐久が減る」という状態を持てなかった。素材を剣系へ移して耐久バーを持たせたが、
 * <b>素材を変えただけでは近接で殴ったときしか減らない</b> —— 杖の本来の用途は詠唱なので、
 * 詠唱側で明示的に消費させないと「耐久はあるが実質無限に使える」ままになる。
 *
 * <p><b>判定を切り出してあるのは意図的</b>: このフォークのテストには<b>サーバ実装が無い</b>
 * （MockBukkit を持たないので、既存の {@code ItemCooldownGenericPathWiringTest} は
 * ソーステキスト検査に逃げている）。{@link #damageFor} は ItemStack にも Player にも触らないので、
 * 消費量・耐久力エンチャントの効き方という<b>間違えると痛い部分だけは挙動で固定できる</b>。
 * ItemStack を触る側（{@code SpellCaster}）は「ここが返した数だけ減らす」に痩せる。
 *
 * <p><b>耐久力(Unbreaking)の式</b>: バニラの道具と同じ「レベル L のとき確率 1/(L+1) でのみ消費」。
 * TF 側の {@code ChainBreakSupport#damageHeldTool} と同じ規則で、TF の
 * {@code DurabilityPenalty#afterUnbreaking}（%ダメージ向けの期待値式）とは<b>あえて別物</b>。
 * 1回1点の消費に期待値式を使うと、切り捨てで「常に0」か「常に1」に潰れてしまう。
 */
public final class CastDurabilityPolicy {

    /** config が丸ごと無い/壊れている場合の既定。無効側に倒す（黙って耐久が溶けるより気づける）。 */
    public static final CastDurabilityPolicy DISABLED = new CastDurabilityPolicy(false, 0, true);

    private final boolean enabled;
    private final int amount;
    private final boolean respectUnbreaking;

    public CastDurabilityPolicy(boolean enabled, int amount, boolean respectUnbreaking) {
        this.enabled = enabled;
        this.amount = amount;
        this.respectUnbreaking = respectUnbreaking;
    }

    public boolean enabled() {
        return enabled;
    }

    public int amount() {
        return amount;
    }

    public boolean respectUnbreaking() {
        return respectUnbreaking;
    }

    /**
     * この詠唱で実際に減らす耐久値（触媒かどうかの門を含む版。<b>実装はこちらを呼ぶこと</b>）。
     *
     * <p><b>触媒(杖)以外は絶対に減らさない（2026-08-25）</b>: {@code SpellBindListener#canBind} は
     * ArsPaper のカスタムアイテム以外なら<b>何にでも</b>スペルをバインドできる。この機能は
     * 「詠唱でしか使わない杖に耐久を効かせる」ために入れたものなので、門が無いと
     * <b>ネザライトの剣・ツルハシ・防具にバインドしただけで、詠唱するたびにその道具が磨耗する</b>
     * （＝バインドが実質デメリットになり、素材を剣系へ移した杖と見分けもつかない）。
     *
     * <p>「触媒か」の判定は {@code TrinityForgeBridge#isCatalystItem} 一本に寄せてある
     * （{@code use-skill: ARS_MAGIC} または {@code catalysts.yml} 登録品）。魔法のステ供給元
     * （{@code MagicStatSourcePolicy}）とアチーブメントの {@code catalyst_cast} と<b>同じ定義</b>で、
     * 杖を1本足すたびに直す場所を増やさないため。
     *
     * @param catalystItem    減らす対象のアイテムが触媒(杖)か。false なら常に 0
     * @param unbreakingLevel 耐久力エンチャントのレベル（未付与なら 0）
     * @param roll            {@code [0.0, 1.0)} の乱数。テストから決定的に渡せるよう引数にしている
     * @return 減らす耐久値。0 なら今回は減らさない
     */
    public int damageFor(boolean catalystItem, int unbreakingLevel, double roll) {
        if (!catalystItem) {
            return 0;
        }
        return damageFor(unbreakingLevel, roll);
    }

    /**
     * 消費量と耐久力エンチャントだけを見た「触媒だった場合の」消費値。
     *
     * <p>触媒の門は含まないので、実装から直接呼ばない
     * （{@link #damageFor(boolean, int, double)} を使う）。門と乱数の効き方を別々にテストできるよう
     * 残してある。
     *
     * @param unbreakingLevel 耐久力エンチャントのレベル（未付与なら 0）
     * @param roll            {@code [0.0, 1.0)} の乱数。テストから決定的に渡せるよう引数にしている
     * @return 減らす耐久値。0 なら今回は減らさない
     */
    public int damageFor(int unbreakingLevel, double roll) {
        if (!enabled || amount <= 0) {
            return 0;
        }
        if (!respectUnbreaking || unbreakingLevel <= 0) {
            return amount;
        }
        // レベル L のとき消費するのは確率 1/(L+1)。L=3 なら 4 回に 1 回だけ減る。
        return roll < 1.0 / (unbreakingLevel + 1) ? amount : 0;
    }

    /**
     * {@code config.yml} の {@code cast-durability} セクションから読む。
     *
     * <p>セクションが無いときに {@link #DISABLED} を返すのは、フォークの config.yml が過去に
     * 丸ごと別ファイルで上書きされ「全キーが既定値のまま半年動いていた」事故があるため
     * （config.yml 冒頭のコメント参照）。既定を有効側に置くと、同じ事故がもう一度起きたときに
     * <b>耐久だけが黙って減り続ける</b>。
     */
    public static CastDurabilityPolicy from(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return DISABLED;
        }
        return new CastDurabilityPolicy(
                section.getBoolean("enabled", false),
                section.getInt("amount", 1),
                section.getBoolean("respect-unbreaking", true));
    }
}
