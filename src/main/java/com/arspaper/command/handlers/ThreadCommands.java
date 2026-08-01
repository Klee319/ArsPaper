package com.arspaper.command.handlers;

import com.arspaper.ArsPaper;
import com.arspaper.gui.ThreadGui;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.item.ThreadApplicationPolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * {@code /ars thread} — メインハンドの装備のスレッドスロットGUIを開く。
 *
 * <p><b>なぜコマンドなのか(2026-07-31 F2)</b>: 着用防具はスニーク+右クリックで
 * {@link com.arspaper.item.ThreadGuiOpenListener} が開くが、同じトリガーを手持ち装備へ広げると
 * {@link com.arspaper.spell.SpellBindListener}(NORMAL 優先度・右クリックで呪文発動)などと
 * 二重に走ってしまう。武器・触媒・ツールはこのコマンドを唯一の入口にすることで衝突を消している。
 *
 * <p>コマンド名は {@code /ars} のサブコマンドに閉じてある ── 汎用的な非修飾名
 * ({@code /menu} 等)は先に enable した別プラグインに総取りされる事故があるため。
 *
 * <p><b>発見経路(2026-07-31 F3 指摘1)</b>: Brigadier の補完だけが入口だと
 * 「そんなコマンドがある」と知っている人しか辿れない。{@link HelpCommands}({@code /ars help})に
 * 掲載し、さらにスレッド枠を持つ装備をメインハンドに選択した時点で
 * {@link com.arspaper.item.ThreadGuiOpenListener} がアクションバーへ案内を出す。
 */
public final class ThreadCommands {

    private ThreadCommands() {
    }

    /**
     * メインハンドの装備のスレッドGUIを開く。開けない場合は理由を日本語で返す。
     *
     * <p>着用スロットへ入る防具を手に持っている場合も開ける(装着してから着る運用のため)。
     * ただしその防具のスレッドは「着用中」しかステに乗らない点は従来どおり。
     */
    public static int executeThread(ArsPaper plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤー専用コマンドです！", NamedTextColor.RED));
            return 0;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(Component.text(
                    "スレッドを装着したい装備をメインハンドに持ってから実行してください。",
                    NamedTextColor.RED));
            return 0;
        }

        // F6 指摘1(HIGH): ItemMeta はスタック単位なので、2個以上のスタックへ装着すると
        // 全個体がスレッドを持ち消費は1個だけ = 複製。取り外しは逆に全個体から消える = データ喪失。
        // BLAZE_ROD 触媒11件と ENDER_EYE#85 は最大スタック64で、シフトクラフトすると
        // PDC が同一な N 個スタックができるため実際に到達可能(詳細は ThreadApplicationPolicy)。
        if (ThreadApplicationPolicy.isStackTooLargeToSocket(held.getAmount())) {
            player.sendMessage(Component.text(
                    "同じ装備が" + held.getAmount() + "個重なっています。"
                            + "スレッドは1個ずつしか装着できません（1個だけ手に持ってから実行してください）。",
                    NamedTextColor.RED));
            return 0;
        }

        int slots = effectiveThreadSlots(held, player);
        if (slots <= 0) {
            player.sendMessage(Component.text(
                    "この装備にはスレッド枠がありません（スレッド枠を持つ装備を持ってください。"
                            + "「スレッド枠拡張の儀式」で枠を増やせる装備もあります）。",
                    NamedTextColor.RED));
            return 0;
        }

        if (!ThreadApplicationPolicy.isArmorSlotMaterial(held.getType())) {
            // 手持ち装備で効く範囲を開く前に伝える(GUI内にも同じ注記が出る)。
            player.sendMessage(Component.text(
                    "手持ち装備のスレッド: ステータスとセット効果は効きますが、"
                            + "常時ポーション効果・飛行は着用中の防具だけです。",
                    NamedTextColor.GRAY));
        }

        // スロット番号を必ず渡す。ThreadGui は装着直前にこのスロットの中身と対象の同一性を
        // 突き合わせ、対象を手から離したままの装着(スレッドだけ溶ける)を止める(F3 指摘5)。
        new ThreadGui(player, held, plugin, player.getInventory().getHeldItemSlot()).open();
        return 1;
    }

    /** 装備自身の item-stats {@code thread_slots}(儀式による拡張込み)。TF未ロード時は0。 */
    private static int effectiveThreadSlots(ItemStack item, Player player) {
        try {
            Map<String, Double> stats = TrinityForgeBridge.resolveFullItemStats(item);
            return TrinityForgeBridge.tfEffectiveThreadSlotCap(stats, player);
        } catch (Throwable tfUnavailable) {
            return 0;
        }
    }
}
