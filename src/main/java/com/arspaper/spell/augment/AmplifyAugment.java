package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * パワー+1.0。ダメージ増、硬度増、各エフェクト固有の強化。
 * Ars Nouveau: amplification += 1.0
 *
 * <p>この augment 自身が積むのは「増幅段数({@code amplifyLevel})」のみ。段数の使い道は
 * エフェクトごとに異なる（例: harm/scorch/lightning 等の直接ダメージ系は
 * {@code SpellContext#dealSpellDamage} を通じて Sharpness と同じ乗算ボーナス(既定1段+10%、
 * {@code glyphs.yml} の {@code amplify.params.damage-rate-per-stack} で調整可。2026-08-02変更)。
 * knockback/launch 等の力・速度系や summon 系のHPは従来どおり段数に固定値を掛ける個別実装のまま）。
 */
public class AmplifyAugment implements SpellAugment {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public AmplifyAugment(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "amplify");
        this.config = config;
    }

    @Override
    public void modify(SpellContext context) {
        int bonus = (int) config.getParam("amplify", "per-stack", 1.0);
        context.setAmplifyLevel(context.getAmplifyLevel() + bonus);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "増幅"; }

    @Override
    public String getDescription() { return "効果の威力を強化する"; }

    @Override
    public int getManaCost() { return config.getManaCost("amplify"); }

    @Override
    public int getTier() { return config.getTier("amplify"); }
}
