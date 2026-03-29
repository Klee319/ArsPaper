package com.arspaper.spell.augment;

import com.arspaper.spell.SpellAugment;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 超増強グリフ。ベース増強2個分の効果を1スロットで発揮する。
 * コストは通常の4倍（上限64マナ）。
 * スタック上限計算では2個分としてカウントされる。
 */
public class SuperAugment implements SpellAugment {

    private final NamespacedKey id;
    private final SpellAugment base;
    private final GlyphConfig config;
    private final String displayName;
    private final String description;

    /**
     * @param plugin    プラグインインスタンス
     * @param config    グリフ設定
     * @param base      ベースとなるオーグメント
     * @param displayName 表示名（例: "超増幅"）
     * @param description 説明文
     */
    public SuperAugment(JavaPlugin plugin, GlyphConfig config, SpellAugment base,
                        String displayName, String description) {
        this.id = new NamespacedKey(plugin, "super_" + base.getId().getKey());
        this.config = config;
        this.base = base;
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public void modify(SpellContext context) {
        base.modify(context);
        base.modify(context);
    }

    /** ベースオーグメントのキー（互換性・スタック計算用） */
    public String getBaseKey() {
        return base.getId().getKey();
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return displayName; }

    @Override
    public String getDescription() { return description; }

    @Override
    public int getManaCost() {
        return Math.min(64, config.getManaCost(base.getId().getKey()) * 4);
    }

    @Override
    public int getTier() { return config.getTier(id.getKey()); }
}
