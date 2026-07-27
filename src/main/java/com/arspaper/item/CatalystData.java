package com.arspaper.item;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * spellbooks.yml の {@code catalysts:} 節の1エントリ分の触媒定義データ。
 *
 * <p>触媒は「item-catalog相当のアイテム定義＋item-stat相当のステ(固定/品質別/ランダムロール)」を持つ。
 * ステの解決自体はTrinityForgeエンジンに委譲する（{@link com.arspaper.integration.TrinityForgeBridge#registerCatalystStats}
 * でTFの動的item-stats登録APIへ登録し、品質/rollSeed込みの解決・lore自動生成・戦闘連携をTF側に一本化する）。
 *
 * <p>不変レコード。生成は {@link CatalystConfig} のローダーが担う。
 *
 * @param id                    触媒ID（spellbooks.yml catalysts: の直下キー）
 * @param material              ベースとなるバニラマテリアル
 * @param customModelData       CustomModelData値
 * @param displayName           表示名
 * @param nameColor             表示名の色
 * @param dyeColor              革防具染色色（革素材以外では無視される）。未指定は{@code null}
 * @param lore                  フレーバーLore（任意）
 * @param bindType              TrinityForge BindType名（例: TRADEABLE）。未指定は{@code null}(刻印なし)
 * @param maxBindTier           バインド可能な最大spell(グリフ)tier
 * @param manaFlatReduction     消費マナからの実数減算
 * @param manaPercentReduction  消費マナ減少率(%)。ManaManagerの装備由来削減%に加算合成される
 * @param cooldownMs            発動CT(ミリ秒)。0=CTなし(触媒由来の追加CTゲートを無効化)
 * @param fixedStats            item-stat固定値(canonicalキー -&gt; 値)
 * @param perQualityStats       item-stat品質1あたりの加算値(canonicalキー -&gt; 値)
 * @param randomStats           item-statランダムロール範囲(canonicalキー -&gt; min/max)
 */
public record CatalystData(
    String id,
    Material material,
    int customModelData,
    String displayName,
    TextColor nameColor,
    Color dyeColor,
    List<String> lore,
    String bindType,
    int maxBindTier,
    int manaFlatReduction,
    int manaPercentReduction,
    long cooldownMs,
    Map<String, Double> fixedStats,
    Map<String, Double> perQualityStats,
    Map<String, CatalystStatRange> randomStats
) {
}
