package com.arspaper.item;

import net.kyori.adventure.text.format.TextColor;

/**
 * spellbooks.yml の1ティア分の魔導書定義データ。
 * spellbooks.yml のリスト内の並び順がそのままティア段階を表し、
 * {@link #tier()}（1始まり）はアイテムのPDCに書き込まれるBOOK_TIER整数値と一致する。
 *
 * 不変レコード。生成は {@link SpellBookConfig} のローダーが担う。
 */
public record SpellBookTierData(
    int tier,
    String id,
    String displayName,
    TextColor nameColor,
    int maxSlots,
    int maxGlyphTier,
    int maxGlyphs,
    int customModelData,
    String upgradeFrom,
    long cooldownMs
) {

    // 旧SpellBookTier enum の getXxx() 呼び出し形を維持するためのエイリアス。
    // 呼び出し側の差分を最小化しつつ、内部実装はconfigルックアップに委譲する。
    public int getTier() { return tier; }
    public String getItemId() { return id; }
    public String getDisplayName() { return displayName; }
    public TextColor getNameColor() { return nameColor; }
    public int getMaxSlots() { return maxSlots; }
    public int getMaxGlyphTier() { return maxGlyphTier; }
    /** 1魔法に設定可能なグリフ数(既定9、ハード上限9)。 */
    public int getMaxGlyphs() { return Math.max(1, Math.min(9, maxGlyphs <= 0 ? 9 : maxGlyphs)); }
    public int getCustomModelData() { return customModelData; }
    public String getUpgradeFrom() { return upgradeFrom; }
    /** 発動CT(ミリ秒)。0=CTなし(魔導書由来の追加CTゲートを無効化、従来挙動)。 */
    public long getCooldownMs() { return cooldownMs; }
}
