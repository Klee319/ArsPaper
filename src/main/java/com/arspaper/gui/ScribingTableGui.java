package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaConfig;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.SpellComponent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scribing Table のGUI。
 * BaseGuiを継承し、GuiListenerで統一管理される（リスナーリーク防止）。
 * Geyser互換のためボタン式（クリックイベントベース）で設計。
 */
public class ScribingTableGui extends BaseGui {

    private static final Gson GSON = new Gson();
    private final ArsPaper plugin;
    private final Location tableLocation;
    private final List<SpellComponent> displayedGlyphs = new ArrayList<>();

    public ScribingTableGui(ArsPaper plugin, Player player, Location tableLocation) {
        super(player, 6, Component.text("筆記台", NamedTextColor.DARK_AQUA));
        this.plugin = plugin;
        this.tableLocation = tableLocation;
    }

    @Override
    public void render() {
        inventory.clear();
        displayedGlyphs.clear();

        Set<String> unlocked = getUnlockedGlyphs();

        // 装飾用ガラスパネルで上下段を埋める
        fillRow(0, Material.GRAY_STAINED_GLASS_PANE);
        fillRow(5, Material.GRAY_STAINED_GLASS_PANE);

        // グリフをボタンとして配置
        int slot = 9;
        for (SpellComponent component : plugin.getSpellRegistry().getAll()) {
            if (slot >= 45) break;

            boolean isUnlocked = unlocked.contains(component.getId().toString());
            inventory.setItem(slot, createGlyphButton(component, isUnlocked));
            displayedGlyphs.add(component);
            slot++;
        }
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        if (slot < 9 || slot >= 45) return false;

        int glyphIndex = slot - 9;
        if (glyphIndex >= displayedGlyphs.size()) return false;

        SpellComponent component = displayedGlyphs.get(glyphIndex);
        Set<String> unlocked = getUnlockedGlyphs();

        if (unlocked.contains(component.getId().toString())) {
            clicker.sendMessage(Component.text("すでに解放済みです！", NamedTextColor.YELLOW));
            return true;
        }

        if (!tryPayUnlockCost(clicker, component)) {
            return true;
        }

        // アンロック
        unlocked.add(component.getId().toString());
        saveUnlockedGlyphs(unlocked);

        // グリフマナボーナス付与（config値を使用）
        int currentBonus = clicker.getPersistentDataContainer()
            .getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int perGlyphBonus = plugin.getConfig().getInt("mana.per-glyph-unlock-bonus", 5);
        clicker.getPersistentDataContainer().set(
            ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, currentBonus + perGlyphBonus
        );

        clicker.sendMessage(Component.text(
            "解放: " + component.getDisplayName() + "！ (最大マナ+" + perGlyphBonus + ")",
            NamedTextColor.GREEN
        ));

        // GUIを再描画
        render();
        return true;
    }

    private ItemStack createGlyphButton(SpellComponent component, boolean unlocked) {
        Material material = switch (component.getType()) {
            case FORM -> unlocked ? Material.DIAMOND : Material.COAL;
            case EFFECT -> unlocked ? Material.EMERALD : Material.COAL;
            case AUGMENT -> unlocked ? Material.AMETHYST_SHARD : Material.COAL;
        };

        NamedTextColor typeColor = switch (component.getType()) {
            case FORM -> NamedTextColor.GREEN;
            case EFFECT -> NamedTextColor.YELLOW;
            case AUGMENT -> NamedTextColor.LIGHT_PURPLE;
        };

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("種類: " + localizeType(component.getType()), typeColor)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("マナコスト: " + component.getManaCost(), NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("ティア: " + component.getTier(), NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        if (!unlocked) {
            lore.add(Component.empty());
            lore.add(Component.text("クリックで解放！", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("必要: " + getUnlockCostDescription(component), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }

        return createButton(material,
            Component.text(
                (unlocked ? "[解放済] " : "") + component.getDisplayName(),
                unlocked ? NamedTextColor.GREEN : NamedTextColor.RED
            ),
            lore);
    }

    private String getUnlockCostDescription(SpellComponent component) {
        return switch (component.getTier()) {
            case 1 -> "5レベル + ラピスラズリ1個";
            case 2 -> "10レベル + ラピスラズリ4個";
            case 3 -> "20レベル + ラピスラズリ8個";
            default -> "不明";
        };
    }

    private String localizeType(SpellComponent.ComponentType type) {
        return switch (type) {
            case FORM -> "形態";
            case EFFECT -> "効果";
            case AUGMENT -> "増強";
        };
    }

    private boolean tryPayUnlockCost(Player player, SpellComponent component) {
        int levelCost = switch (component.getTier()) {
            case 1 -> 5;
            case 2 -> 10;
            case 3 -> 20;
            default -> 30;
        };
        int lapisCost = switch (component.getTier()) {
            case 1 -> 1;
            case 2 -> 4;
            case 3 -> 8;
            default -> 16;
        };

        if (player.getLevel() < levelCost) {
            player.sendMessage(Component.text(
                "レベルが不足しています！必要: " + levelCost, NamedTextColor.RED
            ));
            return false;
        }

        if (!player.getInventory().contains(Material.LAPIS_LAZULI, lapisCost)) {
            player.sendMessage(Component.text(
                "ラピスラズリが不足しています！必要: " + lapisCost + "個", NamedTextColor.RED
            ));
            return false;
        }

        player.setLevel(player.getLevel() - levelCost);
        player.getInventory().removeItem(new ItemStack(Material.LAPIS_LAZULI, lapisCost));
        return true;
    }

    private Set<String> getUnlockedGlyphs() {
        String json = viewer.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        if (json == null) return new HashSet<>();

        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        Set<String> result = new HashSet<>();
        arr.forEach(el -> result.add(el.getAsString()));
        return result;
    }

    private void saveUnlockedGlyphs(Set<String> glyphs) {
        JsonArray arr = new JsonArray();
        glyphs.forEach(arr::add);
        viewer.getPersistentDataContainer().set(
            ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING, GSON.toJson(arr)
        );
    }
}
