package com.arspaper.spell;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.arspaper.mana.ManaManager;
import com.arspaper.spell.form.BeamForm;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

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

    private final ManaManager manaManager;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> glyphCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> glyphCacheExpiry = new ConcurrentHashMap<>();

    public SpellCaster(ManaManager manaManager) {
        this.manaManager = manaManager;
    }

    /**
     * スペルを発動する。
     *
     * @param caster 術者
     * @param recipe スペル構成
     * @return 発動に成功したかどうか
     */
    public boolean cast(Player caster, SpellRecipe recipe) {
        return cast(caster, recipe, false);
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
        if (recipe == null || !recipe.isValid()) {
            caster.sendMessage(Component.text("無効なスペルです！", NamedTextColor.RED));
            return false;
        }

        // グリフ解放チェック: 共有エンチャント付きでない場合のみ
        if (!sharedSpell && !checkGlyphsUnlocked(caster, recipe)) {
            caster.sendMessage(Component.text("未解放のグリフが含まれています！", NamedTextColor.RED));
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

        long now = System.currentTimeMillis();
        Long lastCast = cooldowns.get(caster.getUniqueId());
        if (lastCast != null && now - lastCast < cooldownMs) {
            return false;
        }

        int baseCost = recipe.getTotalManaCost();
        int costReduction = Math.min(100, caster.getPersistentDataContainer()
            .getOrDefault(ManaKeys.THREAD_COST_REDUCTION, PersistentDataType.INTEGER, 0));
        int cost = Math.max(1, baseCost - (int) Math.round(baseCost * costReduction / 100.0));
        if (!manaManager.consumeMana(caster, cost)) {
            caster.sendMessage(Component.text("マナが不足しています！", NamedTextColor.RED));
            return false;
        }

        cooldowns.put(caster.getUniqueId(), now);

        SpellContext context = new SpellContext(caster, recipe);
        context.applyFormAugments();
        SpellForm spellForm = recipe.getForm();
        spellForm.cast(caster, context);

        // アクションバーにスペル名を表示
        caster.sendActionBar(Component.text("§d" + recipe.getName()));

        return true;
    }

    /**
     * プレイヤーのクールダウンをクリアする（ログアウト時用）。
     */
    public void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
        invalidateGlyphCache(playerId);
    }

    /**
     * プレイヤーのグリフキャッシュを無効化する。
     */
    public void invalidateGlyphCache(UUID playerId) {
        glyphCache.remove(playerId);
        glyphCacheExpiry.remove(playerId);
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
