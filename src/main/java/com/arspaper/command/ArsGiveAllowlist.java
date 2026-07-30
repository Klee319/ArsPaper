package com.arspaper.command;

import java.util.Locale;
import java.util.Set;

/**
 * /ars give で配布可能な装置系アイテムID。
 * 魔導書・素材・触媒などは /tf give 側へ寄せる。
 */
public final class ArsGiveAllowlist {

    private static final Set<String> EXACT = Set.of(
            "pedestal",
            "ritual_core",
            "scribing_table",
            "source_jar",
            "creative_source_jar");

    private ArsGiveAllowlist() {}

    public static boolean isAllowed(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        if (EXACT.contains(id)) {
            return true;
        }
        if (id.endsWith("_sourcelink")) {
            return true;
        }
        // 2026-07-31: sourcejars.yml に足した上位ジャーも配布可能にする。
        // ここを忘れると「ブロックとしては登録されているのに /ars give で出せない」ジャーができる。
        if (com.arspaper.block.impl.SourceJar.isSourceJarId(id)) {
            return true;
        }
        // カスタムid (sourcelinks.yml items:) のソースリンクも配布可能にする
        com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
        return ars != null && ars.getBlockRegistry().get(id)
                .filter(cb -> cb instanceof com.arspaper.source.sourcelink.Sourcelink)
                .isPresent();
    }
}
