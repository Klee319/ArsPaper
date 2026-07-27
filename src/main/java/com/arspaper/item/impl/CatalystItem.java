package com.arspaper.item.impl;

import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.CatalystData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 触媒アイテム（spellbooks.yml catalysts:節から生成）。
 *
 * <p>バインドされた魔法を発動すると、この触媒のステータス(ステ)が反映された魔法が飛ぶ
 * （反射経路は {@link com.arspaper.spell.SpellBindListener} と
 * {@link com.arspaper.integration.TrinityForgeBridge#magicalFinalDamage} 参照）。
 * ステ自体の解決（固定/品質別/ランダムロール）はTrinityForgeの動的item-stats登録
 * （{@link TrinityForgeBridge#registerCatalystStats}）に委譲し、フォーク側では重複実装しない。
 */
public class CatalystItem extends BaseCustomItem {

    private final CatalystData data;

    public CatalystItem(JavaPlugin plugin, CatalystData data) {
        super(plugin, "catalyst_" + data.id());
        this.data = data;
    }

    @Override
    public Material getBaseMaterial() {
        return data.material();
    }

    @Override
    public Component getDisplayName() {
        return Component.text(data.displayName(), data.nameColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return data.customModelData();
    }

    /** 触媒(完成品): 儀式クラフト等で品質(rollSeed + quality)を刻印する対象。 */
    @Override
    public boolean isQualityStamped() { return true; }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta -> {
            // 革防具素材のみ染色（他素材ではcolor指定があっても無視）
            if (data.dyeColor() != null && meta instanceof LeatherArmorMeta leatherMeta) {
                leatherMeta.setColor(data.dyeColor());
            }

            List<Component> lore = new ArrayList<>();
            for (String line : data.lore()) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("最大バインドティア: " + data.maxBindTier(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            if (data.manaFlatReduction() > 0 || data.manaPercentReduction() > 0) {
                StringBuilder manaLine = new StringBuilder("消費マナ軽減: ");
                if (data.manaFlatReduction() > 0) manaLine.append("-").append(data.manaFlatReduction());
                if (data.manaPercentReduction() > 0) {
                    if (data.manaFlatReduction() > 0) manaLine.append(" / ");
                    manaLine.append("-").append(data.manaPercentReduction()).append("%");
                }
                lore.add(Component.text(manaLine.toString(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (data.cooldownMs() > 0) {
                lore.add(Component.text("発動CT: " + (data.cooldownMs() / 1000.0) + "秒", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            // ロールシード付与: 生成毎に一意な値を刻印し、品質0のbaselineでもランダムステの個体差を
            // TrinityForge側で解決可能にする（既存SpellBook等の品質スタンプ/rollSeed付与の作法に倣う）。
            TrinityForgeBridge.writeItemRoll(meta, UUID.randomUUID().getMostSignificantBits(), 0);

            // bind-type刻印（未指定/TF未ロード時はno-op、fail-open）
            TrinityForgeBridge.applyCatalystBindType(meta, data.bindType());
        });
        return item;
    }

    public CatalystData getCatalystData() {
        return data;
    }
}
