package com.arspaper.spell;

import com.arspaper.ArsPaper;

/**
 * グリフ表示名の解決を一元化するヘルパー。
 * glyphs.yml の display-name 上書きを優先し、未設定ならソースコードのハードコード名にフォールバックする。
 * プレイヤーに表示される全箇所（GUI/メッセージ）はここを経由すること。
 * 互換性判定等の内部ロジックは comp.getId().getKey() を使うためこのクラスの影響を受けない。
 */
public final class GlyphNames {

    private GlyphNames() {}

    public static String display(SpellComponent comp) {
        if (comp == null) return "";

        ArsPaper plugin = ArsPaper.getInstance();
        GlyphConfig config = plugin != null ? plugin.getGlyphConfig() : null;
        String override = config != null ? config.displayNameOverride(comp.getId().getKey()) : null;

        return override != null ? override : comp.getDisplayName();
    }
}
