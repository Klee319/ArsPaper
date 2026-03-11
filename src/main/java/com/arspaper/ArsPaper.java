package com.arspaper;

import com.arspaper.block.CustomBlockListener;
import com.arspaper.block.CustomBlockRegistry;
import com.arspaper.block.impl.Pedestal;
import com.arspaper.block.impl.RitualCore;
import com.arspaper.block.impl.ScribingTable;
import com.arspaper.block.impl.SourceJar;
import com.arspaper.command.ArsCommand;
import com.arspaper.gui.GuiListener;
import com.arspaper.item.*;
import com.arspaper.item.impl.MageArmor;
import com.arspaper.item.impl.SpellBook;
import com.arspaper.item.impl.Wand;
import com.arspaper.mana.ManaConfig;
import com.arspaper.mana.ManaManager;
import com.arspaper.recipe.RecipeManager;
import com.arspaper.ritual.RitualManager;
import com.arspaper.ritual.RitualRecipeRegistry;
import com.arspaper.source.SourceNetwork;
import com.arspaper.source.SourcelinkTickTask;
import com.arspaper.source.sourcelink.MycelialSourcelink;
import com.arspaper.source.sourcelink.VolcanicSourcelink;
import com.arspaper.spell.ProjectileHitListener;
import com.arspaper.spell.SpellRegistry;
import com.arspaper.spell.augment.AccelerateAugment;
import com.arspaper.spell.augment.AmplifyAugment;
import com.arspaper.spell.augment.AoeAugment;
import com.arspaper.spell.augment.DampenAugment;
import com.arspaper.spell.augment.ExtendTimeAugment;
import com.arspaper.spell.augment.PierceAugment;
import com.arspaper.spell.augment.SplitAugment;
import com.arspaper.spell.effect.*;
import com.arspaper.spell.form.ProjectileForm;
import com.arspaper.spell.form.SelfForm;
import com.arspaper.spell.form.TouchForm;
import com.arspaper.spell.form.UnderfootForm;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public class ArsPaper extends JavaPlugin {

    private static ArsPaper instance;
    private CustomItemRegistry itemRegistry;
    private CustomBlockRegistry blockRegistry;
    private SpellRegistry spellRegistry;
    private ManaManager manaManager;
    private SourceNetwork sourceNetwork;
    private SourcelinkTickTask sourcelinkTickTask;
    private RecipeManager recipeManager;
    private RitualRecipeRegistry ritualRecipeRegistry;
    private RitualManager ritualManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        initRegistries();
        registerListeners();
        registerCommands();

        // レシピ読み込み（アイテム登録後）
        recipeManager = new RecipeManager(this);
        recipeManager.loadRecipes();

        // 儀式レシピ読み込み
        ritualRecipeRegistry = new RitualRecipeRegistry(this);
        ritualRecipeRegistry.loadRecipes();
        ritualManager = new RitualManager(ritualRecipeRegistry);

        getLogger().info("ArsPaper enabled!");
    }

    @Override
    public void onDisable() {
        if (manaManager != null) {
            manaManager.shutdown();
        }
        if (sourceNetwork != null) {
            sourceNetwork.shutdown();
        }
        if (sourcelinkTickTask != null) {
            sourcelinkTickTask.stop();
        }
        if (recipeManager != null) {
            recipeManager.unloadRecipes();
        }
        getLogger().info("ArsPaper disabled!");
    }

    private void initRegistries() {
        // スペルレジストリ
        spellRegistry = new SpellRegistry();
        registerDefaultGlyphs();

        // マナマネージャー
        ManaConfig manaConfig = ManaConfig.fromConfig(getConfig());
        manaManager = new ManaManager(this, manaConfig);

        // カスタムアイテムレジストリ
        itemRegistry = new CustomItemRegistry();

        // カスタムブロックレジストリ
        blockRegistry = new CustomBlockRegistry();
        registerDefaultBlocks();
        registerDefaultItems();

        // Sourceネットワーク
        sourceNetwork = new SourceNetwork(this);

        // SourcelinkティックTask
        sourcelinkTickTask = new SourcelinkTickTask(this, blockRegistry);
        sourcelinkTickTask.start();
    }

    private void registerDefaultGlyphs() {
        // Forms
        spellRegistry.register(new ProjectileForm(this));
        spellRegistry.register(new TouchForm(this));
        spellRegistry.register(new SelfForm(this));
        spellRegistry.register(new UnderfootForm(this));

        // Effects
        spellRegistry.register(new BreakEffect(this));
        spellRegistry.register(new HarmEffect(this));
        spellRegistry.register(new HealEffect(this));
        spellRegistry.register(new GrowEffect(this));
        spellRegistry.register(new LightEffect(this));
        spellRegistry.register(new SpeedEffect(this));
        spellRegistry.register(new BlinkEffect(this));
        spellRegistry.register(new BounceEffect(this));
        spellRegistry.register(new IgniteEffect(this));
        spellRegistry.register(new FreezeEffect(this));
        spellRegistry.register(new LightningEffect(this));
        spellRegistry.register(new KnockbackEffect(this));
        spellRegistry.register(new ShieldEffect(this));
        spellRegistry.register(new SnareEffect(this));

        // Augments
        spellRegistry.register(new AmplifyAugment(this));
        spellRegistry.register(new AoeAugment(this));
        spellRegistry.register(new ExtendTimeAugment(this));
        spellRegistry.register(new PierceAugment(this));
        spellRegistry.register(new DampenAugment(this));
        spellRegistry.register(new AccelerateAugment(this));
        spellRegistry.register(new SplitAugment(this));
    }

    private void registerDefaultItems() {
        // Spell Books (3 tiers)
        for (SpellBookTier tier : SpellBookTier.values()) {
            itemRegistry.register(new SpellBook(this, spellRegistry, tier));
        }

        itemRegistry.register(new Wand(this));

        // Mage Armor (3 tiers × 4 slots)
        for (ArmorTier armorTier : ArmorTier.values()) {
            for (ArmorSlot armorSlot : ArmorSlot.values()) {
                itemRegistry.register(new MageArmor(this, armorTier, armorSlot));
            }
        }
    }

    private void registerDefaultBlocks() {
        ScribingTable scribingTable = new ScribingTable(this);
        SourceJar sourceJar = new SourceJar(this);
        VolcanicSourcelink volcanicSourcelink = new VolcanicSourcelink(this);
        MycelialSourcelink mycelialSourcelink = new MycelialSourcelink(this);
        RitualCore ritualCore = new RitualCore(this);
        Pedestal pedestal = new Pedestal(this);

        blockRegistry.register(scribingTable);
        blockRegistry.register(sourceJar);
        blockRegistry.register(volcanicSourcelink);
        blockRegistry.register(mycelialSourcelink);
        blockRegistry.register(ritualCore);
        blockRegistry.register(pedestal);

        // カスタムブロックもアイテムとして取得できるようにする
        itemRegistry.register(scribingTable);
        itemRegistry.register(sourceJar);
        itemRegistry.register(volcanicSourcelink);
        itemRegistry.register(mycelialSourcelink);
        itemRegistry.register(ritualCore);
        itemRegistry.register(pedestal);
    }

    private void registerListeners() {
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new CustomItemListener(itemRegistry), this);
        pluginManager.registerEvents(new CustomBlockListener(this, blockRegistry), this);
        pluginManager.registerEvents(new ProjectileHitListener(), this);
        pluginManager.registerEvents(new GuiListener(), this);
        pluginManager.registerEvents(manaManager, this);
        pluginManager.registerEvents(new ArmorManaListener(this), this);
    }

    @SuppressWarnings("UnstableApiUsage")
    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            ArsCommand.register(commands, this);
        });
    }

    public static ArsPaper getInstance() {
        return instance;
    }

    public CustomItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public CustomBlockRegistry getBlockRegistry() {
        return blockRegistry;
    }

    public SpellRegistry getSpellRegistry() {
        return spellRegistry;
    }

    public ManaManager getManaManager() {
        return manaManager;
    }

    public SourceNetwork getSourceNetwork() {
        return sourceNetwork;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public RitualManager getRitualManager() {
        return ritualManager;
    }

    public RitualRecipeRegistry getRitualRecipeRegistry() {
        return ritualRecipeRegistry;
    }
}
