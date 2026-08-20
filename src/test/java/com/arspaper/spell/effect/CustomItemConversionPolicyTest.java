package com.arspaper.spell.effect;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 精錬/粉砕魔法がカスタムアイテムを Material 一致だけで変換しないこと (2026-08-20 W-172 回帰)。
 *
 * <h2>実バグ</h2>
 * 精錬魔法で <b>729倍圧縮ジャガイモ({@code potato_3x}, base_material: POTATO)を焼いたら
 * ただのベイクドポテト1個になった</b>。{@code SMELT_MAP.get(stack.getType())} の結果を
 * {@code setItemStack(new ItemStack(...))} で差し替えていたため、CMD・PDC・表示名が
 * まとめて消え、圧縮を戻すこともできない完全な喪失になっていた。
 *
 * <p>同じ形の穴が粉砕({@code crush_map} の STONE/DEEPSLATE/QUARTZ_BLOCK/MELON = 圧縮素材の
 * ベース材質)にも開いていたので、判定は {@link CustomItemConversionPolicy} に集約してある。
 *
 * <p>{@code ItemStack} を作れない(このフォークに MockBukkit / Mockito は無い)ので、
 * 「同一性を持つか」を boolean で受ける判定本体を対象にする。
 */
class CustomItemConversionPolicyTest {

    /** 実際の SMELT_MAP と同じ形の抜粋。POTATO は圧縮ジャガイモのベース材質でもある。 */
    private static final Map<Material, Material> SMELT = Map.of(
            Material.POTATO, Material.BAKED_POTATO,
            Material.IRON_ORE, Material.IRON_INGOT);

    /** 実際の crush_map と同じ形の抜粋。STONE は stone_1x..5x のベース材質でもある。 */
    private static final Map<Material, Material> CRUSH = Map.of(
            Material.STONE, Material.COBBLESTONE,
            Material.GRAVEL, Material.SAND);

    @Test
    void vanillaDropsStillConvert() {
        assertEquals(Material.BAKED_POTATO,
                CustomItemConversionPolicy.resultFor(SMELT, Material.POTATO, false),
                "素のジャガイモは今までどおり焼けること(保護のせいで本来の用途を殺してはいけない)");
        assertEquals(Material.COBBLESTONE,
                CustomItemConversionPolicy.resultFor(CRUSH, Material.STONE, false),
                "素の石は今までどおり粉砕できること");
    }

    @Test
    void customItemsAreNeverConverted() {
        assertNull(CustomItemConversionPolicy.resultFor(SMELT, Material.POTATO, true),
                "圧縮ジャガイモを焼いてベイクドポテト1個に化けさせてはいけない(W-172 の実バグ)");
        assertNull(CustomItemConversionPolicy.resultFor(CRUSH, Material.STONE, true),
                "圧縮石を粉砕して丸石1個に化けさせてはいけない");
    }

    @Test
    void unknownMaterialsStayUntouched() {
        assertNull(CustomItemConversionPolicy.resultFor(SMELT, Material.DIAMOND, false),
                "変換表に無い Material は従来どおり無変換");
        assertNull(CustomItemConversionPolicy.resultFor(SMELT, null, false),
                "null Material で NPE を投げないこと");
    }
}
