package com.arspaper.item;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * materials.ymlの1素材分の定義データ。
 */
public record MaterialConfig(
    String id,
    String displayName,
    String nameColor,
    Material baseMaterial,
    int customModelData,
    List<String> lore,
    // エンチャント光沢(キラキラ)を付与するか。既定 true（従来どおり）
    boolean enchantGlow,
    // 儀式レシピ（null=レシピなし）
    String coreItem,
    List<String> pedestalItems,
    int source,
    /**
     * この素材を「食料として食べてよい」か(materials.yml の {@code edible}、既定 false)。
     *
     * <p><b>2026-08-19 W-131/W-149 の真因</b>: {@code CustomItemListener#onConsumeMaterial} は
     * 「materials.yml 由来 かつ base_material が食べ物」なら<b>無条件で</b>
     * {@code PlayerItemConsumeEvent} をキャンセルしていた。圧縮ステーキ(base_material: COOKED_BEEF)の
     * ような圧縮食料もこれに掛かるため、<b>食事モーションだけ再生されてアイテムは減らず満腹度も戻らない</b>
     * (メッセージも出ない)という症状になっていた。TF 本体の {@code stats/food-gimmick.yml} の
     * {@code custom-foods} に登録しても症状が変わらなかったのは、Ars 側がキャンセルした時点で
     * TF の {@code FoodGimmickListener}({@code ignoreCancelled = true})に到達しないため。
     *
     * <p><b>この旗は「食べられるか」の門であって「何回復するか」ではない。</b> 回復量は従来どおり
     * TF の {@code custom-foods} が決める。したがって<b>圧縮食料は Ars 側 {@code edible: true} と
     * TF 側 {@code custom-foods} の両方に載っている必要がある</b>:
     * <ul>
     *   <li>Ars だけ true / TF 未登録 → TF の {@code unregistered-custom-food-ban} が塞ぐ(食べられない)</li>
     *   <li>TF だけ登録 / Ars が false → ここが塞ぐ(食べられない)</li>
     * </ul>
     * どちらの片落ちも「食べられない」側へ倒れる(バニラ栄養値で食べ放題にはならない)ので、
     * 事故の向きとしては安全側。片方だけ足したときの取りこぼしは
     * {@code CompressedFoodEdibleFlagTest} が検出する。
     */
    boolean edible
) {}
