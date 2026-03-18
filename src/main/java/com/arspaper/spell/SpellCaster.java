package com.arspaper.spell;

import com.arspaper.mana.ManaKeys;
import com.arspaper.mana.ManaManager;
import com.arspaper.spell.form.BeamForm;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * スペルの発動を処理するエンジン。
 * マナチェック + クールダウン → Formの発動 → Effectチェーンの実行。
 */
public class SpellCaster {

    private static final long DEFAULT_COOLDOWN_MS = 500; // 0.5秒
    private static final long MIN_COOLDOWN_MS = 100;    // 最低CT

    private final ManaManager manaManager;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

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
        if (recipe == null || !recipe.isValid()) {
            caster.sendMessage(Component.text("無効なスペルです！", NamedTextColor.RED));
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
        int costReduction = caster.getPersistentDataContainer()
            .getOrDefault(ManaKeys.THREAD_COST_REDUCTION, PersistentDataType.INTEGER, 0);
        int cost = Math.max(1, baseCost - baseCost * costReduction / 100);
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
    }
}
