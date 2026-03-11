package com.arspaper.spell;

import com.arspaper.mana.ManaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * スペルの発動を処理するエンジン。
 * マナチェック + クールダウン → Formの発動 → Effectチェーンの実行。
 */
public class SpellCaster {

    private static final long DEFAULT_COOLDOWN_MS = 500; // 0.5秒

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

        // クールダウンチェック
        long now = System.currentTimeMillis();
        Long lastCast = cooldowns.get(caster.getUniqueId());
        if (lastCast != null && now - lastCast < DEFAULT_COOLDOWN_MS) {
            return false;
        }

        int cost = recipe.getTotalManaCost();
        if (!manaManager.consumeMana(caster, cost)) {
            caster.sendMessage(Component.text("マナが不足しています！", NamedTextColor.RED));
            return false;
        }

        cooldowns.put(caster.getUniqueId(), now);

        SpellContext context = new SpellContext(caster, recipe);
        context.applyFormAugments();
        SpellForm form = recipe.getForm();
        form.cast(caster, context);

        return true;
    }

    /**
     * プレイヤーのクールダウンをクリアする（ログアウト時用）。
     */
    public void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
    }
}
