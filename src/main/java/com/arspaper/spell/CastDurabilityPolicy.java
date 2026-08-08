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
     * この詠唱で実際に減らす耐久値。
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
