package com.arspaper.spell;

import com.arspaper.ArsPaper;
import com.arspaper.item.CatalystData;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.SpellBookTierData;
import com.arspaper.mana.ManaKeys;
import com.arspaper.mana.ManaManager;
import com.arspaper.spell.form.BeamForm;
import com.arspaper.world.WorldSettingsManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * スペルの発動を処理するエンジン。
 * マナチェック + クールダウン → Formの発動 → Effectチェーンの実行。
 */
public class SpellCaster {

    private static final long DEFAULT_COOLDOWN_MS = 500; // 0.5秒
    private static final long MIN_COOLDOWN_MS = 100;    // 最低CT
    private static final long CACHE_TTL_MS = 5000;      // グリフキャッシュ有効期間
    /** form非取得時に使う単一CTのformキー（従来挙動互換）。 */
    private static final String GLOBAL_FORM_KEY = "_global_";

    private final ManaManager manaManager;
    /** form別クールダウン。キー = playerUuid + ":" + formKey。 */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> glyphCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> glyphCacheExpiry = new ConcurrentHashMap<>();
    /** form別CT秒数(ms換算)。config.yml の form-cooldowns から設定駆動で読み込む。 */
    private volatile Map<String, Long> formCooldownMs = Map.of();
    /** 詠唱1回あたりの杖の耐久消費。config.yml の cast-durability から読み込む。 */
    private volatile CastDurabilityPolicy castDurability = CastDurabilityPolicy.DISABLED;
    /** 使用ゲート（perk所持→glyph使用許可）。 */
    private final UsageGate usageGate;

    public SpellCaster(ManaManager manaManager, UnlockedGlyphs unlockedGlyphs) {
        this.manaManager = manaManager;
        this.usageGate = new UsageGate(ArsPaper.getInstance(), unlockedGlyphs);
        reloadFormCooldowns();
    }

    /**
     * config.yml の form-cooldowns セクションを再読み込みする。
     * 秒数(>0)が設定されたformのみ採用し、ms換算で保持する。
     * 0/未定義は従来の計算CTにフォールバックする（cast内で解決）。
     */
    public void reloadFormCooldowns() {
        Map<String, Long> newMap = new HashMap<>();
        var section = ArsPaper.getInstance().getConfig()
            .getConfigurationSection("form-cooldowns");
        if (section != null) {
            for (String formKey : section.getKeys(false)) {
                double seconds = section.getDouble(formKey, 0);
                if (seconds > 0) {
                    newMap.put(formKey, (long) (seconds * 1000));
                }
            }
        }
        this.formCooldownMs = Map.copyOf(newMap);
        this.castDurability = CastDurabilityPolicy.from(
            ArsPaper.getInstance().getConfig().getConfigurationSection("cast-durability"));
    }

    /**
     * usage-gate.yml を再読み込みする。
     */
    public void reloadUsageGate() {
        usageGate.reload();
    }

    /**
     * 指定glyphの使用権限をプレイヤーが満たすか（使用ゲート判定）。
     * @param glyphKey NamespacedKey.getKey() の文字列
     */
    public boolean hasGlyphPermission(Player player, String glyphKey) {
        return usageGate.hasPermission(player, glyphKey);
    }

    /**
     * recipe内で使用権限を満たさない最初のglyphの表示名を返す。
     * 全て許可されていればnull。
     */
    public String firstMissingPerkGlyph(Player player, SpellRecipe recipe) {
        for (SpellComponent comp : recipe.getComponents()) {
            if (!usageGate.hasPermission(player, comp.getId().getKey())) {
                return GlyphNames.display(comp);
            }
        }
        return null;
    }

    /**
     * スペルを発動する。
     *
     * @param caster 術者
     * @param recipe スペル構成
     * @return 発動に成功したかどうか
     */
    public boolean cast(Player caster, SpellRecipe recipe) {
        return cast(caster, recipe, false, null);
    }

    /**
     * スペルを発動する（触媒付き）。触媒（ワンド/スペルブック）の会心/貫通を魔法ダメージへ連携する。
     *
     * @param caster   術者
     * @param recipe   スペル構成
     * @param catalyst 詠唱に使った触媒 ItemStack。特定不能なら {@code null}
     * @return 発動に成功したかどうか
     */
    public boolean cast(Player caster, SpellRecipe recipe, org.bukkit.inventory.ItemStack catalyst) {
        return cast(caster, recipe, false, catalyst);
    }

    /**
     * スペルを発動する。
     *
     * @param caster 術者
     * @param recipe スペル構成
     * @param sharedSpell 共有エンチャント付きの場合true（グリフチェックをスキップ）
     * @return 発動に成功したかどうか
     */
    public boolean cast(Player caster, SpellRecipe recipe, boolean sharedSpell) {
        return cast(caster, recipe, sharedSpell, null);
    }

    /**
     * スペルを発動する（触媒付き）。
     *
     * @param caster 術者
     * @param recipe スペル構成
     * @param sharedSpell 共有エンチャント付きの場合true（グリフチェックをスキップ）
     * @param catalyst 詠唱に使った触媒 ItemStack。特定不能なら {@code null}（従来どおり plain(0) フォールバック）
     * @return 発動に成功したかどうか
     */
    public boolean cast(Player caster, SpellRecipe recipe, boolean sharedSpell,
                        org.bukkit.inventory.ItemStack catalyst) {
        return cast(caster, recipe, sharedSpell, catalyst, null);
    }

    /**
     * スペルを発動する（触媒 + 詠唱に使った実アイテム付き / 2026-07-31 D6）。
     *
     * <p>{@code catalyst} と {@code castItem} を分ける理由: バインド詠唱では
     * 「手に持っているバインド済みアイテム」が {@code spellbooks.yml} の {@code catalysts:} 節に
     * 登録済みのときだけ触媒として扱われ、それ以外では触媒引数が<b>魔導書</b>になる。
     * TFカタログの杖11本は {@code catalysts:} に無いため、杖のステータス（攻撃力 10584 等）が
     * 完全に落ちていた。マナ削減 / CT / max-bind-tier は従来どおり {@code catalyst} が担い、
     * ステータス供給は {@code castItem} が担う。
     *
     * @param castItem 実際に右クリックして詠唱したアイテム（杖など）。特定不能なら {@code null}
     */
    public boolean cast(Player caster, SpellRecipe recipe, boolean sharedSpell,
                        org.bukkit.inventory.ItemStack catalyst,
                        org.bukkit.inventory.ItemStack castItem) {
        if (recipe == null || !recipe.isValid()) {
            caster.sendMessage(Component.text("無効なスペルです！", NamedTextColor.RED));
            return false;
        }

        // SOULBOUND / OWNER_BOUND: 非所有者は触媒・魔導書での詠唱不可。
        // 共有エンチャント付き魔導書(sharedSpell + share on catalyst)は例外。
        if (catalyst != null) {
            boolean shareExempt = sharedSpell
                && com.arspaper.enchant.ArsEnchantments.hasShareEnchant(catalyst);
            if (!shareExempt
                    && !com.arspaper.integration.TrinityForgeBridge.mayActorUseItem(caster, catalyst)) {
                caster.sendMessage(Component.text(
                    "このアイテムは所有者以外は使用できません。", NamedTextColor.RED));
                return false;
            }
        }

        // 触媒/魔導書の詠唱ゲート (use-requirements): use-skill / use-level 要件未達なら不発
        // (マナ消費前)。近接/弓/ツール/防具ゲートと同じ規則・文言をTF側で一元評価する。
        // TF未ロード/enforceオフ/要件なしは null(fail-open)。
        if (catalyst != null) {
            net.kyori.adventure.text.Component denial =
                com.arspaper.integration.TrinityForgeBridge.useRequirementDenial(caster, catalyst);
            if (denial != null) {
                caster.sendActionBar(denial);
                return false;
            }
        }

        // 2026-07-31 G10: バインド詠唱の使用条件ゲート。
        // SpellBindListener#onRightClick は既定優先度で PlayerInteractEvent を setCancelled(true) するため、
        // TF側 UseRequirementListener(HIGH, ignoreCancelled=true) が走らない。さらに上の catalyst 引数は
        // 非触媒バインドでは魔導書なので、杖自身の use-level-requirement / use-skill が
        // どの経路でも検査されず「Lv1でも infinity_cane(要Lv100)で撃てる」状態だった。
        // castItem(=実際に右クリックしたアイテム)を同じ TF ゲートへ通して塞ぐ。
        // catalyst と同一インスタンスのときは上で既に検査済みなので二重に出さない。
        if (castItem != null && castItem != catalyst) {
            net.kyori.adventure.text.Component castItemDenial =
                com.arspaper.integration.TrinityForgeBridge.useRequirementDenial(caster, castItem);
            if (castItemDenial != null) {
                caster.sendActionBar(castItemDenial);
                return false;
            }
        }

        // グリフ解放チェック: 共有エンチャント付きでない場合のみ
        if (!sharedSpell && !checkGlyphsUnlocked(caster, recipe)) {
            caster.sendMessage(Component.text("未解放のグリフが含まれています！", NamedTextColor.RED));
            return false;
        }

        // ワールド別スペルBANチェック
        String bannedName = checkSpellBanned(caster, recipe);
        if (bannedName != null) {
            caster.sendMessage(Component.text(
                bannedName + " はこのワールドでは使用禁止です！", NamedTextColor.RED));
            return false;
        }

        // 使用ゲート(β): perk未所持のglyphが含まれていれば不発（マナ消費前）。
        // 仕様(UNLOCK §2.2 Model Y)では glyph入手は自由・使用にperk必要。
        // 共有スペルでも使用者本人のperkゲートは常に効かせる（glyph解放チェックとは分離）。
        String missingGlyph = firstMissingPerkGlyph(caster, recipe);
        if (missingGlyph != null) {
            caster.sendMessage(Component.text(
                "このグリフを使う権限がありません: " + missingGlyph, NamedTextColor.RED));
            return false;
        }

        // 連射レベルを計算（Form直後のrapid_fire増強をカウント）
        int rapidFireLevel = 0;
        SpellForm form = recipe.getForm();
        // 照射形態は連射2個内蔵
        if (form != null && form.getId().getKey().equals("beam")) {
            rapidFireLevel = 2;
        }
        boolean pastForm = false;
        for (SpellComponent comp : recipe.getComponents()) {
            if (comp instanceof SpellForm) { pastForm = true; continue; }
            if (!pastForm) continue;
            if (comp instanceof SpellAugment && comp.getId().getKey().equals("rapid_fire")) {
                rapidFireLevel++;
            } else {
                break; // Form直後の連射増強のみカウント
            }
        }

        // クールダウン: 連射1つにつき-100ms（最低100ms）
        // 500 → 400 → 300 → 200 → 100
        long cooldownMs = Math.max(MIN_COOLDOWN_MS,
            DEFAULT_COOLDOWN_MS - rapidFireLevel * 100L);

        // form別CTを解決。form-cooldownsに秒数(>0)が設定されていればそれを使用、
        // 未設定/0なら上記の従来計算CTにフォールバックする。
        String formKey = (form != null) ? form.getId().getKey() : GLOBAL_FORM_KEY;
        long effectiveCooldownMs = resolveFormCooldownMs(formKey, cooldownMs);
        String cooldownKey = caster.getUniqueId() + ":" + formKey;

        long now = System.currentTimeMillis();
        Long lastCast = cooldowns.get(cooldownKey);
        if (lastCast != null && now - lastCast < effectiveCooldownMs) {
            return false;
        }

        // 触媒(catalysts.yml)/魔導書(spellbooks.yml)のCTオプション解決。
        // 触媒として登録済みのアイテムを優先し、そうでなければ魔導書アイテムのcooldownを見る
        // （両者は同一詠唱で二重適用しない: 触媒が解決できた場合は魔導書側を見ない）。
        CatalystData catalystData = resolveCatalystData(catalyst);
        SpellBookTierData bookTierData = (catalystData == null) ? resolveSpellBookTierData(catalyst) : null;

        // 触媒/魔導書のどちらかが「このアイテムのCTを既に持っている」かを判定する。
        // 持っていれば下の item-cooldown 汎用パス(genericItemCooldownSeconds)は一切見ない
        // ―― 触媒CT / 魔導書CT / item-cooldown の3経路は同一詠唱で重ねがけしない
        // (優先順位: 触媒 > 魔導書 > item-cooldown汎用)。
        // catalystOwnsCt は「catalysts.ymlのcooldown秒指定」と「catalyst自身が持つ
        // item-cooldownステ」のどちらかがあれば true(既存のownsCt判定を条件外へ出しただけで
        // 挙動は変えていない)。bookOwnsCt は spellbooks.yml の cooldown秒指定のみを見る
        // (現状 spell-books: は全ティア cooldown:0 なので実質常にfalseだが、将来値が入っても
        // 汎用パスと二重適用しないようにこのまま残す)。
        boolean catalystOwnsCt = catalystData != null
            && (catalystData.cooldownMs() > 0
                || com.arspaper.integration.TrinityForgeBridge.itemCooldownSeconds(catalyst) > 0.0);
        boolean bookOwnsCt = bookTierData != null && bookTierData.getCooldownMs() > 0;

        // 触媒のみ: アイテムCTゲージ(武器CT/詠唱CT)が残っている間は詠唱不可。
        // CT設定(item-cooldownステ or cooldownオプション)を持つ触媒だけをゲートし、
        // 同マテリアルのバニラ由来クールダウン(エンダーパール等)では誤ブロックしない
        // (CombatListener.meleeWeaponOnCooldown と同じ規則)。これにより近接命中で入ったCT中は
        // 詠唱も塞がれ、詠唱で入ったCT中の連続詠唱も塞がれる(攻撃側は既存のTFゲートが塞ぐ)。
        if (catalystData != null && catalyst != null && caster.getCooldown(catalyst) > 0 && catalystOwnsCt) {
            return false;
        }

        // 触媒別CT: form別CTとは別キー空間（"catalyst:" + id + ":" + uuid）でゲートする。
        String catalystCooldownKey = null;
        if (catalystData != null && catalystData.cooldownMs() > 0) {
            catalystCooldownKey = "catalyst:" + catalystData.id() + ":" + caster.getUniqueId();
            Long lastCatalystCast = cooldowns.get(catalystCooldownKey);
            if (lastCatalystCast != null && now - lastCatalystCast < catalystData.cooldownMs()) {
                return false;
            }
        }
        // 魔導書別CT: 同様に別キー空間（"book:" + id + ":" + uuid）でゲートする。
        String bookCooldownKey = null;
        if (bookTierData != null && bookTierData.getCooldownMs() > 0) {
            bookCooldownKey = "book:" + bookTierData.getItemId() + ":" + caster.getUniqueId();
            Long lastBookCast = cooldowns.get(bookCooldownKey);
            if (lastBookCast != null && now - lastBookCast < bookTierData.getCooldownMs()) {
                return false;
            }
        }

        // item-cooldown 汎用パス(2026-08-02): 触媒/魔導書のどちらもCTを持たない詠唱でも、
        // 「実際に右クリックして詠唱したアイテム」が item-stats.yml の item-cooldown ステを
        // 持っていればCTを効かせる。effectiveCastItem は castItem(バインド品/杖など、
        // catalyst引数と別物のケース)があればそれを、無ければ catalyst 自体(魔導書/触媒を
        // 素で右クリックしたケース)を使う。
        //
        // これが無いと何が起きるか: TFカタログの杖10本(BLAZE_ROD#400002〜400008/
        // 400012〜400014)は spellbooks.yml の catalysts: に未登録(catalystData==null)。
        // かつ SpellBindListener 経由のバインド詠唱では catalyst 引数が「バインド先の
        // 魔導書」に化け(D6)、その魔導書は bookTierData != null だが spell-books: の
        // cooldown は全ティア0(cooldownMs()==0)。つまり catalystOwnsCt / bookOwnsCt が
        // 両方false になり、item-stats.yml に設定した item-cooldown (3.0〜1.8秒)が
        // 一本も読まれずに無視されていた。
        double genericItemCooldownSeconds = 0.0;
        org.bukkit.inventory.ItemStack effectiveCastItem = (castItem != null) ? castItem : catalyst;
        if (!catalystOwnsCt && !bookOwnsCt && effectiveCastItem != null) {
            genericItemCooldownSeconds =
                com.arspaper.integration.TrinityForgeBridge.itemCooldownSeconds(effectiveCastItem);
            // 触媒ゲート(282行目)と同じ規則: item-cooldownステを実際に持つアイテムだけをゲートし、
            // 同マテリアルのバニラ由来クールダウン(エンダーパール等)では誤ブロックしない。
            if (genericItemCooldownSeconds > 0.0 && caster.getCooldown(effectiveCastItem) > 0) {
                return false;
            }
        }

        int baseCost = recipe.getTotalManaCost();
        // マナ消費量低下%の合算はManaManagerに集約（THREAD_COST_REDUCTION＋将来の装備由来削減）。
        // 触媒(catalysts.yml)のmana-cost-reductionは、実数減算(flat)を先に適用してから
        // 割合減算(percent、ManaManagerの装備由来%に加算合成)を掛ける。触媒でない経路は従来どおり不変。
        int costReductionPercent = manaManager.getCostReductionPercent(caster);
        int manaFlatReduction = 0;
        if (catalystData != null) {
            costReductionPercent = Math.min(100, costReductionPercent + catalystData.manaPercentReduction());
            manaFlatReduction = catalystData.manaFlatReduction();
            // P2-Java: 触媒のマナ削減はcatalysts.ymlのstats節(mana-cost-reduction-flat/-percent、
            // INTEGER item-stats)としてTrinityForgeへ動的登録され、品質/ランダムロール込みで解決される。
            // 旧来のmana-cost-reduction:{flat,percent}(CatalystData由来、上の2行)は後方互換のため
            // 合算を継続する — エディタの移行はstats書込み時に旧キーを削除するため、移行済み設定では
            // 二重計上にならない(未移行の設定はCatalystData側のみが値を持つ)。
            com.arspaper.integration.TrinityForgeBridge.CatalystManaReduction resolved =
                com.arspaper.integration.TrinityForgeBridge.resolveCatalystManaReduction(catalyst);
            costReductionPercent = Math.min(100, costReductionPercent + resolved.percent());
            manaFlatReduction += resolved.flat();
        }
        // 2026-07-26 マナ系ステ穴埋め(タスク5、オーケストレータ決定): パーク/役職/永続バフ/base-stats由来の
        // mana_cost_reduction_flat/-percent を、触媒(アイテム)由来の削減と加算合成する。
        // TF正準スケール(PercentStatNormalize.RATE_KEYS)では mana-cost-reduction-percent は割合[0,1]、
        // mana-cost-reduction-flat は整数のFLAT軽減量なので、percent側だけ%整数へ変換してから合算する。
        // 触媒(アイテム)側は上のcatalystData/resolveCatalystManaReductionが引き続き担当し、
        // tfNonItemStatTotalは装備を一切含まない非アイテム分だけを返すため二重計上しない。
        // clampReductionFraction で軽減率が[0,0.95]にクランプ済み(消費マナが負=回復化する事故を防止)、
        // 加えて下の Math.min(100, ...) が最終合成後にも同じ安全上限を再度効かせる。
        double nonItemPercentFraction = com.arspaper.integration.TrinityForgeBridge.clampReductionFraction(
                com.arspaper.integration.TrinityForgeBridge.tfNonItemStatTotal(
                        caster, "mana_cost_reduction_percent"));
        costReductionPercent = Math.min(100,
                costReductionPercent + (int) Math.round(nonItemPercentFraction * 100.0));
        manaFlatReduction += (int) Math.round(com.arspaper.integration.TrinityForgeBridge.tfNonItemStatTotal(
                caster, "mana_cost_reduction_flat"));
        int afterFlatReduction = Math.max(0, baseCost - manaFlatReduction);
        int cost = Math.max(1, afterFlatReduction - (int) Math.round(afterFlatReduction * costReductionPercent / 100.0));
        if (!manaManager.consumeMana(caster, cost)) {
            // マナ不足通知が無効化されていなければメッセージ表示
            int notifyOff = caster.getPersistentDataContainer()
                .getOrDefault(ManaKeys.MANA_NOTIFY_OFF, PersistentDataType.INTEGER, 0);
            if (notifyOff == 0) {
                caster.sendMessage(Component.text("マナが不足しています！", NamedTextColor.RED));
            }
            return false;
        }
        // 要件⑥ source-auto-consume: 上のconsumeManaがSource補填で不足分を賄った場合、
        // その補填量を記録しておく（キャンセル時のマナ返還からSource補填分を除外するため）。
        int sourceConvertedAmount = manaManager.getLastSourceConvertedAmount();

        cooldowns.put(cooldownKey, now);
        if (catalystCooldownKey != null) cooldowns.put(catalystCooldownKey, now);
        if (bookCooldownKey != null) cooldowns.put(bookCooldownKey, now);
        // 非発動（idle）回復ボーナス判定用に最終詠唱時刻を記録
        manaManager.touchCast(caster);

        SpellContext context = new SpellContext(caster, recipe, catalyst, castItem);
        context.applyFormAugments();
        SpellForm spellForm = recipe.getForm();
        spellForm.cast(caster, context);

        // エフェクトがキャンセルした場合、マナを返還
        if (context.isCancelled()) {
            // source-auto-consumeでSource補填された分(sourceConvertedAmount)はSourceへ戻す経路が
            // 用意されていないため、マナ返還からは除外する(=返還しない)。これにより
            // 「不足分だけSource補填→自己キャンセル→全額マナ返還」でSourceがマナへ実質変換され続ける
            // エクスプロイトを遮断する。正当な実消費マナ分(cost - sourceConvertedAmount)は従来どおり返還する。
            int manaRefund = cost - sourceConvertedAmount;
            if (manaRefund > 0) {
                manaManager.addMana(caster, manaRefund);
            }
            cooldowns.remove(cooldownKey);
            if (catalystCooldownKey != null) cooldowns.remove(catalystCooldownKey);
            if (bookCooldownKey != null) cooldowns.remove(bookCooldownKey);
            return false;
        }

        // M-5: 詠唱に使ったアイテム(杖)の耐久を消費する。キャンセル時はマナを返すのと同じ理屈で
        // 減らさないので、必ず上の isCancelled ブロックより後に置くこと。
        consumeCastDurability(caster, effectiveCastItem);

        // P9: 触媒詠唱成功時にアイテムクールダウンゲージ(武器CT相当)を表示する。触媒の実クールダウン
        // (catalystData.cooldownMs())をフォールバック秒として渡し、触媒の解決済みitem-cooldownステが
        // あればそちらを優先する。触媒未使用(catalystData==null)の詠唱には何も表示しない(従来挙動)。
        if (catalystData != null) {
            com.arspaper.integration.TrinityForgeBridge.startItemCooldown(
                caster, catalyst, catalystData.cooldownMs() / 1000.0);
        } else if (genericItemCooldownSeconds > 0.0 && effectiveCastItem != null) {
            // item-cooldown 汎用パス(上のゲートと対): 触媒/魔導書のどちらもCTを持たない
            // 詠唱で、実際に詠唱に使ったアイテム(杖など)が item-cooldown ステを持つ場合のみ
            // ゲージを開始する。fallbackSeconds は 0.0(TrinityForgeBridge内部で
            // itemCooldownSeconds を再解決するため、ここでの再計算は不要)。
            com.arspaper.integration.TrinityForgeBridge.startItemCooldown(
                caster, effectiveCastItem, 0.0);
        }

        // アクションバーにスペル名を表示
        caster.sendActionBar(Component.text("§d" + recipe.getName()));

        return true;
    }

    /**
     * 詠唱に使ったアイテム(杖)の耐久を1詠唱ぶん減らす。
     *
     * <p><b>減らす対象は「手に持っている実体」だけ</b>: {@code effectiveCastItem} は
     * PlayerInteractEvent 由来のスタックで、実装によっては<b>インベントリのコピー</b>が来る。
     * コピーを書き換えても手持ちの耐久は1も減らない（＝実機で「減らない」と報告されるまで気づけない）ので、
     * メインハンド／オフハンドを照合してから<b>そのスロットの実体</b>を減らして置き直す。
     * どちらの手でもない詠唱（将来の遠隔経路など）は対象外にする。
     *
     * <p><b>{@code damageItemStack} を使わないのは意図的</b>: MockBukkit 未実装で、TF 側では
     * テストが失敗ではなく SKIPPED に化ける既知の罠。TF の {@code ChainBreakSupport#damageHeldTool} と
     * 同じく {@link org.bukkit.inventory.meta.Damageable} を直接操作する。
     *
     * <p>最大耐久0の素材（魔導書の BOOK、旧素材の BLAZE_ROD）は自動的に無視される。
     * つまり<b>剣系へ移した杖だけが減る</b>ので、配布済みの旧杖を壊してしまうこともない。
     */
    private void consumeCastDurability(Player caster, org.bukkit.inventory.ItemStack castItem) {
        CastDurabilityPolicy policy = castDurability;
        if (policy == null || !policy.enabled() || castItem == null || castItem.getType().isAir()) {
            return;
        }
        if (caster.getGameMode() == org.bukkit.GameMode.CREATIVE
                || caster.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        org.bukkit.inventory.PlayerInventory inventory = caster.getInventory();
        org.bukkit.inventory.EquipmentSlot slot;
        org.bukkit.inventory.ItemStack held;
        if (castItem.isSimilar(inventory.getItemInMainHand())) {
            slot = org.bukkit.inventory.EquipmentSlot.HAND;
            held = inventory.getItemInMainHand();
        } else if (castItem.isSimilar(inventory.getItemInOffHand())) {
            slot = org.bukkit.inventory.EquipmentSlot.OFF_HAND;
            held = inventory.getItemInOffHand();
        } else {
            return;
        }

        org.bukkit.inventory.meta.ItemMeta meta = held.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable) || meta.isUnbreakable()) {
            return;
        }
        int maxDurability = damageable.hasMaxDamage()
            ? damageable.getMaxDamage()
            : held.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return; // BOOK / BLAZE_ROD など耐久を持たない素材
        }
        int amount = policy.damageFor(
            held.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING),
            java.util.concurrent.ThreadLocalRandom.current().nextDouble());
        if (amount <= 0) {
            return;
        }
        int next = damageable.getDamage() + amount;
        if (next >= maxDurability) {
            setHeld(inventory, slot, null);
            caster.playSound(caster, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return;
        }
        damageable.setDamage(next);
        held.setItemMeta(meta);
        setHeld(inventory, slot, held);
    }

    private static void setHeld(org.bukkit.inventory.PlayerInventory inventory,
                                org.bukkit.inventory.EquipmentSlot slot,
                                org.bukkit.inventory.ItemStack item) {
        if (slot == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
            inventory.setItemInOffHand(item);
        } else {
            inventory.setItemInMainHand(item);
        }
    }

    /**
     * プレイヤーのクールダウンをクリアする（ログアウト時用）。
     * form別キー（playerUuid + ":" + formKey）を全て除去する。
     */
    public void clearCooldown(UUID playerId) {
        String prefix = playerId + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        invalidateGlyphCache(playerId);
    }

    /**
     * 詠唱に使われたcatalyst ItemStackが、登録済み触媒(catalysts.yml)かどうかを解決する。
     * material + CustomModelDataで照合するCatalystConfigへ委譲する。
     */
    private static CatalystData resolveCatalystData(org.bukkit.inventory.ItemStack catalyst) {
        if (catalyst == null) return null;
        return ArsPaper.getInstance().getCatalystConfig().resolve(catalyst);
    }

    /**
     * 詠唱に使われたcatalyst ItemStackが魔導書(spell_book_*)であれば、そのティア定義を返す。
     * 魔導書でない、またはBOOK_TIER未設定/未知ティアの場合は{@code null}。
     */
    private static SpellBookTierData resolveSpellBookTierData(org.bukkit.inventory.ItemStack catalyst) {
        if (catalyst == null || !catalyst.hasItemMeta()) return null;
        String customId = catalyst.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        if (customId == null || !customId.startsWith("spell_book_")) return null;
        int tier = catalyst.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.BOOK_TIER, PersistentDataType.INTEGER, 1);
        return ArsPaper.getInstance().getSpellBookConfig().byTier(tier);
    }

    /**
     * 指定formの実効CT(ms)を解決する。
     * form-cooldownsに秒数(>0)が設定されていればそれを、なければfallbackを返す。
     */
    private long resolveFormCooldownMs(String formKey, long fallbackMs) {
        Long configured = formCooldownMs.get(formKey);
        return (configured != null && configured > 0) ? configured : fallbackMs;
    }

    /**
     * プレイヤーのグリフキャッシュを無効化する。
     */
    public void invalidateGlyphCache(UUID playerId) {
        glyphCache.remove(playerId);
        glyphCacheExpiry.remove(playerId);
    }

    /**
     * スペル内にワールドでBANされたグリフが含まれるかチェック。
     * @return BANされたグリフの表示名（なければnull）
     */
    private String checkSpellBanned(Player caster, SpellRecipe recipe) {
        WorldSettingsManager wsm = ArsPaper.getInstance().getWorldSettingsManager();
        if (wsm == null) return null;
        String worldName = caster.getWorld().getName();
        for (SpellComponent comp : recipe.getComponents()) {
            if (wsm.isSpellBanned(worldName, comp.getId().toString())) {
                return GlyphNames.display(comp);
            }
        }
        return null;
    }

    /**
     * スペル内の全グリフが発動者によって解放済みかチェック。
     */
    private boolean checkGlyphsUnlocked(Player caster, SpellRecipe recipe) {
        Set<String> unlocked = getCachedGlyphs(caster);

        for (SpellComponent comp : recipe.getComponents()) {
            if (!unlocked.contains(comp.getId().toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * プレイヤーの解放済みグリフをキャッシュ付きで取得する。
     * TTL(5秒)以内であればキャッシュを返す。
     */
    private Set<String> getCachedGlyphs(Player player) {
        UUID uuid = player.getUniqueId();
        Long expiry = glyphCacheExpiry.get(uuid);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            Set<String> cached = glyphCache.get(uuid);
            if (cached != null) return cached;
        }

        String json = player.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        if (json == null) return Set.of();

        Set<String> unlocked = new HashSet<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            arr.forEach(el -> unlocked.add(el.getAsString()));
        } catch (Exception e) {
            return Set.of();
        }

        glyphCache.put(uuid, unlocked);
        glyphCacheExpiry.put(uuid, System.currentTimeMillis() + CACHE_TTL_MS);
        return unlocked;
    }
}
