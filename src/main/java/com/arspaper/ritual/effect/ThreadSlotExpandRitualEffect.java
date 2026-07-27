package com.arspaper.ritual.effect;

import com.arspaper.block.impl.RitualCore;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * スレッド枠拡張の儀式 - コアに置いた装備の TrinityForge {@code thread-slots} 枠を+1する。
 * effect-params の {@code max-slots} が、この儀式で1つの装備に付与できる累計スレッド枠数の上限
 * （儀式由来の累計付与カウンタそのもの）。累計がこの上限に達している、またはカテゴリ上限
 * （防具=5等）に既に達している場合は付与不可として儀式を失敗させる（素材は消費しない）。
 */
public class ThreadSlotExpandRitualEffect implements RitualEffect {

    /** {@code max-slots} 未指定/不正値/負値の既定フォールバック。 */
    private static final int DEFAULT_MAX_SLOTS = 1;

    @Override
    public boolean validate(Location coreLocation, Player player, RitualRecipe recipe) {
        int maxSlots = resolveMaxSlots(recipe);
        ItemStack coreItem = resolveCoreItem(coreLocation);
        if (coreItem == null) {
            player.sendMessage(Component.text("コアに対象の装備を置いてください！", NamedTextColor.RED));
            return false;
        }
        if (!TrinityForgeBridge.canExpandThreadSlot(coreItem, maxSlots)) {
            player.sendMessage(Component.text(
                    "この装備はこれ以上スレッド枠を拡張できません！", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        int maxSlots = resolveMaxSlots(recipe);

        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) {
            return;
        }
        ItemStack coreItem = RitualCore.getStoredItem(tileState);
        if (coreItem == null) {
            player.sendMessage(Component.text("コアに対象の装備を置いてください！", NamedTextColor.RED));
            return;
        }

        ItemStack expanded = TrinityForgeBridge.expandThreadSlot(coreItem, maxSlots);
        if (expanded == null) {
            player.sendMessage(Component.text(
                    "この装備はこれ以上スレッド枠を拡張できません！", NamedTextColor.RED));
            return;
        }

        // 変換後の装備を同じコアへ書き戻す（thread_slot_expand は RitualManager 側でコア非消費扱い）。
        RitualCore.setStoredItem(tileState, expanded);

        // エフェクト（ThreadRitualEffect と同じ演出パターン）
        Location effectLoc = coreLocation.clone().add(0.5, 1.5, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 80, 0.5, 0.5, 0.5, 1.0);
        coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

        player.sendMessage(Component.text("スレッド枠を+1しました！", NamedTextColor.GREEN));
    }

    /** コアに置かれている装備を読み取る（{@code validate} は消費前のためクリアしない）。 */
    private static ItemStack resolveCoreItem(Location coreLocation) {
        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) {
            return null;
        }
        return RitualCore.getStoredItem(tileState);
    }

    /** {@code max-slots}（この儀式で付与できる累計スレッド枠数の上限）を解決する。不正値/負値は1にクランプ。 */
    private static int resolveMaxSlots(RitualRecipe recipe) {
        String raw = recipe.effectParams().getOrDefault("max-slots", String.valueOf(DEFAULT_MAX_SLOTS));
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(DEFAULT_MAX_SLOTS, parsed);
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_SLOTS;
        }
    }
}
