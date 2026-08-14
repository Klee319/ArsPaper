package com.arspaper.ritual.effect;

import com.arspaper.ArsPaper;
import com.arspaper.enchant.ArsEnchantments;
import com.arspaper.item.FunctionalItemConfig;
import com.arspaper.item.ItemKeys;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import com.arspaper.util.DisplayText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * エンチャント本クラフトの儀式 - コアに置いた本をカスタムエンチャント本に変換する。
 * Paper Registry APIで登録されたエンチャントを使用し、EnchantmentStorageMetaに格納する。
 *
 * <p>表示名/lore/エンチャント光は {@code functional-items.yml} の同名エントリ
 * ({@code items.<レシピID>}) から上書きできる (2026-08-14)。書かれていなければ従来どおり
 * 「&lt;エンチャント名&gt; &lt;ローマ数字&gt;」のハードコード表示にフォールバックする。
 * 参照する仕組みは {@link com.arspaper.item.BaseCustomItem#resolveDisplayName()} と同じ
 * {@link FunctionalItemConfig} なので、専用の config 層は増やさない。
 */
public class EnchantBookRitualEffect implements RitualEffect {

    /**
     * {@code UnifiedRecipeLoader#recipeKey} が2件目以降に付ける {@code _r2} 形式の接尾辞を落とす。
     * functional-items.yml のエントリキーは接尾辞の無い基底IDなので、完全一致で引けなかったときの
     * フォールバックに使う ({@code RecipeUnlockGate#gateKey} と同じ規則)。
     * 接尾辞が無い、または数字以外が続く場合は入力をそのまま返す。
     */
    static String stripRecipeIndexSuffix(String recipeId) {
        if (recipeId == null) return null;
        int idx = recipeId.lastIndexOf("_r");
        if (idx <= 0) return recipeId;
        String suffix = recipeId.substring(idx + 2);
        if (suffix.isEmpty()) return recipeId;
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return recipeId;
        }
        return recipeId.substring(0, idx);
    }

    /** functional-items.yml の上書きを、完全一致→基底IDの順で引く。未読み込み時は null。 */
    private static FunctionalItemConfig config() {
        ArsPaper instance = ArsPaper.getInstance();
        return instance != null ? instance.getFunctionalItemConfig() : null;
    }

    private static String displayNameOverride(String recipeId) {
        FunctionalItemConfig cfg = config();
        if (cfg == null || recipeId == null) return null;
        String exact = cfg.displayNameOverride(recipeId);
        if (exact != null) return exact;
        return cfg.displayNameOverride(stripRecipeIndexSuffix(recipeId));
    }

    private static List<String> loreOverride(String recipeId) {
        FunctionalItemConfig cfg = config();
        if (cfg == null || recipeId == null) return null;
        List<String> exact = cfg.loreOverride(recipeId);
        if (exact != null) return exact;
        return cfg.loreOverride(stripRecipeIndexSuffix(recipeId));
    }

    private static Boolean enchantGlowOverride(String recipeId) {
        FunctionalItemConfig cfg = config();
        if (cfg == null || recipeId == null) return null;
        Boolean exact = cfg.enchantGlowOverride(recipeId);
        if (exact != null) return exact;
        return cfg.enchantGlowOverride(stripRecipeIndexSuffix(recipeId));
    }

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        String enchantId = recipe.effectParams().get("enchantment");
        String levelStr = recipe.effectParams().getOrDefault("level", "1");

        if (enchantId == null) {
            player.sendMessage(Component.text("エンチャントタイプが指定されていません！", NamedTextColor.RED));
            return;
        }

        Enchantment enchant = ArsEnchantments.getFromId(enchantId);
        if (enchant == null) {
            player.sendMessage(Component.text("不明なエンチャント: " + enchantId, NamedTextColor.RED));
            return;
        }

        int parsedLevel;
        try {
            parsedLevel = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            parsedLevel = 1;
        }
        final int level = Math.min(parsedLevel, ArsEnchantments.MAX_LEVEL);

        String displayName = ArsEnchantments.getDisplayName(enchantId);
        String roman = ArsEnchantments.toRoman(level);

        // functional-items.yml の上書き (無ければハードコード表示)
        String nameOverride = displayNameOverride(recipe.id());
        List<String> loreLines = loreOverride(recipe.id());
        Boolean glow = enchantGlowOverride(recipe.id());

        // Bukkit APIベースのエンチャント本を生成
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
        enchantedBook.editMeta(meta -> {
            meta.displayName((nameOverride != null && !nameOverride.isBlank())
                ? DisplayText.component(nameOverride).decoration(TextDecoration.ITALIC, false)
                : Component.text(displayName + " " + roman, NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(
                ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING, "enchant_book"
            );

            // EnchantmentStorageMetaにエンチャントを格納
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchant, level, true);
            }

            if (loreLines != null) {
                meta.lore(loreLines.stream()
                    .map(line -> DisplayText.component(line).decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList()));
            } else {
                meta.lore(List.of(
                    Component.text(displayName + " " + roman, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("金床でメイジアーマーに適用", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                ));
            }

            // エンチャント本は格納エンチャントで既に光るので、既定では何も足さない。
            // enchant-glow: true を明示したときだけ他のカスタムアイテムと同じ光沢処理を行う。
            if (Boolean.TRUE.equals(glow)) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });

        // コアアイテムはRitualManagerが既に消費済み
        coreLocation.getWorld().dropItemNaturally(
            coreLocation.clone().add(0.5, 1.5, 0.5), enchantedBook
        );

        // エフェクト
        Location effectLoc = coreLocation.clone().add(0.5, 1.5, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 100, 0.5, 1, 0.5, 2.0);
        coreLocation.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 30, 0.3, 0.5, 0.3, 0.1);
        coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

        player.sendMessage(Component.text(
            displayName + " " + roman + " のエンチャント本を精製しました！", NamedTextColor.GREEN
        ));
    }
}
