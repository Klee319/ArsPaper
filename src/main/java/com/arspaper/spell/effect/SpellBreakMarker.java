package com.arspaper.spell.effect;

/**
 * 魔法(Ars)破壊が合成する {@link org.bukkit.event.block.BlockBreakEvent} に立てる block metadata キー。
 *
 * <p>TrinityForge 側は同じ文字列("{@value #METADATA_KEY}")を採取系リスナーの冒頭ガードに使う
 * ({@code com.trinityforge.listeners.SpellBreakGuard})。フォークとTFはコンパイル時に結合しない方針
 * (jarのロード順/API再生成問題を避ける)なので、この定数は文字列として両側に個別定義されている —
 * 変更する場合は必ず両側を同時に直すこと。
 *
 * <p>使い方: {@link AdvancedBreakEffect}/{@link BreakEffect} が合成 BlockBreakEvent を
 * {@code Bukkit.getPluginManager().callEvent(...)} で発火する直前にブロックへ
 * {@link org.bukkit.metadata.FixedMetadataValue} をセットし、発火後は必ず {@code finally} で除去する。
 * メインハンドが杖かどうかでの判定は採用しない(任意アイテムへスペルをバインドできるため、ピッケルへ
 * バインドして判定をすり抜けるexploitになる — 2026-07-26設計判断)。
 */
public final class SpellBreakMarker {

    public static final String METADATA_KEY = "trinityforge:spell-break";

    private SpellBreakMarker() {
    }

    /**
     * Fires a synthetic break event while the exact block is marked for TrinityForge.
     * The marker always disappears, including when another listener throws.
     */
    public static org.bukkit.event.block.BlockBreakEvent callMarkedBreakEvent(
            org.bukkit.plugin.Plugin plugin, org.bukkit.block.Block block,
            org.bukkit.entity.Player caster) {
        org.bukkit.event.block.BlockBreakEvent event;
        block.setMetadata(METADATA_KEY, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        try {
            event = new org.bukkit.event.block.BlockBreakEvent(block, caster);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
        } finally {
            block.removeMetadata(METADATA_KEY, plugin);
        }
        return event;
    }

    public static org.bukkit.event.block.BlockBreakEvent callMarkedBreakEvent(
            org.bukkit.block.Block block, org.bukkit.entity.Player caster) {
        return callMarkedBreakEvent(com.arspaper.ArsPaper.getInstance(), block, caster);
    }
}
