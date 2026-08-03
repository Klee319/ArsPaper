package com.arspaper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.regex.Pattern;

/**
 * yml に書かれた「表示名 / lore の生文字列」を Component / プレーン文字列へ変換する<b>唯一の入口</b>。
 *
 * <p><b>なぜ1本に寄せるのか</b> — このプラグインの yml は2つの記法が混在している:
 * <ul>
 *   <li>{@code materials.yml} / {@code sourcelinks.yml} / {@code sourcejars.yml} /
 *       {@code threads.yml} … <b>レガシーの {@code &}(や {@code §})カラーコード</b></li>
 *   <li>{@code functional-items.yml}(ヘッダに明記) / TrinityForge の {@code items/catalog.yml} …
 *       <b>MiniMessage</b></li>
 * </ul>
 * 片方の記法をもう片方のパーサに通す(あるいは素の {@code Component.text(生文字列)} で包む)と、
 * <b>記号がそのまま画面に出る</b>。実際 2026-08-03 時点で
 * {@code Component.text(def.displayName())} を書いていた {@code SourceJar} /
 * {@code Sourcelink} のティアII/III と、{@code display_name} を文字列連結していた
 * {@code UnifiedRecipeLoader} の素材儀式レシピ名が、レシピGUIに
 * {@code &6&l無限ソース核精製} のように生表示されていた。
 *
 * <p>したがってここでは<b>どちらの記法で書かれていても壊さない</b>ことを唯一の契約とする:
 * レガシーコードがあればレガシーとして解釈し、MiniMessage タグがあれば MiniMessage として解釈する。
 * 両方無ければただのテキスト。呼び出し側は記法を気にしなくてよい ―― 気にさせると必ずどこかが漏れる。
 *
 * <p>斜体は常に明示 OFF にする(バニラのカスタム名は既定で斜体になるため)。
 */
public final class DisplayText {

    /** {@code &a} / {@code §a} 形式のレガシーカラーコード。 */
    private static final Pattern LEGACY = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");

    /** MiniMessage のタグらしき並び。{@code <b>} {@code <gold>} {@code <#ff0000>} 等。 */
    private static final Pattern MINI_TAG = Pattern.compile("<[^<>]+>");

    private DisplayText() {
    }

    /**
     * yml の生文字列を Component にする。null/空は {@link Component#empty()}。
     *
     * @param raw yml から読んだ文字列(レガシー {@code &} 記法 / MiniMessage / 素のテキスト)
     */
    public static Component component(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        Component parsed = parse(raw);
        return parsed.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * yml の生文字列を「色記号を落としたプレーン文字列」にする。
     * 文字列連結で名前を組み立てる場所(儀式レシピ名など)はこちらを使う ――
     * 連結してから色を付けても、途中に残ったコードが必ず生表示になる。
     */
    public static String plain(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        try {
            return PlainTextComponentSerializer.plainText().serialize(parse(raw));
        } catch (RuntimeException | LinkageError ignored) {
            return raw;
        }
    }

    /** Component をプレーン文字列にする。失敗しても表示のために例外は投げない。 */
    public static String plain(Component component) {
        if (component == null) {
            return "";
        }
        try {
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    /** 生文字列に色記号(レガシー/MiniMessage のどちらか)が含まれるか。 */
    public static boolean hasMarkup(String raw) {
        return raw != null && (LEGACY.matcher(raw).find() || MINI_TAG.matcher(raw).find());
    }

    private static Component parse(String raw) {
        try {
            if (LEGACY.matcher(raw).find()) {
                // レガシー優先。&付き文字列に MiniMessage を通すと <...> が無いので素通りしてしまう。
                return LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(raw.replace('§', '&'));
            }
            if (MINI_TAG.matcher(raw).find()) {
                return MiniMessage.miniMessage().deserialize(raw);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // 壊れたタグ等。表示のためにゲームループを壊さない ―― 生文字列へ落とす。
        }
        return Component.text(raw);
    }
}
