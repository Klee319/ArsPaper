package com.arspaper.command.handlers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /ars help} — {@code /ars} のサブコマンド一覧。
 *
 * <h2>なぜ必要か(2026-07-31 F3 指摘1)</h2>
 * これまで {@code /ars} のサブコマンドの唯一の発見経路が<b>Brigadier のタブ補完</b>だった。
 * 補完は「{@code /ars } まで打てば出る」ので存在を知っている人には十分だが、
 * {@code /ars thread}(手持ち装備のスレッド装着 ── 武器・触媒はここしか入口が無い)のように
 * <b>存在自体を知らないと辿れない</b>機能があると「枠はあるのに使えない」と受け取られる。
 *
 * <p>権限で見えないものは載せない({@code arspaper.admin} を持たない人に管理コマンドを見せると
 * 「打てないコマンド」が並ぶだけ)。並び順は Brigadier の登録順ではなく<b>使う頻度順</b>。
 */
public final class HelpCommands {

    /** 表示する1行ぶん。{@code adminOnly} は {@code arspaper.admin} 保持者にだけ見せる。 */
    private record Entry(String usage, String description, boolean adminOnly) {
    }

    private static final String ADMIN_PERMISSION = "arspaper.admin";

    private static final List<Entry> ENTRIES = List.of(
            new Entry("/ars thread",
                    "メインハンドの装備にスレッドを装着（武器・触媒・ツールはここが入口。"
                            + "着用防具はスニーク+右クリックでも開く）", false),
            new Entry("/ars status", "自分のステータス（マナ・グリフ解放状況など）を表示", false),
            new Entry("/ars mana", "マナの上限・回復量の内訳を表示", false),
            new Entry("/ars mana notify", "マナ不足の通知をON/OFF", false),
            new Entry("/ars backpack", "着用中の防具のバックパックスレッドの中身を開く", false),
            new Entry("/ars spell list", "手持ちの魔導書のスペル構成を表示", false),
            new Entry("/ars spell set <スロット> <構成>", "魔導書のスロットにスペルを書き込む", false),
            new Entry("/ars spell bind <スロット>",
                    "オフハンドの装備へスペルをバインド（メインハンドに魔導書）", false),
            new Entry("/ars spell unbind", "手持ちの装備のバインドを解除", false),
            new Entry("/ars ranking glyphs", "グリフ解放数のランキング", false),
            new Entry("/ars ranking mana", "最大マナのランキング", false),
            new Entry("/ars give <id> [個数] [対象]", "ArsPaper のアイテムを配布", true),
            new Entry("/ars glyph <unlockall|lockall>", "グリフを一括解放/一括封鎖", true),
            new Entry("/ars world ...", "ワールド単位のマナ設定・情報・立入禁止", true),
            new Entry("/ars fixmana [対象]", "マナ値の破損を修復", true),
            new Entry("/ars cleanup", "孤児データの掃除", true),
            new Entry("/ars debug [on|off]", "デバッグ表示の切替", true),
            new Entry("/ars pvp <on|off>", "PvP の切替", true),
            new Entry("/ars reload [reset]", "設定の再読み込み（reset で既定値へ戻す）", true)
    );

    private HelpCommands() {
    }

    public static int executeHelp(CommandSender sender) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        sender.sendMessage(Component.text("=== ArsPaper コマンド一覧 ===", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true));
        for (Entry entry : ENTRIES) {
            if (entry.adminOnly() && !admin) {
                continue;
            }
            sender.sendMessage(Component.text(entry.usage(), NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.suggestCommand(commandRootOf(entry.usage())))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry.description(), NamedTextColor.GRAY)));
        }
        return 1;
    }

    /**
     * クリック時にチャット欄へ差し込む文字列。プレースホルダ({@code <...>} / {@code [...]} /
     * {@code ...})の手前で切る ── そのまま送ると必ず構文エラーになるため。
     */
    private static String commandRootOf(String usage) {
        StringBuilder root = new StringBuilder();
        for (String token : usage.split(" ")) {
            if (token.startsWith("<") || token.startsWith("[") || token.equals("...")) {
                break;
            }
            if (root.length() > 0) {
                root.append(' ');
            }
            root.append(token);
        }
        return root.toString();
    }
}
