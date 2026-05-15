package com.arspaper.api;

import com.arspaper.ArsPaper;
import com.arspaper.api.event.ArsGlyphLockedEvent;
import com.arspaper.api.event.ArsGlyphUnlockedEvent;
import com.arspaper.api.event.ArsManaModifierChangeEvent;
import com.arspaper.api.event.LockSource;
import com.arspaper.api.event.UnlockSource;
import com.arspaper.api.internal.MagicDamageMarker;
import com.arspaper.api.modifier.ModifierStore;
import com.arspaper.api.modifier.ModifierType;
import com.arspaper.api.quality.ItemQuality;
import com.arspaper.api.quality.QualityHelper;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.SpellComponent;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ArsPaper 公開API。
 *
 * 外部プラグイン(ValhallaMMO/EliteMobs等)は本クラス経由でのみ ArsPaper を操作する。
 * 内部実装に直接依存してはならない。
 *
 * Lifecycle: ArsPaper#onEnable() で ApiBootstrap.init() が ModifierStore と
 * PlayerDataStore を生成して内部参照を確立する。
 */
public final class ArsAPI {

    private ArsAPI() {}

    private static ArsPaper plugin;
    private static ModifierStore modifierStore;
    private static ArsPlayerDataStore playerDataStore;
    private static boolean initialized = false;

    /** ApiBootstrapから呼ばれる初期化メソッド。1回のみ。 */
    static void init(ArsPaper p, ModifierStore mods, ArsPlayerDataStore store) {
        plugin = p;
        modifierStore = mods;
        playerDataStore = store;
        initialized = true;
    }

    public static boolean isInitialized() { return initialized; }

    private static void ensureInit() {
        if (!initialized) throw new IllegalStateException("ArsAPI not initialized yet");
    }

    // ============================================================
    // Glyph
    // ============================================================

    public static boolean isGlyphUnlocked(Player p, String glyphId) {
        return getUnlockedGlyphs(p).contains(glyphId);
    }

    public static void unlockGlyph(Player p, String glyphId) {
        ensureInit();
        Set<String> set = readGlyphSet(p);
        if (set.add(glyphId)) {
            writeGlyphSet(p, set);
            plugin.getSpellCaster().invalidateGlyphCache(p.getUniqueId());
            plugin.getServer().getPluginManager().callEvent(
                new ArsGlyphUnlockedEvent(p, glyphId, UnlockSource.API));
        }
    }

    public static void lockGlyph(Player p, String glyphId) {
        ensureInit();
        Set<String> set = readGlyphSet(p);
        if (set.remove(glyphId)) {
            writeGlyphSet(p, set);
            plugin.getSpellCaster().invalidateGlyphCache(p.getUniqueId());
            plugin.getServer().getPluginManager().callEvent(
                new ArsGlyphLockedEvent(p, glyphId, LockSource.API));
        }
    }

    public static Set<String> getUnlockedGlyphs(Player p) {
        return Collections.unmodifiableSet(readGlyphSet(p));
    }

    public static Set<String> getAllGlyphIds() {
        ensureInit();
        Set<String> ids = new HashSet<>();
        for (SpellComponent c : plugin.getSpellRegistry().getAll()) {
            ids.add(c.getId().toString());
        }
        return Collections.unmodifiableSet(ids);
    }

    public static @Nullable String getGlyphIdFromItem(ItemStack item) {
        // 現状ArsPaperにはGlyphをアイテム化する機能がないためnull。将来用フック。
        return null;
    }

    private static Set<String> readGlyphSet(Player p) {
        String json = p.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        if (json == null) return new HashSet<>();
        Set<String> set = new HashSet<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            arr.forEach(el -> set.add(el.getAsString()));
        } catch (Exception e) {
            plugin.getLogger().warning("Corrupted UNLOCKED_GLYPHS for " + p.getName() + ": " + e.getMessage());
        }
        return set;
    }

    private static void writeGlyphSet(Player p, Set<String> set) {
        JsonArray arr = new JsonArray();
        set.forEach(arr::add);
        p.getPersistentDataContainer().set(
            ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING, arr.toString());
    }

    // ============================================================
    // Ritual / Recipe unlock
    // ============================================================

    public static boolean isRitualUnlocked(Player p, String ritualId) {
        ensureInit();
        return playerDataStore.isRitualUnlocked(p.getUniqueId(), ritualId);
    }

    public static void unlockRitual(Player p, String ritualId) {
        ensureInit();
        playerDataStore.unlockRitual(p.getUniqueId(), ritualId);
        playerDataStore.save(p);
    }

    public static void lockRitual(Player p, String ritualId) {
        ensureInit();
        playerDataStore.lockRitual(p.getUniqueId(), ritualId);
        playerDataStore.save(p);
    }

    public static boolean isRecipeUnlocked(Player p, String recipeId) {
        ensureInit();
        return playerDataStore.isRecipeUnlocked(p.getUniqueId(), recipeId);
    }

    public static void unlockRecipe(Player p, String recipeId) {
        ensureInit();
        playerDataStore.unlockRecipe(p.getUniqueId(), recipeId);
        playerDataStore.save(p);
    }

    public static void lockRecipe(Player p, String recipeId) {
        ensureInit();
        playerDataStore.lockRecipe(p.getUniqueId(), recipeId);
        playerDataStore.save(p);
    }

    public static Set<String> getAllRitualIds() {
        ensureInit();
        Set<String> ids = new HashSet<>();
        var reg = plugin.getRitualRecipeRegistry();
        if (reg != null) {
            for (var recipe : reg.getAll()) {
                ids.add(recipe.id());
            }
        }
        return ids;
    }

    public static Set<String> getAllRecipeIds() {
        ensureInit();
        Set<String> ids = new HashSet<>();
        if (plugin.getRecipeManager() != null) {
            ids.addAll(plugin.getRecipeManager().getRegisteredRecipeIds());
        }
        return ids;
    }

    // ============================================================
    // Mana / Cost / Material Modifier
    // ============================================================

    public static double getMaxMana(Player p) {
        ensureInit();
        return plugin.getManaManager().getMaxMana(p);
    }

    public static double getManaRegenRate(Player p) {
        ensureInit();
        return plugin.getManaManager().getRegenRateForApi(p);
    }

    public static void addManaModifier(Player p, NamespacedKey key, ModifierType type, double value) {
        ensureInit();
        Double oldValue = modifierStore.put(p.getUniqueId(), key, type, value);
        if (playerDataStore.isPersistModifiers()) playerDataStore.save(p);
        plugin.getServer().getPluginManager().callEvent(
            new ArsManaModifierChangeEvent(p, key, type, oldValue, value));
    }

    public static void removeManaModifier(Player p, NamespacedKey key, ModifierType type) {
        ensureInit();
        Double oldValue = modifierStore.remove(p.getUniqueId(), key, type);
        if (oldValue == null) return;
        if (playerDataStore.isPersistModifiers()) playerDataStore.save(p);
        plugin.getServer().getPluginManager().callEvent(
            new ArsManaModifierChangeEvent(p, key, type, oldValue, null));
    }

    public static @Nullable Double getManaModifier(Player p, NamespacedKey key, ModifierType type) {
        ensureInit();
        return modifierStore.get(p.getUniqueId(), key, type);
    }

    public static Map<NamespacedKey, Double> listModifiers(Player p, ModifierType type) {
        ensureInit();
        return new LinkedHashMap<>(modifierStore.list(p.getUniqueId(), type));
    }

    /** 内部利用: ManaManager等が ModifierStore に直接アクセスするためのフック。 */
    public static ModifierStore internalModifierStore() {
        ensureInit();
        return modifierStore;
    }

    public static ArsPlayerDataStore internalPlayerDataStore() {
        ensureInit();
        return playerDataStore;
    }

    // ============================================================
    // Item Registry
    // ============================================================

    public static @Nullable ItemStack getItem(String itemId) {
        ensureInit();
        return plugin.getItemRegistry().get(itemId)
            .map(BaseCustomItem::createItemStack)
            .orElse(null);
    }

    public static Set<String> getItemRegistry() {
        ensureInit();
        Set<String> ids = new HashSet<>();
        for (BaseCustomItem b : plugin.getItemRegistry().getAll()) {
            ids.add(b.getItemId());
        }
        return ids;
    }

    public static @Nullable String getItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
    }

    // ============================================================
    // Item Quality
    // ============================================================

    public static int getItemQuality(ItemStack stack) {
        return QualityHelper.get(stack);
    }

    public static void setItemQuality(ItemStack stack, int quality) {
        QualityHelper.set(stack, ItemQuality.clamp(quality));
    }

    // ============================================================
    // Magic Damage Marker
    // ============================================================

    public static boolean isMagicDamage(EntityDamageEvent e) {
        ensureInit();
        return MagicDamageMarker.isMagic(e, plugin);
    }

    public static @Nullable Player getCaster(EntityDamageEvent e) {
        ensureInit();
        if (!(e.getEntity() instanceof LivingEntity le)) return null;
        return MagicDamageMarker.getCaster(le, plugin);
    }

    public static @Nullable Player getCaster(LivingEntity victim) {
        ensureInit();
        return MagicDamageMarker.getCaster(victim, plugin);
    }

    /** 内部利用: ダメージEffectがmarker付与する際のフック。 */
    public static void attachMagicMarker(LivingEntity target, Player caster) {
        ensureInit();
        MagicDamageMarker.attach(plugin, target, caster);
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    public static void reloadPlayerData(Player p) {
        ensureInit();
        playerDataStore.unload(p.getUniqueId());
        playerDataStore.load(p);
    }

    public static void savePlayerData(Player p) {
        ensureInit();
        playerDataStore.save(p);
    }

    public static void saveAllPlayerData() {
        ensureInit();
        playerDataStore.saveAll();
    }

    /** 共通 helper: 指定UUIDの ModifierStore 集計 (パッケージ内利用). */
    static ModifierStore store() {
        ensureInit();
        return modifierStore;
    }

    /** 共通 helper: PlayerDataStore取得 (パッケージ内利用). */
    static ArsPlayerDataStore playerData() {
        ensureInit();
        return playerDataStore;
    }

    /** 共通 helper: plugin取得 (パッケージ内利用). */
    public static ArsPaper plugin() {
        ensureInit();
        return plugin;
    }

    /** key+typeで現プレイヤーのModifier合計 (集計用). */
    public static double sumModifier(UUID uuid, ModifierType type) {
        ensureInit();
        return modifierStore.sum(uuid, type);
    }
}
