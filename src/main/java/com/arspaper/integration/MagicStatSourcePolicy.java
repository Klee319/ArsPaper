package com.arspaper.integration;

/**
 * 魔法ダメージの「ステータス供給元」と「基礎ダメージ合成」の純粋ロジック（2026-07-31 D6）。
 *
 * <p><b>なぜ別クラスに切り出しているか</b>: このフォークのテスト基盤は Bukkit ランタイム /
 * MockBukkit / Mockito を持たない（{@code SourceAutoConsumeTest} の javadoc 参照）。
 * {@link TrinityForgeBridge} は {@code ItemStack} と TrinityForge プラグイン実体を要求するため
 * 単体テストできないので、判断と算術だけをここへ寄せてテスト可能にしている。
 *
 * <p><b>背景（D6 の真因）</b>: バインド詠唱（{@code SpellBindListener}）は「手に持っている
 * バインド済みアイテム」が {@code spellbooks.yml} の {@code catalysts:} 節に**完全一致で登録済み**の
 * ときだけ、それを触媒引数として {@code SpellCaster.cast} へ渡していた。TF カタログ側の杖11本
 * （{@code BLAZE_ROD#400002}〜{@code #400014} 等）は {@code catalysts:} に無いため、触媒引数が
 * <b>魔導書</b>に化け、魔導書は {@code item-stats.yml} に無いので攻撃力が 0 になっていた
 * （＝「10000 の攻撃力の杖で殴っても魔法が 50 しか出ない」）。
 *
 * <p>対策として「詠唱に使った実アイテム（castItem）」を触媒引数とは別に運び、
 * <b>{@code use-skill: ARS_MAGIC} を持つアイテムだけ</b>をステ供給元として認める。
 * 何でも認めると {@code SpellBindListener#canBind} が任意アイテムを許すため、
 * ネザライトの剣にバインドして近接ステで魔法を撃つ、という上位互換の抜け道になる。
 */
public final class MagicStatSourcePolicy {

    /** 杖・触媒系アイテムに付く TF の {@code use-skill} 値。これ以外はステ供給元として認めない。 */
    public static final String ARS_MAGIC_USE_SKILL = "ARS_MAGIC";

    /** {@code combat/damage.yml} の {@code magical.attack-power-scale} が読めない時の既定値。 */
    public static final double DEFAULT_ATTACK_POWER_SCALE = 1.0;

    /** TF 側 {@code CombatDamageConfig} のスキーマ範囲（[0,10]）と一致させる。 */
    private static final double MIN_ATTACK_POWER_SCALE = 0.0;
    private static final double MAX_ATTACK_POWER_SCALE = 10.0;

    private MagicStatSourcePolicy() {
    }

    /** 魔法の攻撃ステ（攻撃力・会心・貫通など）をどのアイテムから引くか。 */
    public enum StatSource {
        /** 詠唱に使った実アイテム（杖）。D6 で追加した本命の経路。 */
        CAST_ITEM,
        /** {@code catalysts.yml} 登録済みの触媒。castItem が使えない場合の従来経路。 */
        CATALYST,
        /** どちらも使えない（魔導書直接詠唱・儀式・タレット等）。攻撃力は乗らない。 */
        NONE
    }

    /**
     * TF の {@code use-skill} 値が「Ars 魔法のステ供給元として認められる」ものかどうか。
     * 大文字小文字と前後の空白は無視する（{@code item-stats.yml} は手書きなので）。
     */
    public static boolean isMagicUseSkill(String useSkill) {
        return useSkill != null && useSkill.trim().equalsIgnoreCase(ARS_MAGIC_USE_SKILL);
    }

    /**
     * ステ供給元の選択。
     *
     * @param castItemAccepted 詠唱に使った実アイテムが存在し、かつ {@code use-skill: ARS_MAGIC}
     *                         （または {@code catalysts.yml} 登録品）としてステ供給を認められたか
     * @param catalystRegistered 触媒引数が {@code catalysts.yml} 登録済みの触媒か
     */
    public static StatSource chooseStatSource(boolean castItemAccepted, boolean catalystRegistered) {
        if (castItemAccepted) {
            return StatSource.CAST_ITEM;
        }
        return catalystRegistered ? StatSource.CATALYST : StatSource.NONE;
    }

    /** {@code magical.attack-power-scale} を TF 側スキーマと同じ [0,10] にクランプする。 */
    public static double clampAttackPowerScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_ATTACK_POWER_SCALE;
        }
        return Math.max(MIN_ATTACK_POWER_SCALE, Math.min(MAX_ATTACK_POWER_SCALE, scale));
    }

    /**
     * 基礎ダメージへ加算する「杖の攻撃力」分。負の攻撃力は 0 扱い（回復側へ倒さない）。
     * scale=0 なら常に 0（この機能を config で完全にオフにできる）。
     */
    public static double scaledAttackPower(double attackPower, double scale) {
        if (!Double.isFinite(attackPower) || attackPower <= 0.0) {
            return 0.0;
        }
        return attackPower * clampAttackPowerScale(scale);
    }

    /**
     * 魔法の基礎ダメージ = グリフ基礎ダメージ + 杖の攻撃力 × 係数。
     * 仕様（{@code docs/MAGIC_BALANCE_SPEC.md} / {@code docs/COMBAT_SYSTEM_SPEC.md}）の
     * 「デフォルト魔法ダメージ = Arsスペル攻撃力 + 触媒の攻撃力ステータス」を係数付きで表す。
     */
    public static double effectiveBase(double spellBase, double attackPower, double scale) {
        return spellBase + scaledAttackPower(attackPower, scale);
    }

    /**
     * グリフ別ダメージ倍率（TF {@code glyph_damage_multiplier_bonus} / {@code
     * TrinityForge#glyphDamageMultiplier}）を基礎ダメージへ適用する。
     *
     * <p>掛ける対象は<b>「グリフ基礎 + 杖の攻撃力」を合成した後の基礎ダメージ</b>で、
     * 対称パイプライン（守備力・耐性・会心）へ渡す<b>前</b>。グリフ基礎だけに掛けると、
     * 杖の攻撃力が支配的な高レベル帯で「害悪強化 +30%」が実質無効（27 の 30% = +8）になり、
     * lore に出るのに効かないという D6/G5 の症状が形を変えて残るため。
     * 逆に最終ダメージへ掛けてしまうと守備力の減算より後ろになり防御が無意味化するので、
     * ここ（基礎ダメージ層）が正しい適用点。
     *
     * <p>倍率が非有限 / 0以下のときは何も掛けない（fail-open：TF 側の既定は 1.0）。
     */
    public static double applyGlyphMultiplier(double base, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
            return base;
        }
        return base * multiplier;
    }

    /**
     * 増幅(Amplify)グリフ 1段あたりの乗率の既定値。{@code glyphs.yml} の
     * {@code amplify.params.damage-rate-per-stack} が実際に使う値で、これは読めない時のフォールバック。
     */
    public static final double DEFAULT_AMPLIFY_DAMAGE_RATE = 0.10;

    /**
     * 乗率計算に使う増幅段数の絶対値上限の既定値。{@code glyphs.yml} の
     * {@code amplify.params.max-damage-level} が実値。ダメージ系グリフは既に
     * {@code max-augments.amplify}（グリフ互換性チェック、出荷時点で harm/scorch/cold_snap/
     * crush_wave/windshear/lightning/sonic_boom/heavy_impact のいずれも6）で増幅の積み増し自体を
     * 構造的に止めているが、将来の設定変更でその上限が外れても乗率計算側だけは青天井にしない保険。
     */
    public static final int DEFAULT_MAX_AMPLIFY_DAMAGE_LEVEL = 20;

    /**
     * 増幅(Amplify)/減衰(Dampen)の段数を、Sharpness 等ダメージ増加エンチャントと同じ乗算方式で
     * 基礎ダメージへ適用する（2026-08-02。旧仕様は各ダメージ系グリフが「増幅1段+3.0HP」のように
     * <b>グリフ自身の基礎ダメージにだけ</b>固定値を加算していた）。
     *
     * <p><b>変更理由</b>: 杖の攻撃力(attack-power)は {@link #effectiveBase} でこの後に別途加算される。
     * 触媒(杖)が育つほど、グリフ側の固定加算(最大でも数十HP)は攻撃力(Lv100帯で10000超)に対して
     * 相対的に無意味化していた。乗算にすることで、杖の攻撃力を含めた合計ダメージに対して
     * 常に一定割合のボーナスになり、触媒ビルドでも増幅グリフの価値が保たれる。
     *
     * <p>適用点は {@link #effectiveBase} の<b>後</b>（{@link #applyGlyphMultiplier} と同じ理由:
     * グリフ基礎だけに掛けると高攻撃力帯で実質無効になる。守備力等の対称パイプラインより<b>前</b>
     * であることも同じ——最終ダメージへ掛けると守備力の減算より後ろになり、防御が意味を持たなくなる）。
     *
     * <p>倍率は0未満にしない（{@code base} が正である限り、符号反転で回復に化けさせない。
     * 対称パイプライン側の負クランプによる回復変換は既存の別経路（{@code SpellContext#dealSpellDamage}
     * の負ダメージ処理）に任せる）。
     *
     * @param base         {@link #effectiveBase} 適用後の基礎ダメージ
     * @param amplifyLevel 増幅段数（{@code SpellContext#getAmplifyLevel()}。Dampenで負にもなり得る）
     * @param ratePerStack 1段あたりの乗率（既定 {@link #DEFAULT_AMPLIFY_DAMAGE_RATE} = 0.10 = +10%）
     * @param maxLevel     乗率計算に使う段数の絶対値上限（0以下ならクランプなし）
     * @return {@code base * max(0, 1 + ratePerStack * clamp(amplifyLevel, -maxLevel, maxLevel))}。
     *         {@code amplifyLevel==0} または {@code ratePerStack} が非有限/0のときは {@code base} をそのまま返す
     */
    public static double applyAmplifyMultiplier(double base, int amplifyLevel, double ratePerStack, int maxLevel) {
        if (amplifyLevel == 0 || !Double.isFinite(ratePerStack) || ratePerStack == 0.0) {
            return base;
        }
        int clampedLevel = amplifyLevel;
        if (maxLevel > 0) {
            clampedLevel = Math.max(-maxLevel, Math.min(maxLevel, amplifyLevel));
        }
        double multiplier = 1.0 + ratePerStack * clampedLevel;
        if (multiplier < 0.0) {
            multiplier = 0.0;
        }
        return base * multiplier;
    }
}
