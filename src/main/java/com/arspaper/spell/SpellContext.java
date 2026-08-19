package com.arspaper.spell;

import com.arspaper.ArsPaper;
import com.arspaper.spell.effect.LingerEffect;
import com.arspaper.spell.effect.RuneEffect;
import com.arspaper.util.WorldGuardHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * スペル発動時の実行コンテキスト。
 * Ars Nouveau準拠: AugmentはEffectの後ろに配置し、そのEffectを強化する。
 *
 * 例: [Proj] [Harm] [Amplify] [AOE] [Heal] [ExtendTime]
 *   → Harm に Amplify+AOE、Heal に ExtendTime が適用される。
 *
 * SpellStats (Ars Nouveau準拠):
 * - amplifyLevel: Amplify(+1)/Dampen(-1) で変動
 * - aoeLevel: AOE(+1) で変動
 * - durationLevel: ExtendTime(+1)/DurationDown(-1) で変動
 * - acceleration: Accelerate(+1.0)/Decelerate(-0.5) で変動
 * - pierceCount: Pierce buff count
 * - splitCount: Split buff count
 * - sensitive: Sensitive flag
 * - randomizing: Randomize flag
 * - extractCount: Extract buff count (Fortuneと排他)
 * - fortuneLevel: Fortune buff count (Extractと排他)
 */
public class SpellContext {

    private final UUID casterUuid;
    private final SpellRecipe recipe;
    private final List<SpellComponent> components;
    /**
     * 詠唱に使った触媒（ワンド/スペルブック）の ItemStack。会心/貫通を魔法ダメージへ連携するために保持する。
     * 儀式・タレット等の非プレイヤー詠唱では特定できないため {@code null}（従来どおり plain(0) フォールバック）。
     */
    private final ItemStack catalyst;
    /**
     * 実際に右クリックして詠唱したアイテム（2026-07-31 D6）。バインド詠唱では
     * {@code catalyst} が「{@code catalysts.yml} 登録済みの触媒」または<b>魔導書</b>に化けるため、
     * 杖のステータス（攻撃力・会心・貫通等）を拾う唯一の経路がこれになる。
     *
     * <p>魔導書直接詠唱・儀式・タレット等では {@code null}（従来どおり攻撃力は乗らない）。
     * {@code use-skill: ARS_MAGIC} を持つ品だけがステ源として採用される判定は
     * {@code TrinityForgeBridge#resolveMagicStatSource} 側にある（剣にバインドして近接ステで
     * 魔法を撃つ抜け道を塞ぐため）。
     */
    private final ItemStack castItem;

    // === キャンセルフラグ（エフェクトがマナ消費なしで中止する場合に使用） ===
    private boolean cancelled = false;
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    // === Augment統計値（Effect適用後にリセット） ===
    private int amplifyLevel = 0;
    private int aoeLevel = 0;
    private int durationLevel = 0;     // ExtendTime(+1) / DurationDown(-1)
    private double acceleration = 0.0; // Accelerate(+1.0) / Decelerate(-0.5)
    private int pierceCount = 0;
    private int splitCount = 0;
    private boolean sensitive = false;
    private boolean randomizing = false;
    private int extractCount = 0;
    private int fortuneLevel = 0;

    // パターン増強用
    private boolean wallPattern = false;    // 壁パターン（壁状AOE）
    private int lingerLevel = 0;            // 残留レベル（1個=5秒、0=無効）
    private int delayTicks = 0;             // 遅延ティック
    private int rapidFireLevel = 0;         // 連射レベル（CT短縮）
    private boolean traceActive = false;     // 軌跡（経路上に効果適用）
    private int propagateChainCount = 0;     // 伝播（エンティティヒット時に周辺の敵にチェーン、1段=3体）
    private boolean inPropagateChain = false; // チェーン中フラグ（無限再帰防止）
    private int reachLevel = 0;              // 延伸（射程/距離延長）
    private boolean secondaryInvocation = false; // 二次発動（伝播/軌跡経由、パーティクル削減用）

    // Form-augment用（ProjectileForm等が参照）
    private double projectileSpeedMultiplier = 1.0;

    // ヒット面情報（ブロックAOE展開で使用）
    private org.bukkit.block.BlockFace hitFace = null;
    public org.bukkit.block.BlockFace getHitFace() { return hitFace; }
    public void setHitFace(org.bukkit.block.BlockFace hitFace) { this.hitFace = hitFace; }

    /**
     * <b>1回の詠唱で共有される状態</b>（2026-08-19）。{@link #copy()} は<b>参照をそのまま渡す</b>ので、
     * 同じ詠唱から生まれたコンテキストのコピーは全て同じインスタンスを見る。
     *
     * <p><b>なぜ要るか</b>: 伝播チェーンの除外集合はこれまで {@code resolveGroupsOnEntity} の
     * ローカル変数で、その回の対象と術者しか入っていなかった。炸裂のように<b>フォームが複数の対象へ
     * 独立に resolve する</b>と、A のチェーンが B・C（＝炸裂が直撃させる相手）を選んでしまい、
     * 実際のダメージはバニラの無敵時間に吸われて<b>伝播ぶんのマナが丸ごと無駄になっていた</b>。
     * ヒット済みを詠唱単位で共有すると、チェーンは代わりに<b>まだ当たっていない相手</b>を選ぶので、
     * 同じマナが「重複」ではなく「到達範囲」に変わる。
     */
    private static final class CastState {
        /** この詠唱で既に効果を与えた（または与えることが確定した）エンティティ。 */
        private final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();
        /** この詠唱で消費したチェーン数。上限は propagate.params.max-chains-per-cast。 */
        private int chainsUsed = 0;
    }

    /** 詠唱1回で共有する状態（コピー間で参照を共有する）。 */
    private final CastState castState;

    /**
     * 伝播チェーン先に掛ける威力倍率（1.0 = 減衰なし）。
     * 近い順に {@code propagate.params.damage-falloff-per-hop} ずつ引き、
     * {@code min-damage-rate} で下げ止まる。{@link #dealSpellDamage} が最終ダメージへ掛ける。
     */
    private double propagateDamageRate = 1.0;

    public SpellContext(Player caster, SpellRecipe recipe) {
        this(caster, recipe, null);
    }

    /**
     * 触媒付きコンテキスト。触媒（ワンド/スペルブック）の会心/貫通を魔法ダメージへ連携する。
     *
     * @param catalyst 詠唱に使った触媒 ItemStack。特定不能なら {@code null}
     */
    public SpellContext(Player caster, SpellRecipe recipe, ItemStack catalyst) {
        this(caster, recipe, catalyst, null);
    }

    /**
     * 触媒 + 詠唱に使った実アイテム付きコンテキスト（2026-07-31 D6）。
     *
     * @param catalyst 詠唱に使った触媒 ItemStack（{@code catalysts.yml} 登録品、または非触媒バインド
     *                 詠唱では魔導書本体）。特定不能なら {@code null}
     * @param castItem 実際に右クリックして詠唱したアイテム（杖など）。特定不能なら {@code null}
     */
    public SpellContext(Player caster, SpellRecipe recipe, ItemStack catalyst, ItemStack castItem) {
        this.casterUuid = caster.getUniqueId();
        this.recipe = recipe;
        this.components = recipe.getComponents();
        // 生成毎に呼び出し側スタックへ影響しないよう防御的コピー（不変扱い）。
        this.catalyst = (catalyst != null) ? catalyst.clone() : null;
        this.castItem = (castItem != null) ? castItem.clone() : null;
        this.castState = new CastState();
    }

    private SpellContext(SpellContext other) {
        this.casterUuid = other.casterUuid;
        this.recipe = other.recipe;
        this.components = other.components;
        this.catalyst = other.catalyst;
        this.castItem = other.castItem;
        // ★参照を共有する（値コピーにすると伝播の重複排除が詠唱単位で効かなくなる）。
        this.castState = other.castState;
    }

    /**
     * 詠唱に使った触媒 ItemStack を返す。非プレイヤー詠唱等で特定できない場合は {@code null}。
     */
    public ItemStack getCatalyst() {
        return catalyst;
    }

    /**
     * 実際に右クリックして詠唱したアイテム（杖など）を返す。特定できない場合は {@code null}。
     */
    public ItemStack getCastItem() {
        return castItem;
    }

    public Player getCaster() {
        return Bukkit.getPlayer(casterUuid);
    }

    public UUID getCasterUuid() {
        return casterUuid;
    }

    public SpellRecipe getRecipe() {
        return recipe;
    }

    // === Amplify/Dampen ===
    // 減衰は増幅の半分の効果（2回積んで-1レベル）
    private int dampenAccum = 0;
    private int durationDownAccum = 0;

    public int getAmplifyLevel() { return amplifyLevel; }
    public void setAmplifyLevel(int amplifyLevel) { this.amplifyLevel = amplifyLevel; }
    public void applyDampen() {
        dampenAccum++;
        if (dampenAccum % 2 == 0) {
            amplifyLevel--;
        }
    }

    /** 減衰が1回以上適用されているか */
    public boolean hasDampen() {
        return dampenAccum > 0;
    }

    /** 減衰の累積回数を返す */
    public int getDampenAccum() {
        return dampenAccum;
    }

    // === AOE 視線基準3軸 ===
    // 横: 視線に対して左右
    public int getAoeWidth() { return aoeLevel; }
    public void setAoeWidth(int w) { this.aoeLevel = w; }
    // 縦: 視線に対して上下
    private int aoeHeightLevel = 0;
    public int getAoeHeight() { return aoeHeightLevel; }
    public void setAoeHeight(int h) { this.aoeHeightLevel = h; }
    // 奥: 視線方向
    private int aoeVerticalLevel = 0;
    public int getAoeDepth() { return aoeVerticalLevel; }
    public void setAoeDepth(int d) { this.aoeVerticalLevel = d; }

    // 後方互換
    public int getAoeLevel() { return aoeLevel; }
    public void setAoeLevel(int aoeLevel) { this.aoeLevel = aoeLevel; }
    public int getAoeVerticalLevel() { return aoeVerticalLevel; }
    public void setAoeVerticalLevel(int v) { this.aoeVerticalLevel = v; }

    // === AOE (半径) - 内部AOE処理エフェクト用（召喚数/爆発威力/拾い範囲等） ===
    private int aoeRadiusLevel = 0;
    public int getAoeRadiusLevel() { return aoeRadiusLevel; }
    public void setAoeRadiusLevel(int aoeRadiusLevel) { this.aoeRadiusLevel = aoeRadiusLevel; }

    // === Duration (ExtendTime/DurationDown) ===
    // 短縮は延長の半分の効果（2回積んで-1レベル）
    public int getDurationLevel() { return durationLevel; }
    public void setDurationLevel(int durationLevel) { this.durationLevel = durationLevel; }
    public void applyDurationDown() {
        durationDownAccum++;
        if (durationDownAccum % 2 == 0) {
            durationLevel--;
        }
    }

    /** 後方互換: 旧durationTicks互換。各Effectが自分で解釈すべき。 */
    public int getDurationTicks() { return durationLevel * 200; }

    // 2026-08-02: 旧仕様（増強/減衰は呼び出し側で spellBase に内包済みが前提、という説明）は
    // dealSpellDamage(target, spellBase, glyphId, boolean) の javadoc へ置き換えた
    // （増幅の乗算ボーナスをどちらが持つかが呼び出し側で選べるようになったため）。

    /**
     * <b>防御無視ダメージ用の基礎ダメージ</b>（日輪/月輪の直接HP減少）。
     * <b>グリフ基礎＋増減グリフだけ</b>で、杖・触媒の攻撃力(attack-power)は<b>絶対に乗せない</b>。
     *
     * <p><b>この線引きの根拠（2026-07-31 F4。2026-07-30 に一度 attack-power を乗せたのを撤回）</b>:
     * ユーザー確定方針「魔法ダメージに攻撃力を100%加算し近接と対等にする」は
     * <b>通常ダメージ経路（{@link #dealSpellDamage} → TF の対称パイプライン）の話</b>である。
     * 防御無視ダメージは {@code setHealth} で直接HPを削る＝{@code EntityDamageEvent} すら発火せず、
     * 守備力・耐性・回避・トーテム・盾・TF の {@code PvpDamagePolicy} の<b>いずれも通らない</b>。
     * <b>近接側にこれと対応する経路が存在しない</b>ので、そもそも「対等性」の対象外であり、
     * ここに伸びる値（attack-power は Lv100 帯で10000超）を足すと軽減不能の即死ボタンになる。
     * 杖の攻撃力を活かしたいなら {@link #dealSpellDamage} を使うダメージグリフを使うのが正しい。
     *
     * <p>会心/貫通/出血/回避も一切かからない。防御無視でない普通のダメージ魔法は
     * 必ず {@link #dealSpellDamage} を通すこと。
     *
     * <p>戻り値は<b>そのまま HP から引いてはいけない</b> —
     * {@link DefenseIgnoringDamagePolicy#cappedDamage} で「対象の最大体力比」の上限
     * （1発／1詠唱の累計）を必ず通すこと。理由は同クラスの javadoc 参照。
     *
     * @param spellBase グリフ由来の基礎ダメージ(増減グリフ適用済み)
     * @return {@code spellBase} そのまま（この経路には装備由来のステを一切合成しない）
     */
    public double defenseIgnoringDamage(double spellBase) {
        return spellBase;
    }

    /**
     * 対象の最大体力。属性が読めない環境では {@code 0} を返し、
     * {@link DefenseIgnoringDamagePolicy#cappedDamage} 側で<b>割合上限だけが無効化される</b>
     * （TF の {@code PvpDamagePolicy#maxHealthOf} と同じ安全側の流儀）。
     * 日輪/月輪が同じ読み方を二重実装しないための共有ヘルパ。
     */
    public static double maxHealthOf(LivingEntity target) {
        if (target == null) {
            return 0.0;
        }
        try {
            org.bukkit.attribute.AttributeInstance attribute =
                target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            return attribute == null ? 0.0 : Math.max(0.0, attribute.getValue());
        } catch (RuntimeException | NoSuchFieldError | NoClassDefFoundError ignored) {
            return 0.0;
        }
    }

    public void dealSpellDamage(LivingEntity target, double spellBase) {
        dealSpellDamage(target, spellBase, null);
    }

    /**
     * {@link #dealSpellDamage(LivingEntity, double)} のグリフID付き版（2026-07-31 G5）。
     *
     * <p>ダメージ系エフェクトは必ずこちらを使い、自分のグリフID（{@code getId().getKey()}）を渡すこと。
     * TF の {@code glyph_damage_multiplier_bonus}（スキルツリー「害悪強化」等）は
     * {@code stats/glyph-damage-boost.yml} に列挙されたグリフにだけ乗る設計で、
     * <b>ここでIDを渡さないと該当グリフでも倍率が無言で乗らない</b>（lore には出るのに効かない状態に戻る）。
     *
     * <p>増幅(Amplify)の乗算ボーナス（2026-08-02）は既定で適用される
     * （{@link #dealSpellDamage(LivingEntity, double, String, boolean)} の3引数省略形）。
     *
     * @param glyphId ダメージを出したグリフのID。不明なら {@code null}（倍率なし）
     */
    public void dealSpellDamage(LivingEntity target, double spellBase, String glyphId) {
        dealSpellDamage(target, spellBase, glyphId, true);
    }

    /**
     * {@link #dealSpellDamage(LivingEntity, double, String)} に、増幅(Amplify)の乗算ボーナスを
     * このグリフ呼び出しへ適用するかどうかを明示できる版（2026-08-02）。
     *
     * <p><b>増幅グリフの仕様変更</b>: 旧仕様は各ダメージ系エフェクト（{@code HarmEffect} 等）が
     * 「増幅1段+3.0HP」のように {@code spellBase} 自身へ固定値を加算していた。杖の攻撃力
     * (attack-power)は本メソッドの先で {@link com.arspaper.integration.MagicStatSourcePolicy#effectiveBase}
     * が別途加算するため、触媒(杖)が育つほど固定加算の寄与が相対的に無意味化していた。現在は
     * {@code applyAmplifyDamageMultiplier=true} のとき、この呼び出し時点の
     * {@link #getAmplifyLevel()}（=increment済みの増幅段数）を Sharpness と同じ乗算方式
     * （既定1段+10%、{@code glyphs.yml} の {@code amplify.params.damage-rate-per-stack}）で
     * 「グリフ基礎＋杖の攻撃力」の合計へ掛ける（実装は
     * {@link com.arspaper.integration.TrinityForgeBridge#magicalFinalDamage(java.util.UUID,
     * LivingEntity, double, org.bukkit.inventory.ItemStack, org.bukkit.inventory.ItemStack, String, int)}）。
     *
     * <p><b>{@code false} を渡すべき場合</b>: 呼び出し側が {@code spellBase} へ既に増幅段数を
     * 自前で織り込み済みのとき（例: {@code HealEffect} の対アンデッド分岐は heal 用の
     * {@code amplify-bonus} を既に加算済み）。ここで {@code true} のままだと二重計上になる。
     *
     * @param glyphId                     ダメージを出したグリフのID。不明なら {@code null}（倍率なし）
     * @param applyAmplifyDamageMultiplier 増幅の乗算ボーナスをこの呼び出しに適用するか
     */
    public void dealSpellDamage(LivingEntity target, double spellBase, String glyphId,
                                 boolean applyAmplifyDamageMultiplier) {
        Player caster = getCaster();
        if (caster == null || target == null || spellBase <= 0) {
            return;
        }
        int amplifyForDamage = applyAmplifyDamageMultiplier ? amplifyLevel : 0;
        // 杖/触媒の攻撃力・会心/貫通を対称パイプラインへ連携する。
        // ステ供給元が特定できない（儀式/タレット等の非プレイヤー詠唱）場合は plain(0) フォールバック。
        double finalDamage = com.arspaper.integration.TrinityForgeBridge
            .magicalFinalDamage(casterUuid, target, spellBase, catalyst, castItem, glyphId, amplifyForDamage);
        // 伝播チェーン先の威力減衰(2026-08-19)。【最終ダメージ】に掛ける ── spellBase 側に掛けると
        // TF の守備力が引き算で効くため、0.4 倍のつもりが min-component-damage(1) まで落ちて
        // 減衰率と実ダメージが桁で食い違う。回復(負値)にも同じ倍率を掛けて向きを揃える。
        if (propagateDamageRate != 1.0) {
            finalDamage *= propagateDamageRate;
        }
        // #6: 負の最終魔法ダメージは対象を回復させる(TF物理側 CombatListener と対称。負クランプ設定時のみ発生)。
        // 0 は何もしない。正のときのみ MAGIC ダメージソースで適用する。
        if (finalDamage < 0) {
            com.arspaper.integration.TrinityForgeBridge.healEntity(target, -finalDamage);
            return;
        }
        if (finalDamage <= 0) {
            return;
        }
        com.arspaper.integration.TrinityForgeBridge.applyMagicDamage(target, caster, finalDamage);
    }

    /**
     * スペルダメージを攻撃力上昇/弱体化/耐性で補正して返す。
     * 1レベルにつき10%の乗算。
     *
     * @param baseDamage 基本ダメージ（Amplify等で計算済み）
     * @param target ダメージ対象
     * @return 補正後ダメージ（最低0）
     * @deprecated 魔法ダメージは {@link #dealSpellDamage(LivingEntity, double)} 経由で
     *             TrinityForge 対称パイプラインに供給すること。本メソッドは旧式の自前補正で、
     *             対称パイプラインを通らないため新規利用は禁止。
     */
    @Deprecated
    public double calculateSpellDamage(double baseDamage, LivingEntity target) {
        double damage = baseDamage;
        Player caster = getCaster();

        // キャスターの攻撃力上昇: +10%/lv (乗算)
        if (caster != null) {
            org.bukkit.potion.PotionEffect strength = caster.getPotionEffect(
                org.bukkit.potion.PotionEffectType.STRENGTH);
            if (strength != null) {
                damage *= 1.0 + (strength.getAmplifier() + 1) * 0.1;
            }
            // キャスターの弱体化: -10%/lv (乗算)
            org.bukkit.potion.PotionEffect weakness = caster.getPotionEffect(
                org.bukkit.potion.PotionEffectType.WEAKNESS);
            if (weakness != null) {
                damage *= Math.max(0, 1.0 - (weakness.getAmplifier() + 1) * 0.1);
            }
        }

        // ターゲットの耐性: -10%/lv (乗算)
        if (target != null) {
            org.bukkit.potion.PotionEffect resistance = target.getPotionEffect(
                org.bukkit.potion.PotionEffectType.RESISTANCE);
            if (resistance != null) {
                damage *= Math.max(0, 1.0 - (resistance.getAmplifier() + 1) * 0.1);
            }
        }

        return Math.max(0, damage);
    }

    /**
     * @deprecated spell_power スレッド削除済み。常に1.0を返す。
     */
    @Deprecated
    public double getSpellPowerMultiplier() {
        return 1.0;
    }

    // === Acceleration ===
    public double getAcceleration() { return acceleration; }
    public void setAcceleration(double acceleration) { this.acceleration = acceleration; }

    // === Pierce ===
    public int getPierceCount() { return pierceCount; }
    public void setPierceCount(int pierceCount) { this.pierceCount = pierceCount; }

    // === Projectile Speed (Form augment) ===
    public double getProjectileSpeedMultiplier() { return projectileSpeedMultiplier; }
    public void setProjectileSpeedMultiplier(double v) { this.projectileSpeedMultiplier = v; }

    // === Split ===
    public int getSplitCount() { return splitCount; }
    public void setSplitCount(int splitCount) { this.splitCount = splitCount; }

    // === Sensitive ===
    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }

    // === Randomize ===
    public boolean isRandomizing() { return randomizing; }
    public void setRandomizing(boolean randomizing) { this.randomizing = randomizing; }

    // === Extract ===
    public int getExtractCount() { return extractCount; }
    public void setExtractCount(int extractCount) { this.extractCount = extractCount; }

    // === Fortune ===
    public int getFortuneLevel() { return fortuneLevel; }
    public void setFortuneLevel(int fortuneLevel) { this.fortuneLevel = fortuneLevel; }

    // === Wall Pattern ===
    public boolean isWallPattern() { return wallPattern; }
    public void setWallPattern(boolean wallPattern) { this.wallPattern = wallPattern; }

    // === Linger Pattern ===
    public boolean isLingerPattern() { return lingerLevel > 0; }
    public int getLingerLevel() { return lingerLevel; }
    public void setLingerLevel(int level) { this.lingerLevel = level; }
    /** 後方互換: booleanセッター */
    public void setLingerPattern(boolean v) { if (v && lingerLevel == 0) lingerLevel = 1; }

    // === Delay ===
    public int getDelayTicks() { return delayTicks; }
    public void setDelayTicks(int delayTicks) { this.delayTicks = delayTicks; }

    // === Trail ===
    public int getRapidFireLevel() { return rapidFireLevel; }
    public void setRapidFireLevel(int rapidFireLevel) { this.rapidFireLevel = rapidFireLevel; }

    public boolean isTraceActive() { return traceActive; }
    public void setTraceActive(boolean traceActive) { this.traceActive = traceActive; }

    public int getPropagateChainCount() { return propagateChainCount; }
    public void setPropagateChainCount(int count) { this.propagateChainCount = count; }
    public void addPropagateChain() { this.propagateChainCount += 3; }
    public boolean isInPropagateChain() { return inPropagateChain; }
    public void setInPropagateChain(boolean v) { this.inPropagateChain = v; }

    /** 二次発動（伝播/軌跡経由）かどうか。パーティクル削減に使用。 */
    public boolean isSecondaryInvocation() { return secondaryInvocation; }
    public void setSecondaryInvocation(boolean v) { this.secondaryInvocation = v; }

    public int getReachLevel() { return reachLevel; }
    public void setReachLevel(int reachLevel) { this.reachLevel = reachLevel; }

    /**
     * コンテキストコピー（Split等で独立コンテキストが必要な場合）。
     */
    public SpellContext copy() {
        SpellContext copy = new SpellContext(this);
        copy.amplifyLevel = this.amplifyLevel;
        copy.aoeLevel = this.aoeLevel;
        copy.aoeHeightLevel = this.aoeHeightLevel;
        copy.aoeVerticalLevel = this.aoeVerticalLevel;
        copy.aoeRadiusLevel = this.aoeRadiusLevel;
        copy.traceActive = this.traceActive;
        copy.propagateChainCount = this.propagateChainCount;
        copy.inPropagateChain = this.inPropagateChain;
        copy.reachLevel = this.reachLevel;
        copy.hitFace = this.hitFace;
        copy.durationLevel = this.durationLevel;
        copy.acceleration = this.acceleration;
        copy.pierceCount = this.pierceCount;
        copy.projectileSpeedMultiplier = this.projectileSpeedMultiplier;
        copy.splitCount = this.splitCount;
        copy.sensitive = this.sensitive;
        copy.randomizing = this.randomizing;
        copy.extractCount = this.extractCount;
        copy.fortuneLevel = this.fortuneLevel;
        copy.wallPattern = this.wallPattern;
        copy.lingerLevel = this.lingerLevel;
        copy.delayTicks = this.delayTicks;
        copy.rapidFireLevel = this.rapidFireLevel;
        copy.dampenAccum = this.dampenAccum;
        copy.durationDownAccum = this.durationDownAccum;
        copy.secondaryInvocation = this.secondaryInvocation;
        copy.propagateDamageRate = this.propagateDamageRate;
        return copy;
    }

    /**
     * Formに影響するAugment（Accelerate, Split, Pierce等）を先行適用する。
     */
    public void applyFormAugments() {
        boolean pastForm = false;
        for (SpellComponent comp : components) {
            if (comp instanceof SpellForm) {
                pastForm = true;
                continue;
            }
            if (!pastForm) continue;
            if (comp instanceof SpellEffect) break;
            if (comp instanceof SpellAugment augment) {
                augment.modify(this);
            }
        }
    }

    private static final double MAX_AOE_RADIUS = 10.0;

    /**
     * EffectGroupを構築する。
     * Ars Nouveau準拠: Effectの後ろに続くAugmentがそのEffectを強化する。
     */
    private List<EffectGroup> buildEffectGroups() {
        List<EffectGroup> groups = new ArrayList<>();
        boolean pastForm = false;

        for (SpellComponent comp : components) {
            if (comp instanceof SpellForm) {
                pastForm = true;
                continue;
            }
            if (!pastForm) continue;

            if (comp instanceof SpellEffect effect) {
                groups.add(new EffectGroup(effect));
            } else if (comp instanceof SpellAugment augment) {
                if (!groups.isEmpty()) {
                    EffectGroup currentGroup = groups.get(groups.size() - 1);
                    // 互換性チェック: GlyphConfigで定義されたaugmentsに含まれるかどうか
                    String effectKey = currentGroup.effect.getId().getKey();
                    String augmentKey = augment.getId().getKey();
                    String baseKey = GlyphConfig.stripSuperPrefix(augmentKey);
                    GlyphConfig glyphConfig = ArsPaper.getInstance().getGlyphConfig();
                    if (glyphConfig.isAugmentCompatible(effectKey, augmentKey)) {
                        // 最大スタック数チェック（超増強は2個分としてカウント）
                        int maxStack = glyphConfig.getMaxAugmentStack(effectKey, augmentKey);
                        int currentCount = getEffectiveAugmentCount(currentGroup.augments, baseKey);
                        int addWeight = augment instanceof com.arspaper.spell.augment.SuperAugment ? 2 : 1;
                        if (currentCount + addWeight <= maxStack) {
                            currentGroup.augments.add(augment);
                        }
                    }
                }
            }
        }
        return groups;
    }

    /**
     * ベースキーに対する実効スタック数を計算する。
     * 超増強（SuperAugment）は2個分としてカウントされる。
     */
    private static int getEffectiveAugmentCount(java.util.List<SpellAugment> augments, String baseKey) {
        int count = 0;
        for (SpellAugment a : augments) {
            String key = a.getId().getKey();
            String aBase = GlyphConfig.stripSuperPrefix(key);
            if (aBase.equals(baseKey)) {
                count += (a instanceof com.arspaper.spell.augment.SuperAugment) ? 2 : 1;
            }
        }
        return count;
    }

    /**
     * ヒット対象にEffectチェーンを実行する（エンティティ対象、AOE拡張なし）。
     * Form側が独自にエンティティスキャンを行う場合に使用する。
     * resolveOnEntity()との二重AOEを防止する。
     */
    public void resolveOnEntityNoAoe(LivingEntity target) {
        Player caster = getCaster();
        if (caster == null) return;
        markCastHit(target);

        List<EffectGroup> groups = buildEffectGroups();
        resolveGroupsOnEntityNoAoe(groups, 0, target);
    }

    private void resolveGroupsOnEntityNoAoe(List<EffectGroup> groups, int startIndex, LivingEntity target) {
        for (int i = startIndex; i < groups.size(); i++) {
            EffectGroup group = groups.get(i);
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }

            // Delay増強
            if (delayTicks > 0) {
                int delay = delayTicks;
                group.effect.applyToEntity(this, target);
                final int nextIndex = i + 1;
                final SpellContext ctx = this;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ctx.resolveGroupsOnEntityNoAoe(groups, nextIndex, target);
                    }
                }.runTaskLater(ArsPaper.getInstance(), delay);
                return;
            }

            // Rune: エンティティの足元にルーン設置（後続エフェクトを引き渡す）
            if (group.effect instanceof RuneEffect runeEffect) {
                List<SpellEffect> effects = new ArrayList<>();
                List<List<SpellAugment>> augmentLists = new ArrayList<>();
                for (int j = i + 1; j < groups.size(); j++) {
                    effects.add(groups.get(j).effect);
                    augmentLists.add(new ArrayList<>(groups.get(j).augments));
                }
                runeEffect.placeRuneWithEffects(this, target.getLocation(), effects, augmentLists);
                return;
            }

            // Linger増強: 後続グループをゾーンで定期適用（Rune以外の効果用）
            if (lingerLevel > 0) {
                group.effect.applyToEntity(this, target);
                startLingerZoneFromAugment(groups, i + 1, target.getLocation());
                return;
            }

            group.effect.applyToEntity(this, target);
        }
        resetAugmentState();
    }

    /**
     * ヒット対象にEffectチェーンを実行する（エンティティ対象）。
     */
    public void resolveOnEntity(LivingEntity target) {
        Player caster = getCaster();
        if (caster == null) return;
        markCastHit(target);

        // PVPチェックはisValidAoeTarget()とEntityDamageEvent(WorldGuard等)で処理。
        // ここではブロックしない（回復スペルが味方に効かなくなるため）。

        List<EffectGroup> groups = buildEffectGroups();
        resolveGroupsOnEntity(groups, 0, target);
    }

    private void resolveGroupsOnEntity(List<EffectGroup> groups, int startIndex, LivingEntity target) {
        Player caster = getCaster();
        if (caster == null) return;

        // Form-level delay: Formに付けたdelay増強で最初のエフェクト発動を遅延
        if (startIndex == 0 && delayTicks > 0) {
            int formDelay = delayTicks;
            delayTicks = 0; // consume the form delay
            final SpellContext ctx = this;
            new BukkitRunnable() {
                @Override
                public void run() {
                    ctx.resolveGroupsOnEntity(groups, 0, target);
                }
            }.runTaskLater(ArsPaper.getInstance(), formDelay);
            return;
        }

        // 自己形態（target == caster）の場合、残留/伝播は無意味なので無効化
        boolean isSelfTarget = target.equals(caster);

        for (int i = startIndex; i < groups.size(); i++) {
            EffectGroup group = groups.get(i);
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }

            // 自己形態では残留/伝播を無効化
            if (isSelfTarget) {
                lingerLevel = 0;
                propagateChainCount = 0;
            }

            // Delay増強: 後続グループを遅延実行
            if (delayTicks > 0) {
                int delay = delayTicks;
                group.effect.applyToEntity(this, target);
                final int nextIndex = i + 1;
                final SpellContext ctx = this;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ctx.resolveGroupsOnEntity(groups, nextIndex, target);
                    }
                }.runTaskLater(ArsPaper.getInstance(), delay);
                return;
            }

            // Rune: エンティティの足元にルーン設置（後続エフェクトを引き渡す）
            if (group.effect instanceof RuneEffect runeEffect) {
                List<SpellEffect> effects = new ArrayList<>();
                List<List<SpellAugment>> augmentLists = new ArrayList<>();
                for (int j = i + 1; j < groups.size(); j++) {
                    effects.add(groups.get(j).effect);
                    augmentLists.add(new ArrayList<>(groups.get(j).augments));
                }
                runeEffect.placeRuneWithEffects(this, target.getLocation(), effects, augmentLists);
                return;
            }

            // Linger増強: 後続グループをゾーンで定期適用（Rune以外の効果用）
            if (lingerLevel > 0) {
                group.effect.applyToEntity(this, target);
                startLingerZoneFromAugment(groups, i + 1, target.getLocation());
                return;
            }

            group.effect.applyToEntity(this, target);

            // エンティティAOE展開（半径増加ベース）
            if (!group.effect.handlesAoeInternally()) {
                double radius = Math.min(aoeRadiusLevel, MAX_AOE_RADIUS);
                if (radius > 0) {
                    Location center = target.getLocation();
                    center.getNearbyLivingEntities(radius).stream()
                        .filter(e -> !e.equals(target) && !e.equals(caster))
                        .filter(e -> isValidAoeTarget(e, caster))
                        .forEach(e -> {
                            markCastHit(e);
                            group.effect.applyToEntity(this, e);
                        });
                }
            }

            // === 伝播チェーン: ヒット地点の周囲の【この詠唱でまだ当てていない】敵へ連鎖 ===
            if (propagateChainCount > 0 && !inPropagateChain) {
                schedulePropagateChain(target.getLocation(), group.effect, caster);
            }
        }
        resetAugmentState();
    }

    /**
     * この詠唱で「もう当てた」ことにする。以後、伝播チェーンの候補から外れる。
     *
     * <p>炸裂のように<b>フォーム側が複数の対象を自前で拾う</b>場合は、1体目を resolve する前に
     * {@link #markCastHits} で対象全員を登録しておくこと。登録が後になると、1体目のチェーンが
     * 「これから直撃させる相手」を選んでしまい、バニラの無敵時間に吸われてチェーン枠を捨てる。
     */
    public void markCastHit(LivingEntity entity) {
        if (entity != null) {
            castState.hitEntities.add(entity.getUniqueId());
        }
    }

    /** {@link #markCastHit} の一括版。 */
    public void markCastHits(java.util.Collection<? extends LivingEntity> entities) {
        if (entities == null) return;
        for (LivingEntity e : entities) {
            markCastHit(e);
        }
    }

    /**
     * {@code origin} を起点に伝播チェーンを張る。
     *
     * <p>候補は「この詠唱でまだ当てていない」エンティティだけ。近い順に取り、1 ホップごとに
     * {@code damage-falloff-per-hop} ずつ威力を落とす（{@code min-damage-rate} で下げ止まり）。
     * 選んだ時点で即ヒット済みへ登録するので、同じ詠唱の別の対象から張られたチェーンと衝突しない。
     *
     * <p><b>上限が要る理由</b>: 除外して探し直す方式は、密集地では候補が尽きるまで外へ伸びる。
     * 対象ごとに {@code getNearbyLivingEntities} を呼ぶので、TT やスポナー前では
     * 対象数 × 検索回数が跳ね上がる。{@code max-chains-per-cast} は詠唱 1 回の総チェーン数の天井。
     */
    private void schedulePropagateChain(Location origin, SpellEffect chainEffect, Player caster) {
        if (origin == null || chainEffect == null) return;
        GlyphConfig cfg = ArsPaper.getInstance().getGlyphConfig();
        double chainRadius = cfg.getParam("propagate", "chain-radius", 8.0);
        int maxPerCast = (int) cfg.getParam("propagate", "max-chains-per-cast", 20.0);
        int budget = SpellPropagateMath.budget(propagateChainCount, maxPerCast, castState.chainsUsed);
        if (budget <= 0 || chainRadius <= 0) return;

        final Location originSnapshot = origin.clone();
        java.util.List<LivingEntity> chainTargets = originSnapshot.getNearbyLivingEntities(chainRadius).stream()
            .filter(e -> !e.equals(caster))
            .filter(e -> !castState.hitEntities.contains(e.getUniqueId()))
            .filter(e -> isValidAoeTarget(e, caster))
            .sorted(java.util.Comparator.comparingDouble(
                e -> e.getLocation().distanceSquared(originSnapshot)))
            .limit(budget)
            .toList();
        if (chainTargets.isEmpty()) return;

        double falloff = cfg.getParam("propagate", "damage-falloff-per-hop", 0.15);
        double minRate = cfg.getParam("propagate", "min-damage-rate", 0.4);
        final Location fxOrigin = originSnapshot.clone().add(0, 1, 0);

        // 遅延実行: 各チェーン対象を2tick間隔でずらしてスパイク軽減
        for (int ci = 0; ci < chainTargets.size(); ci++) {
            final LivingEntity chainTarget = chainTargets.get(ci);
            // 【選んだ時点で登録する】。適用は遅延なので、ここで入れないと同じ詠唱の
            // 別の対象から張られたチェーンが同じ相手を二重に選ぶ。
            markCastHit(chainTarget);
            castState.chainsUsed++;
            final int delay = (ci + 1) * 2; // 2, 4, 6, ... tick後
            final double hopRate = SpellPropagateMath.hopRate(ci, falloff, minRate);
            final double chainRate = this.propagateDamageRate * hopRate;
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (chainTarget.isDead() || !chainTarget.isValid()) return;
                    SpellContext chainCtx = SpellContext.this.copy();
                    chainCtx.setInPropagateChain(true);
                    chainCtx.setSecondaryInvocation(true); // パーティクル削減
                    chainCtx.propagateDamageRate = chainRate;
                    chainEffect.applyToEntity(chainCtx, chainTarget);
                    SpellFxUtil.spawnChainFx(fxOrigin, chainTarget.getLocation().add(0, 1, 0));
                }
            }.runTaskLater(ArsPaper.getInstance(), delay);
        }
    }

    /**
     * <b>エンティティに当たらなかった炸裂</b>から伝播チェーンを張る（2026-08-19）。
     *
     * <p>伝播は本来「エンティティヒット」が起点なので、空中で信管が切れて誰にも当たらなかった
     * 炸裂では<b>一度も発動しなかった</b>。炸裂地点そのものを起点にできると「外しても芋づる式に当たる」が
     * 成立し、これが炸裂に伝播を合わせる固有の利点になる（投射は外した弾から連鎖しない）。
     *
     * <p>直撃対象が 1 体でもあるときは呼ばないこと ── その対象から通常どおりチェーンが張られるので、
     * ここから重ねると同じ詠唱で二重に枠を使う（ヒット済み集合のおかげでダメージは重複しないが、
     * {@code max-chains-per-cast} を無駄に消費する）。
     */
    public void resolvePropagateFromLocation(Location origin) {
        Player caster = getCaster();
        if (caster == null || origin == null) return;
        for (EffectGroup group : buildEffectGroups()) {
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }
            if (propagateChainCount > 0 && !inPropagateChain) {
                schedulePropagateChain(origin, group.effect, caster);
            }
        }
        resetAugmentState();
    }

    /**
     * ヒット対象にEffectチェーンを実行する（ブロック対象、AOE拡張なし）。
     * WallForm等、Form側が独自にブロック走査を行う場合に使用。
     */
    public void resolveOnBlockNoAoe(Location blockLocation) {
        Player caster = getCaster();
        if (caster == null) return;

        List<EffectGroup> groups = buildEffectGroups();
        for (EffectGroup group : groups) {
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }
            group.effect.applyToBlock(this, blockLocation);
        }
        resetAugmentState();
    }

    /**
     * 軌跡モード用: 飛行経路上のブロックにEffectチェーンを実行する。
     * allowsTraceRepeating() == false のエフェクトはスキップする。
     */
    public void resolveOnBlockTrace(Location blockLocation) {
        Player caster = getCaster();
        if (caster == null) return;

        this.secondaryInvocation = true; // 軌跡経由 → パーティクル削減

        List<EffectGroup> groups = buildEffectGroups();
        for (EffectGroup group : groups) {
            if (!group.effect.allowsTraceRepeating()) continue;
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }
            group.effect.applyToBlock(this, blockLocation);
        }
        resetAugmentState();
    }

    /**
     * ヒット対象にEffectチェーンを実行する（ブロック対象）。
     */
    public void resolveOnBlock(Location blockLocation) {
        Player caster = getCaster();
        if (caster == null) return;

        List<EffectGroup> groups = buildEffectGroups();
        resolveGroupsOnBlock(groups, 0, blockLocation);
    }

    private void resolveGroupsOnBlock(List<EffectGroup> groups, int startIndex, Location blockLocation) {
        Player caster = getCaster();
        if (caster == null) return;

        // Form-level delay: Formに付けたdelay増強で最初のエフェクト発動を遅延
        if (startIndex == 0 && delayTicks > 0) {
            int formDelay = delayTicks;
            delayTicks = 0; // consume the form delay
            final SpellContext ctx = this;
            new BukkitRunnable() {
                @Override
                public void run() {
                    ctx.resolveGroupsOnBlock(groups, 0, blockLocation);
                }
            }.runTaskLater(ArsPaper.getInstance(), formDelay);
            return;
        }

        for (int i = startIndex; i < groups.size(); i++) {
            EffectGroup group = groups.get(i);
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }

            // Delay増強: 後続グループを遅延実行
            if (delayTicks > 0) {
                int delay = delayTicks;
                group.effect.applyToBlock(this, blockLocation);
                final int nextIndex = i + 1;
                final SpellContext ctx = this;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ctx.resolveGroupsOnBlock(groups, nextIndex, blockLocation);
                    }
                }.runTaskLater(ArsPaper.getInstance(), delay);
                return;
            }

            // Rune: 後続グループをルーントリガー時に遅延実行（設置時は発動しない）
            // ※ Rune+Linger = ルーン持続化はRuneEffect内部で処理するため、Runeを先に判定
            if (group.effect instanceof RuneEffect runeEffect) {
                List<SpellEffect> effects = new ArrayList<>();
                List<List<SpellAugment>> augmentLists = new ArrayList<>();
                for (int j = i + 1; j < groups.size(); j++) {
                    effects.add(groups.get(j).effect);
                    augmentLists.add(new ArrayList<>(groups.get(j).augments));
                }
                runeEffect.placeRuneWithEffects(this, blockLocation, effects, augmentLists);
                return;
            }

            // 設置系エフェクト: ソリッドブロックに着弾した場合、ヒット面の隣接空気ブロックに変換
            Location effectBlockLoc = blockLocation;
            if (group.effect.getAoeMode() == SpellEffect.AoeMode.HIT_FACE_OUTWARD
                    && hitFace != null
                    && !blockLocation.getBlock().getType().isAir()) {
                Location adjacent = blockLocation.getBlock()
                    .getRelative(hitFace).getLocation();
                if (adjacent.getBlock().getType().isAir()) {
                    effectBlockLoc = adjacent;
                }
            }

            // Linger増強: 後続グループをゾーンで定期適用（Rune以外の効果用）
            if (lingerLevel > 0) {
                group.effect.applyToBlock(this, effectBlockLoc);
                startLingerZoneFromAugment(groups, i + 1, effectBlockLoc.clone().add(0.5, 0.5, 0.5));
                return;
            }

            group.effect.applyToBlock(this, effectBlockLoc);

            // === AOE展開（新3軸: 幅/上下/奥行き + 壁グリフ） ===
            if (!group.effect.handlesAoeInternally()) {
                int width = (int) Math.min(aoeLevel, MAX_AOE_RADIUS);       // 幅
                int height = (int) Math.min(aoeHeightLevel, MAX_AOE_RADIUS); // 上下
                int depth = (int) Math.min(aoeVerticalLevel, MAX_AOE_RADIUS); // 奥行き

                if (width > 0 || height > 0 || depth > 0) {
                    boolean inward = group.effect.getAoeMode() == SpellEffect.AoeMode.HIT_FACE_INWARD;
                    Location aoeLoc = inward ? blockLocation : effectBlockLoc;

                    // ヒット面から座標系を決定
                    org.bukkit.block.BlockFace face = hitFace;
                    if (face == null) {
                        face = org.bukkit.block.BlockFace.UP; // デフォルト=床
                    }

                    int ny = Math.abs(face.getDirection().getBlockY());
                    int nx = face.getDirection().getBlockX();
                    int nz = face.getDirection().getBlockZ();

                    // 法線方向の符号（破壊=奥へ、設置=手前へ）
                    int depthSign = inward ? -1 : 1;

                    if (ny > 0) {
                        // --- 床/天井ヒット ---
                        org.bukkit.util.Vector lookH = caster.getLocation().getDirection().setY(0);
                        if (lookH.lengthSquared() < 0.01) lookH = new org.bukkit.util.Vector(0, 0, 1);
                        boolean fwdZ = Math.abs(lookH.getZ()) >= Math.abs(lookH.getX());
                        int fX = fwdZ ? 0 : (lookH.getX() > 0 ? 1 : -1);
                        int fZ = fwdZ ? (lookH.getZ() > 0 ? 1 : -1) : 0;
                        int rX = fwdZ ? 1 : 0;
                        int rZ = fwdZ ? 0 : 1;
                        int dY = depthSign * face.getDirection().getBlockY();

                        for (int w = -width; w <= width; w++) {
                            for (int h = -height; h <= height; h++) {
                                for (int d = 0; d <= depth; d++) {
                                    if (w == 0 && h == 0 && d == 0) continue; // 中心は既に処理済
                                    int dx = rX * w + fX * h;
                                    int dz = rZ * w + fZ * h;
                                    int dy = dY * d;
                                    group.effect.applyToBlock(this,
                                        aoeLoc.clone().add(dx, dy, dz));
                                }
                            }
                        }
                    } else {
                        // --- 壁ヒット ---
                        // 幅=左右、上下=Y方向、奥行き=法線方向
                        boolean isXFace = Math.abs(nx) > 0;

                        for (int w = -width; w <= width; w++) {
                            for (int h = -height; h <= height; h++) {
                                for (int d = 0; d <= depth; d++) {
                                    if (w == 0 && h == 0 && d == 0) continue;
                                    int ddx = isXFace ? nx * d * depthSign : w;
                                    int ddy = h;
                                    int ddz = isXFace ? w : nz * d * depthSign;
                                    group.effect.applyToBlock(this,
                                        aoeLoc.clone().add(ddx, ddy, ddz));
                                }
                            }
                        }
                    }
                }
            }
        }
        resetAugmentState();
    }

    /**
     * LingerAugmentからゾーンを開始する。後続グループを定期的に適用する。
     */
    private void startLingerZoneFromAugment(List<EffectGroup> groups, int startIndex, Location center) {
        List<SpellEffect> effects = new ArrayList<>();
        List<List<SpellAugment>> augmentLists = new ArrayList<>();
        for (int j = startIndex; j < groups.size(); j++) {
            effects.add(groups.get(j).effect);
            augmentLists.add(new ArrayList<>(groups.get(j).augments));
        }
        if (!effects.isEmpty()) {
            LingerEffect.startZoneStatic(ArsPaper.getInstance(), this, center, effects, augmentLists);
        }
    }

    private static class EffectGroup {
        final SpellEffect effect;
        final List<SpellAugment> augments = new ArrayList<>();
        EffectGroup(SpellEffect effect) { this.effect = effect; }
    }

    /**
     * PVP対象として有効かチェックする。
     * ワールドPVPフラグ、config設定、WorldGuardリージョンフラグを確認。
     */
    private boolean isValidPvPTarget(Player target) {
        if (!target.getWorld().getPVP()) return false;
        boolean pvpEnabled = ArsPaper.getInstance().getConfig().getBoolean("pvp.enabled", true);
        if (!pvpEnabled) return false;
        return WorldGuardHelper.isPvPAllowed(target.getLocation());
    }

    public boolean isValidAoeTarget(LivingEntity entity, Player caster) {
        // PvP無効時はプレイヤーをスペル対象から除外
        if (entity instanceof Player targetPlayer && targetPlayer != caster) {
            if (!isValidPvPTarget(targetPlayer)) return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            if (caster.equals(tameable.getOwner())) return false;
        }
        return true;
    }

    /**
     * Augment状態をリセットする（公開版、LingerEffect等が使用）。
     */
    public void resetPublicAugmentState() {
        resetAugmentState();
    }

    /**
     * Effect-level Augment状態をリセットする。
     * Form-level値（pierce, split, projectileSpeed, acceleration）は
     * スペル全体のライフサイクルで維持するためリセットしない。
     */
    private void resetAugmentState() {
        amplifyLevel = 0;
        dampenAccum = 0;
        durationDownAccum = 0;
        aoeLevel = 0;
        aoeHeightLevel = 0;
        aoeVerticalLevel = 0;
        aoeRadiusLevel = 0;
        durationLevel = 0;
        // acceleration はForm-levelでも使用するためリセットしない
        // pierceCount はProjectileHitListenerが参照し続けるためリセットしない
        // projectileSpeedMultiplier はForm-levelのためリセットしない
        // splitCount はForm-levelのためリセットしない
        sensitive = false;
        randomizing = false;
        extractCount = 0;
        fortuneLevel = 0;
        wallPattern = false;
        lingerLevel = 0;
        delayTicks = 0;
        traceActive = false;
        propagateChainCount = 0;
        inPropagateChain = false;
        reachLevel = 0;
        // rapidFireLevel はForm-levelのためリセットしない
    }
}
