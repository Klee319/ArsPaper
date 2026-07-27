package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.item.impl.ConfigurableMaterial;
import com.arspaper.item.impl.SpellBook;
import com.arspaper.util.PdcHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import io.papermc.paper.event.entity.EntityCompostItemEvent;
import io.papermc.paper.event.block.CompostItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * カスタムアイテムのインタラクションイベントを各BaseCustomItemにディスパッチするリスナー。
 * クラフト・金床でのカスタムアイテム使用もブロックする。
 */
public class CustomItemListener implements Listener {

    private final CustomItemRegistry registry;
    /** スニーク+ドロップによるスロット切替直後のGUI開放を防止 */
    private final java.util.Set<java.util.UUID> dropCooldown = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public CustomItemListener(CustomItemRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // オフハンドの重複呼び出しを無視
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Optional<String> customId = PdcHelper.getCustomItemId(item);
        if (customId.isEmpty()) return;

        Optional<BaseCustomItem> customItem = registry.get(customId.get());
        if (customItem.isEmpty()) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            customItem.get().onRightClick(event);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // スニーク+ドロップ直後はGUI開放を抑制
            if (dropCooldown.contains(event.getPlayer().getUniqueId())) return;
            customItem.get().onLeftClick(event);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Optional<String> customId = PdcHelper.getCustomItemId(item);
        if (customId.isEmpty()) return;

        Optional<BaseCustomItem> customItem = registry.get(customId.get());
        if (customItem.isEmpty()) return;

        customItem.get().onPlace(event);
    }

    /**
     * スペルブックのスニーク+ドロップで前のスロットに切り替える。
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack droppedItem = event.getItemDrop().getItemStack();
        Optional<String> customId = PdcHelper.getCustomItemId(droppedItem);
        if (customId.isEmpty()) return;

        String id = customId.get();
        if (!id.startsWith("spell_book_")) return;

        // ドロップをキャンセルしてアイテムを戻す
        event.setCancelled(true);

        // sneak+左クリック(GUI開放)との同時発火を防止（5tick猶予）
        dropCooldown.add(player.getUniqueId());
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            org.bukkit.Bukkit.getPluginManager().getPlugin("ArsPaper"),
            () -> dropCooldown.remove(player.getUniqueId()), 5L);

        int tier = droppedItem.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.BOOK_TIER, PersistentDataType.INTEGER, 1);
        // 要件⑥ glyph-slot-plus: SpellBook#switchSlotと対称にperk加算分を反映する(fail-open)。
        int maxSlots = ArsPaper.getInstance().getSpellBookConfig().byTier(tier).getMaxSlots()
            + TrinityForgeBridge.tfGlyphSlotBonus(player);
        int current = droppedItem.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, 0);

        int prev = (current - 1 + maxSlots) % maxSlots;
        droppedItem.editMeta(meta ->
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, prev
            )
        );

        // スロット名表示
        Optional<BaseCustomItem> customItem = registry.get(id);
        if (customItem.isPresent() && customItem.get() instanceof SpellBook book) {
            String slotName = book.getSlotSpellName(droppedItem, prev);
            player.sendActionBar(
                Component.text("§d" + slotName + " §7(スロット" + (prev + 1) + ")")
            );
        }
    }

    /**
     * カスタムアイテムをバニラクラフトの素材として使用するのを防止する。
     * ただしプラグイン登録レシピ（arspaper namespace）と革防具染色は許可する。
     */
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        // プラグイン登録レシピはカスタム素材を意図的に使用するので許可
        if (isPluginRecipe(event.getRecipe())) {
            return;
        }
        // カスタム革防具の染色は許可
        if (isDyeingCustomArmor(event.getInventory().getMatrix())) {
            return;
        }
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && PdcHelper.getCustomItemId(item).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * 染色プレビュー: カスタム革防具をクラフトテーブルで染色する際、
     * 元の防具のPDCデータ・表示名・ロア等を保全した結果を生成する。
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();

        // バニラ/外部レシピが materials.yml 素材を消費しないようプレビューを空にする
        if (event.getRecipe() != null && !isPluginRecipe(event.getRecipe())) {
            for (ItemStack item : matrix) {
                if (isConfigurableMaterial(item)) {
                    event.getInventory().setResult(null);
                    return;
                }
            }
        }

        if (!isDyeingCustomArmor(matrix)) return;

        ItemStack vanillaResult = event.getInventory().getResult();
        if (vanillaResult == null || vanillaResult.getType().isAir()) return;

        // クラフトマトリクスからカスタム防具を探す
        ItemStack customArmor = null;
        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir() && isMageArmor(item)) {
                customArmor = item;
                break;
            }
        }
        if (customArmor == null) return;

        // 元の防具をクローンし、バニラ結果から染色色のみ適用
        ItemStack result = customArmor.clone();
        if (vanillaResult.getItemMeta() instanceof LeatherArmorMeta vanillaMeta
                && result.getItemMeta() instanceof LeatherArmorMeta) {
            org.bukkit.Color newColor = vanillaMeta.getColor();
            result.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(newColor));
        }
        event.getInventory().setResult(result);
    }

    /**
     * 砥石: materials.yml 素材はバニラ消費（エンチャ除去等）を禁止する。
     * TrinityForge 装備の PDC 保全は {@code GrindstonePreserveListener} 側で行う。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack upper = event.getInventory().getItem(0);
        ItemStack lower = event.getInventory().getItem(1);
        if (isConfigurableMaterial(upper) || isConfigurableMaterial(lower)) {
            event.setResult(null);
        }
    }

    /**
     * かまど系の燃料スロットに materials.yml 素材を入れさせない。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (isConfigurableMaterial(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    /**
     * コンポスターに materials.yml 素材を投入させない（プレイヤー/村人等）。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityCompost(EntityCompostItemEvent event) {
        if (isConfigurableMaterial(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * ホッパー経由のコンポスター投入も禁止（CompostItemEvent 自体は非キャンセル可能）。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperCompost(CompostItemEvent event) {
        if (isConfigurableMaterial(event.getItem())) {
            event.setWillRaiseLevel(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperToComposter(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() != InventoryType.COMPOSTER) {
            return;
        }
        if (isConfigurableMaterial(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * 食べ物ベースの materials.yml 素材の直接消費を禁止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsumeMaterial(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (isConfigurableMaterial(item) && isMaterialEdibleBase(item)) {
            event.setCancelled(true);
        }
    }

    /**
     * 醸造台の燃料に materials.yml 素材を使わせない。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewingFuel(BrewingStandFuelEvent event) {
        if (isConfigurableMaterial(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    /**
     * かまど/醸造台インベントリへ materials.yml 素材を置かせない。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaMachineClick(InventoryClickEvent event) {
        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.FURNACE
                && type != InventoryType.BLAST_FURNACE
                && type != InventoryType.SMOKER
                && type != InventoryType.BREWING) {
            return;
        }
        if (isConfigurableMaterial(event.getCursor())
                || isConfigurableMaterial(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * 鍛冶台でのカスタムアイテム使用を制御する。
     * カスタム防具は鍛冶型（アーマートリム）の適用を許可し、PDCデータを保全する。
     * それ以外のカスタムアイテムはブロック。
     */
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack customArmorInput = null;

        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (PdcHelper.getCustomItemId(item).isEmpty()) continue;

            if (isMageArmor(item)) {
                customArmorInput = item;
            } else {
                // 防具以外のカスタムアイテム → ブロック
                event.setResult(null);
                return;
            }
        }

        // カスタム防具がある場合、元の防具をクローンしてトリムだけ適用する
        // これによりPDC・エンチャント・耐久・スレッド等すべてのデータが完全に保全される
        if (customArmorInput != null) {
            ItemStack vanillaResult = event.getResult();
            if (vanillaResult != null && !vanillaResult.getType().isAir()) {
                ItemStack result = customArmorInput.clone();
                // バニラ結果からアーマートリムを取得して適用
                if (vanillaResult.getItemMeta() instanceof ArmorMeta vanillaArmorMeta) {
                    ArmorTrim trim = vanillaArmorMeta.getTrim();
                    if (trim != null) {
                        result.editMeta(ArmorMeta.class, meta -> meta.setTrim(trim));
                    }
                }
                event.setResult(result);
            }
        }
    }

    /**
     * カスタムアイテムを金床で使用するのを防止する。
     * ただし防具アイテム（mage_*、ARMOR_SET_ID付き）は修理・エンチャント可能。
     * メイジ防具同士の金床修理もサポート（バニラがCustomModelDataで弾くため）。
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        org.bukkit.inventory.ItemStack slot0 = event.getInventory().getItem(0);
        org.bukkit.inventory.ItemStack slot1 = event.getInventory().getItem(1);

        // メイジ防具同士の修理: 同じMaterialのカスタム防具 → 耐久回復
        if (isMageArmor(slot0) && slot1 != null && !slot1.getType().isAir()
                && slot0.getType() == slot1.getType() && isMageArmor(slot1)) {
            if (event.getResult() == null || event.getResult().getType().isAir()) {
                ItemStack repaired = slot0.clone();
                if (repaired.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    int dmg0 = damageable.getDamage();
                    int dmg1 = slot1.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d1 ? d1.getDamage() : 0;
                    int maxDmg = damageable.hasMaxDamage() ? damageable.getMaxDamage() : repaired.getType().getMaxDurability();
                    int newDmg = Math.max(0, dmg0 - (maxDmg - dmg1) - maxDmg / 20);
                    repaired.editMeta(org.bukkit.inventory.meta.Damageable.class,
                        meta -> meta.setDamage(newDmg));
                    event.setResult(repaired);
                }
            }
            return;
        }

        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null) continue;
            Optional<String> id = PdcHelper.getCustomItemId(item);
            if (id.isPresent()
                && !id.get().startsWith("mage_")
                && !id.get().startsWith("spell_book_")
                && !"enchant_book".equals(id.get())
                && !item.getItemMeta().getPersistentDataContainer().has(ItemKeys.ARMOR_SET_ID, org.bukkit.persistence.PersistentDataType.STRING)) {
                event.setResult(null);
                return;
            }
        }
    }

    /**
     * カスタムアイテムを村人取引に使用するのを防止する。
     * 魔導書(BOOK)等がバニラ取引の素材として吸われるのを防ぐ。
     */
    @EventHandler
    public void onMerchantClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 結果スロット(slot 2)をクリック → 入力スロットのカスタムアイテムチェック
        if (event.getRawSlot() == 2) {
            MerchantInventory merchant = (MerchantInventory) event.getInventory();
            for (int i = 0; i < 2; i++) {
                ItemStack input = merchant.getItem(i);
                if (input != null && PdcHelper.getCustomItemId(input).isPresent()) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text(
                        "カスタムアイテムは取引に使用できません", net.kyori.adventure.text.format.NamedTextColor.RED));
                    return;
                }
            }
        }

        // Shift+クリックでカスタムアイテムを取引スロットに移動するのを防止
        if (event.isShiftClick() && event.getRawSlot() >= event.getInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && PdcHelper.getCustomItemId(clicked).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * アレイ等のMobにカスタムアイテム（スペルブック等）を渡すのを防止する。
     */
    @EventHandler
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof org.bukkit.entity.Allay)) return;

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item != null && PdcHelper.getCustomItemId(item).isPresent()) {
            event.setCancelled(true);
        }
    }

    private boolean isMageArmor(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String customId = pdc.get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        if (customId != null && customId.startsWith("mage_")) return true;
        return pdc.has(ItemKeys.ARMOR_SET_ID, PersistentDataType.STRING);
    }

    /**
     * クラフトマトリクスにカスタム防具+染料が含まれるかを判定する。
     */
    private boolean isDyeingCustomArmor(ItemStack[] matrix) {
        boolean hasCustomArmor = false;
        boolean hasDye = false;
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) continue;
            if (isMageArmor(item) && isLeatherArmor(item.getType())) hasCustomArmor = true;
            if (isDye(item.getType())) hasDye = true;
        }
        return hasCustomArmor && hasDye;
    }

    private static boolean isDye(Material mat) {
        return mat.name().endsWith("_DYE");
    }

    private static boolean isLeatherArmor(Material mat) {
        return mat == Material.LEATHER_HELMET || mat == Material.LEATHER_CHESTPLATE
            || mat == Material.LEATHER_LEGGINGS || mat == Material.LEATHER_BOOTS;
    }

    /** arspaper / trinityforge 名前空間のプラグインレシピか。 */
    private static boolean isPluginRecipe(Recipe recipe) {
        if (!(recipe instanceof org.bukkit.Keyed keyed)) {
            return false;
        }
        String ns = keyed.getKey().getNamespace();
        return "arspaper".equals(ns) || "trinityforge".equals(ns);
    }

    /**
     * materials.yml 由来の ConfigurableMaterial か。
     * registry の型判定を優先し、未登録 id は MaterialConfigManager でフォールバックする。
     */
    private boolean isConfigurableMaterial(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Optional<String> customId = PdcHelper.getCustomItemId(item);
        if (customId.isEmpty()) {
            return false;
        }
        Optional<BaseCustomItem> customItem = registry.get(customId.get());
        if (customItem.isPresent()) {
            return customItem.get() instanceof ConfigurableMaterial;
        }
        return ArsPaper.getInstance().getMaterialConfigManager().get(customId.get()).isPresent();
    }

    /** materials.yml 素材の base_material が食べ物か。 */
    private boolean isMaterialEdibleBase(ItemStack item) {
        Optional<String> customId = PdcHelper.getCustomItemId(item);
        if (customId.isEmpty()) {
            return false;
        }
        return ArsPaper.getInstance().getMaterialConfigManager().get(customId.get())
            .map(cfg -> cfg.baseMaterial().isEdible())
            .orElseGet(() -> {
                Optional<BaseCustomItem> customItem = registry.get(customId.get());
                return customItem.filter(ConfigurableMaterial.class::isInstance)
                    .map(ci -> ((ConfigurableMaterial) ci).getBaseMaterial().isEdible())
                    .orElse(false);
            });
    }

}
