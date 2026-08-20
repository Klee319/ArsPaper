package com.arspaper.spell.effect;

import com.arspaper.util.PdcHelper;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 「Material だけを見てドロップアイテムを別の Material へ差し替える」魔法
 * ({@link SmeltEffect} の精錬 / {@link CrushEffect} の粉砕) が、
 * カスタムアイテムを巻き込まないようにするためのポリシー (2026-08-20 W-172)。
 *
 * <h2>なぜ要るか(実バグ)</h2>
 * 精錬魔法は {@code SMELT_MAP.get(stack.getType())} だけで {@code Item} の中身を
 * {@code setItemStack(new ItemStack(...))} に差し替えていた。{@code potato_3x}
 * (729倍圧縮ジャガイモ、{@code base_material: POTATO}) を焼くと <b>ベイクドポテト1個</b>になり、
 * CMD も PDC も表示名も消えるので圧縮を戻すこともできない完全な喪失になっていた。
 *
 * <p>かまど・醸造台・コンポスター経路は {@code CustomItemListener} が既に塞いでいるが、
 * <b>魔法はどのバニライベントも通らない</b>ので、この経路だけ穴が残っていた。
 * 粉砕(crush)も同じ形で、{@code crush_map} の {@code STONE}/{@code DEEPSLATE}/
 * {@code QUARTZ_BLOCK}/{@code MELON} は圧縮素材のベース材質でもある。
 *
 * <h2>方針</h2>
 * 変換先を用意するのではなく<b>変換しない</b>。「圧縮ジャガイモを焼いた焼き圧縮ジャガイモ」は
 * 全ベース材質ぶん定義しないと成立せず、定義漏れがまた無言の喪失に化けるため。
 */
final class CustomItemConversionPolicy {

    private CustomItemConversionPolicy() {
    }

    /**
     * この {@code stack} を変換してよいなら変換先 Material を、駄目なら {@code null} を返す。
     *
     * @param table 変換表({@code SMELT_MAP} / {@code crush_map})
     * @param stack ドロップアイテムの中身
     */
    static Material resultFor(Map<Material, Material> table, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return resultFor(table, stack.getType(), PdcHelper.hasProtectedIdentity(stack));
    }

    /**
     * 判定本体。Bukkit ランタイム無しで固定できるよう {@link ItemStack} から切り離してある
     * (このフォークのテスト基盤は MockBukkit/Mockito を持たない)。
     *
     * @param protectedIdentity カスタムアイテムid か CustomModelData を持つ
     *                          ({@link PdcHelper#hasProtectedIdentity})
     */
    static Material resultFor(Map<Material, Material> table, Material from, boolean protectedIdentity) {
        if (from == null || protectedIdentity) {
            return null;
        }
        return table.get(from);
    }
}
