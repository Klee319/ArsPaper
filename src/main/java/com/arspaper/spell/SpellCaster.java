package com.arspaper.spell;

import com.arspaper.ArsPaper;
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
    /** 使用ゲート（perk所持→glyph使用許可）。 */
    private final UsageGate usageGate;

    public SpellCaster(ManaManager manaManager) {
        this.manaManager = manaManager;
        this.usageGate = new UsageGate(ArsPaper.getInstance());
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
                return comp.getDisplayName();
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
        if (recipe == null || !recipe.isValid()) {
            caster.sendMessage(Component.text("無効なスペルです！", NamedTextColor.RED));
            return false;
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

        int baseCost = recipe.getTotalManaCost();
        // マナ消費量低下%の合算はManaManagerに集約（THREAD_COST_REDUCTION＋将来の装備由来削減）。
        int costReduction = manaManager.getCostReductionPercent(caster);
        int cost = Math.max(1, baseCost - (int) Math.round(baseCost * costReduction / 100.0));
        if (!manaManager.consumeMana(caster, cost)) {
            // マナ不足通知が無効化されていなければメッセージ表示
            int notifyOff = caster.getPersistentDataContainer()
                .getOrDefault(ManaKeys.MANA_NOTIFY_OFF, PersistentDataType.INTEGER, 0);
            if (notifyOff == 0) {
                caster.sendMessage(Component.text("マナが不足しています！", NamedTextColor.RED));
            }
            return false;
        }

        cooldowns.put(cooldownKey, now);
        // 非発動（idle）回復ボーナス判定用に最終詠唱時刻を記録
        manaManager.touchCast(caster);

        SpellContext context = new SpellContext(caster, recipe, catalyst);
        context.applyFormAugments();
        SpellForm spellForm = recipe.getForm();
        spellForm.cast(caster, context);

        // エフェクトがキャンセルした場合、マナを返還
        if (context.isCancelled()) {
            manaManager.addMana(caster, cost);
            cooldowns.remove(cooldownKey);
            return false;
        }

        // ARS_MAGIC(魔法柱スキル)へEXPを付与（TrinityForge連携）。
        // 到達条件＝詠唱成功確定点: 全perkゲート通過・マナ消費成立(consumeMana==true)・
        // スペル発動後にエフェクトがキャンセルしていない（上のisCancelled分岐でreturn済み）。
        // 誤付与防止の担保:
        //  - 非プレイヤー詠唱（儀式/タレット等）: これらはSpellCaster.castを経由せず、
        //    本メソッドのcaster型はPlayer固定のため、そもそもここには到達しない。
        //  - マナ不足/CT中/各ゲート不通過: いずれも上流でreturnしており未到達。
        //  - 二重付与なし: 1回のcast成功につき1回のみ呼ばれる（cost = 実消費マナ）。
        grantArsMagicExp(caster, cost);

        // アクションバーにスペル名を表示
        caster.sendActionBar(Component.text("§d" + recipe.getName()));

        return true;
    }

    /**
     * ARS_MAGIC カスタムスキルへEXPを付与する（プレイヤー詠唱成功時のみ）。
     * amount = ars-magic.exp-per-cast + ars-magic.exp-per-mana * cost（config駆動）。
     *
     * <p>TrinityForge未ロード（{@code TrinityForge.getInstance()==null}）時は呼ばない（fail-open）。
     * ValhallaMMO不在/ARS_MAGIC未登録/amount<=0 は {@code ArsBridge.grantMagicExp} が内部でno-op化し、
     * 例外も内部で握るため、ここでの追加ハンドリングは不要。
     *
     * @param player 詠唱に成功したプレイヤー
     * @param cost   その詠唱で実際に消費したマナ量
     */
    private void grantArsMagicExp(Player player, int cost) {
        com.trinityforge.TrinityForge tf = com.trinityforge.TrinityForge.getInstance();
        if (tf == null) {
            return; // TF未ロード: 付与しない（例外を出さない）
        }
        var manaConfig = manaManager.getConfig();
        double amount = manaConfig.arsMagicExpPerCast()
            + manaConfig.arsMagicExpPerMana() * cost;
        com.trinityforge.bridge.valhalla.ArsBridge.grantMagicExp(tf, player, amount);
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
                return comp.getDisplayName();
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
