package com.arspaper.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * functional-items.yml から機能アイテム（ワンド/コンパス/儀式ブロック等）の
 * 表示名・lore・エンチャント光・材質(保持アイテムのみ)の上書き設定を読み込む。
 *
 * <p>レシピ({@code recipe:} サブセクション)はここでは扱わない。{@link com.arspaper.recipe.UnifiedRecipeLoader}
 * が同じファイルを別途読み込み、既存の{@code items.yml}と同じ登録器({@code RecipeManager}/
 * {@code RitualRecipeRegistry})へ登録する(二重実装を避けるため)。
 *
 * <p>CustomModelData / 内部ID(itemId)は編集対象外 — ymlに書かれていても無視し、警告ログを1回出す。
 */
public class FunctionalItemConfig {

    /**
     * 材質(material)の上書きを許可するアイテムID(保持アイテムのみ)。
     * ブロック系(pedestal/ritual_core/scribing_table/waystone)は、
     * TileState対応判定・{@code RitualManager}の近傍探索(Material一致による高速フィルタ)・
     * 設置バリデーション等、ブロックのMaterialに密結合したコードが複数存在するため、
     * 現時点ではmaterial上書きを許可しない(安全側に倒す。詳細は移行レポート参照)。
     */
    private static final Set<String> MATERIAL_OVERRIDE_ALLOWED = Set.of(
        "dominion_wand", "teleport_compass", "source_berry"
    );

    /** 1アイテム分の上書き値。フィールドがnullの場合はそのフィールドの上書きが無い(ハードコード値を使う)。 */
    public record Override(
        String displayName,
        List<String> lore,
        Boolean enchantGlow,
        Material material
    ) {}

    private volatile Map<String, Override> overrides = new HashMap<>();
    private final Logger logger;
    private final JavaPlugin plugin;

    public FunctionalItemConfig(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.plugin = plugin;
        load();
    }

    /**
     * functional-items.yml を再読み込みする。
     */
    public void reload() {
        load();
        logger.info("Reloaded functional-items.yml (" + overrides.size() + " overrides)");
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "functional-items.yml");
        if (!file.exists()) {
            plugin.saveResource("functional-items.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection items = config.getConfigurationSection("items");
        Map<String, Override> newData = new HashMap<>();
        if (items != null) {
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(itemId);
                if (section == null) continue;

                warnIfIgnoredKeyPresent(itemId, section, "custom-model-data");
                warnIfIgnoredKeyPresent(itemId, section, "id");

                String displayName = section.getString("display-name", "");
                List<String> lore = section.contains("lore") ? section.getStringList("lore") : null;
                Boolean enchantGlow = section.contains("enchant-glow")
                    ? section.getBoolean("enchant-glow") : null;
                Material material = resolveMaterialOverride(itemId, section);

                boolean hasAny = (displayName != null && !displayName.isBlank())
                    || lore != null || enchantGlow != null || material != null;
                if (hasAny) {
                    newData.put(itemId, new Override(
                        (displayName != null && !displayName.isBlank()) ? displayName : null,
                        lore,
                        enchantGlow,
                        material
                    ));
                }
            }
        }

        // アトミックに差し替え（volatile書き込み）
        this.overrides = newData;
    }

    private Material resolveMaterialOverride(String itemId, ConfigurationSection section) {
        if (!section.contains("material")) return null;
        if (!MATERIAL_OVERRIDE_ALLOWED.contains(itemId)) {
            logger.warning("functional-items.yml: '" + itemId + "' の material 指定は無視されます"
                + "(ブロック系アイテムはTileState/近傍探索ロジックと密結合のため未対応)");
            return null;
        }
        String matName = section.getString("material", "");
        Material material = Material.matchMaterial(matName);
        if (material == null && matName != null && !matName.isBlank()) {
            logger.warning("functional-items.yml: '" + itemId + "' の material '" + matName
                + "' は不明な素材のため無視されます");
        }
        return material;
    }

    private void warnIfIgnoredKeyPresent(String itemId, ConfigurationSection section, String key) {
        if (section.contains(key)) {
            logger.warning("functional-items.yml: '" + itemId + "' の '" + key + "' は編集対象外のため無視されます");
        }
    }

    /**
     * 指定itemIdの表示名上書き値を返す。未設定の場合はnull（ハードコード名にフォールバック）。
     */
    public String displayNameOverride(String itemId) {
        Override o = overrides.get(itemId);
        return o != null ? o.displayName() : null;
    }

    /**
     * 指定itemIdのlore上書き値(MiniMessage文字列のリスト)を返す。未設定の場合はnull。
     */
    public List<String> loreOverride(String itemId) {
        Override o = overrides.get(itemId);
        return o != null ? o.lore() : null;
    }

    /**
     * 指定itemIdのエンチャント光上書き値を返す。未設定の場合はnull（ハードコード既定値にフォールバック）。
     */
    public Boolean enchantGlowOverride(String itemId) {
        Override o = overrides.get(itemId);
        return o != null ? o.enchantGlow() : null;
    }

    /**
     * 指定itemIdの材質上書き値を返す。未設定、または{@link #MATERIAL_OVERRIDE_ALLOWED}対象外の場合はnull。
     */
    public Material materialOverride(String itemId) {
        Override o = overrides.get(itemId);
        return o != null ? o.material() : null;
    }
}
