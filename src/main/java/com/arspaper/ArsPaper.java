package com.arspaper;

import com.arspaper.block.BlockParticleTask;
import com.arspaper.block.CustomBlockListener;
import com.arspaper.block.CustomBlockRegistry;
import com.arspaper.block.impl.CreativeSourceJar;
import com.arspaper.block.impl.Pedestal;
import com.arspaper.block.impl.RitualCore;
import com.arspaper.block.impl.ScribingTable;
import com.arspaper.block.impl.SourceJar;
import com.arspaper.block.impl.Waystone;
import com.arspaper.command.ArsCommand;
import com.arspaper.gui.GuiListener;
import com.arspaper.item.*;
import com.arspaper.item.impl.ConfigurableArmor;
import com.arspaper.item.impl.MageArmor;
import com.arspaper.item.impl.SourceBerry;
import com.arspaper.item.impl.SpellBook;
// SpellWand は廃止（アイテムバインドで代替）
import com.arspaper.item.impl.ThreadItem;
import com.arspaper.item.impl.TeleportCompass;
import com.arspaper.item.impl.Wand;
import com.arspaper.mana.ManaConfig;
import com.arspaper.mana.ManaManager;
import com.arspaper.recipe.RecipeManager;
import com.arspaper.ritual.RitualEffectRegistry;
import com.arspaper.ritual.RitualManager;
import com.arspaper.ritual.RitualRecipeRegistry;
import com.arspaper.ritual.effect.*;

import com.arspaper.source.SourceNetwork;
import com.arspaper.source.SourcelinkTickTask;
import com.arspaper.source.sourcelink.AlchemicalSourcelink;
import com.arspaper.source.sourcelink.BotanicalSourcelink;
import com.arspaper.source.sourcelink.MycelialSourcelink;
import com.arspaper.source.sourcelink.VitalicSourcelink;
import com.arspaper.source.sourcelink.VolcanicSourcelink;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.PhantomBlockListener;
import com.arspaper.spell.ProjectileHitListener;
import com.arspaper.spell.SpellCaster;
import com.arspaper.spell.SpellRegistry;
import com.arspaper.spell.SpellTaskLimiter;
import com.arspaper.spell.SummonedMobListener;
import com.arspaper.spell.augment.*;
import com.arspaper.spell.augment.LingerAugment;
import com.arspaper.spell.augment.PropagateAugment;
import com.arspaper.spell.augment.TrailAugment;
import com.arspaper.spell.effect.*;
import com.arspaper.spell.effect.LingerEffect;
import com.arspaper.spell.effect.PhantomBlockEffect;
import com.arspaper.spell.effect.RotateEffect;
import com.arspaper.spell.form.*;
import com.arspaper.spell.form.BeamForm;
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
    private RitualEffectRegistry ritualEffectRegistry;
    private RitualManager ritualManager;
    private BlockParticleTask blockParticleTask;
    private GlyphConfig glyphConfig;
    private SpellCaster spellCaster;
    private ArmorConfigManager armorConfigManager;
    private MaterialConfigManager materialConfigManager;
    private ArmorManaListener armorManaListener;
    private ThreadConfig threadConfig;
    private com.arspaper.loot.LootTableListener lootTableListener;

    @Override
    public void onEnable() {
        instance = this;
        updateResourceFiles();
        saveDefaultConfig();
        com.arspaper.util.JaTranslations.load(getLogger());

        initRegistries();
        registerListeners();
        registerCommands();

        // 統合レシピ読み込み（アイテム登録後）
        com.arspaper.recipe.UnifiedRecipeLoader recipeLoader = new com.arspaper.recipe.UnifiedRecipeLoader(this);
        recipeLoader.loadAll();

        recipeManager = new RecipeManager(this);
        recipeManager.registerWorkbenchRecipes(recipeLoader.getWorkbenchRecipes());
        recipeManager.registerArmorRecipes(armorConfigManager);

        // 儀式エフェクトレジストリ
        ritualEffectRegistry = new RitualEffectRegistry();
        ritualEffectRegistry.register("weather", new WeatherRitualEffect());
        ritualEffectRegistry.register("thread", new ThreadRitualEffect());
        FlightRitualEffect flightEffect = new FlightRitualEffect();
        getServer().getPluginManager().registerEvents(flightEffect, this);
        ritualEffectRegistry.register("flight", flightEffect);
        ritualEffectRegistry.register("moonfall", new MoonfallRitualEffect());
        ritualEffectRegistry.register("sunrise", new SunriseRitualEffect());
        ritualEffectRegistry.register("repair", new RepairRitualEffect());
        ritualEffectRegistry.register("animal_summon", new AnimalSummonRitualEffect());
        ritualEffectRegistry.register("mob_summon", new MobSummonRitualEffect());
        ritualEffectRegistry.register("enchant_book", new EnchantBookRitualEffect());

        // 儀式レシピ読み込み（UnifiedRecipeLoaderから）
        ritualRecipeRegistry = new RitualRecipeRegistry(this);
        ritualRecipeRegistry.registerRecipes(recipeLoader.getRitualRecipes());
        ritualManager = new RitualManager(ritualRecipeRegistry, ritualEffectRegistry);

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
        if (blockParticleTask != null) {
            blockParticleTask.cancel();
        }
        if (recipeManager != null) {
            recipeManager.unloadRecipes();
        }
        if (ritualManager != null) {
            ritualManager.shutdown();
        }
        // 飛行スレッドタスクをクリーンアップ
        ArmorManaListener.cleanupFlightThread();
        // IgniteEffectの火炎タスクをクリーンアップ
        IgniteEffect.cleanupAll();
        // 一時的なスペル効果をクリーンアップ
        PhantomBlockEffect.cleanupAll();
        LingerEffect.cleanupAll();
        BeamForm.cleanupAll();
        RotateEffect.cleanupAll();
        GlideEffect.restoreAll();
        BounceEffect.cleanupAll();
        ScaleEffect.cleanupAll();
        SpellTaskLimiter.cleanupAll();
        getLogger().info("ArsPaper disabled!");
    }

    /**
     * プラグインバージョンが変わったらリソースYMLを再抽出する。
     * ユーザーのカスタマイズは .bak にバックアップ。
     */
    private void updateResourceFiles() {
        java.io.File versionFile = new java.io.File(getDataFolder(), ".version");
        String currentVersion = getPluginMeta().getVersion();
        String savedVersion = "";

        if (versionFile.exists()) {
            try {
                savedVersion = java.nio.file.Files.readString(versionFile.toPath()).trim();
            } catch (Exception ignored) {}
        }

        if (!currentVersion.equals(savedVersion)) {
            String[] resourceFiles = {"glyphs.yml", "items.yml", "materials.yml", "threads.yml", "armors.yml"};
            for (String name : resourceFiles) {
                java.io.File existing = new java.io.File(getDataFolder(), name);
                if (existing.exists()) {
                    java.io.File backup = new java.io.File(getDataFolder(), name + ".bak");
                    if (backup.exists()) backup.delete();
                    existing.renameTo(backup);
                    getLogger().info("Backed up " + name + " → " + name + ".bak");
                }
                saveResource(name, false);
            }
            // config.ymlは上書きしない（ユーザー設定を保持）

            try {
                java.nio.file.Files.writeString(versionFile.toPath(), currentVersion);
            } catch (Exception e) {
                getLogger().warning("Failed to write version file: " + e.getMessage());
            }
            getLogger().info("Resource files updated to version " + currentVersion);
        }
    }

    private void initRegistries() {
        // グリフ設定
        glyphConfig = new GlyphConfig(this);

        // スペルレジストリ
        spellRegistry = new SpellRegistry();
        registerDefaultGlyphs();

        // エンチャント定数読み込み
        com.arspaper.enchant.ArsEnchantments.loadConfig(getConfig());

        // マナマネージャー
        ManaConfig manaConfig = ManaConfig.fromConfig(getConfig());
        manaManager = new ManaManager(this, manaConfig);

        // スペルキャスター（シングルトン）
        spellCaster = new SpellCaster(manaManager);

        // 防具設定マネージャー
        armorConfigManager = new ArmorConfigManager(this);

        // 素材設定マネージャー
        materialConfigManager = new MaterialConfigManager(this);

        // スレッド設定
        threadConfig = new ThreadConfig(this);

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

        // ブロックパーティクルTask
        blockParticleTask = new BlockParticleTask(this);
        blockParticleTask.start();
    }

    private void registerDefaultGlyphs() {
        // ===== Forms (9種) - 使用頻度・直感順 =====
        spellRegistry.register(new ProjectileForm(this, glyphConfig));   // 投射（最頻出）
        spellRegistry.register(new TouchForm(this, glyphConfig));        // 接触
        spellRegistry.register(new SelfForm(this, glyphConfig));         // 自己
        spellRegistry.register(new UnderfootForm(this, glyphConfig));    // 足元
        spellRegistry.register(new com.arspaper.spell.form.OverheadForm(this, glyphConfig)); // 頭上
        spellRegistry.register(new com.arspaper.spell.form.BurstForm(this, glyphConfig));    // 炸裂
        spellRegistry.register(new OrbitForm(this, glyphConfig));        // 旋回
        spellRegistry.register(new BeamForm(this, glyphConfig));         // 照射

        // ===== Effects - 戦闘（ダメージ/デバフ） =====
        spellRegistry.register(new HarmEffect(this, glyphConfig));       // T1: 害悪
        spellRegistry.register(new IgniteEffect(this, glyphConfig));     // T1: 炎上
        spellRegistry.register(new FreezeEffect(this, glyphConfig));     // T2: 凍結
        spellRegistry.register(new KnockbackEffect(this, glyphConfig));  // T1: 吹飛
        spellRegistry.register(new PullEffect(this, glyphConfig));       // T2: 引寄
        spellRegistry.register(new GravityEffect(this, glyphConfig));    // T2: 重力
        spellRegistry.register(new SnareEffect(this, glyphConfig));      // T1: 拘束
        spellRegistry.register(new ScorchEffect(this, glyphConfig));     // T2: 焦熱
        spellRegistry.register(new ColdSnapEffect(this, glyphConfig));   // T2: 凍裂
        spellRegistry.register(new CrushWaveEffect(this, glyphConfig));  // T2: 粉砕波
        spellRegistry.register(new WindshearEffect(this, glyphConfig));  // T2: 烈風
        spellRegistry.register(new WindBurstEffect(this, glyphConfig));  // T3: 突風
        spellRegistry.register(new LightningEffect(this, glyphConfig));  // T3: 落雷
        spellRegistry.register(new WitherEffect(this, glyphConfig));     // T3: 衰弱
        spellRegistry.register(new HexEffect(this, glyphConfig));        // T3: 呪詛
        spellRegistry.register(new FangsEffect(this, glyphConfig));      // T3: 牙
        spellRegistry.register(new SonicBoomEffect(this, glyphConfig));  // T3: ソニックブーム
        spellRegistry.register(new HeavyImpactEffect(this, glyphConfig));// T3: ヘビーインパクト
        spellRegistry.register(new SolarEffect(this, glyphConfig));      // T3: 日輪
        spellRegistry.register(new LunarEffect(this, glyphConfig));      // T3: 月輪

        // ===== Effects - 移動/ユーティリティ =====
        spellRegistry.register(new LaunchEffect(this, glyphConfig));     // T1: 打ち上げ
        spellRegistry.register(new LeapEffect(this, glyphConfig));       // T1: 跳躍
        spellRegistry.register(new BounceEffect(this, glyphConfig));     // T2: 跳弾
        spellRegistry.register(new SpeedBoostEffect(this, glyphConfig)); // T2: 指向
        spellRegistry.register(new GaleEffect(this, glyphConfig));       // T2: 疾風
        spellRegistry.register(new SlowfallEffect(this, glyphConfig));   // T2: 低速落下
        spellRegistry.register(new LevitateEffect(this, glyphConfig));   // T2: 浮遊
        spellRegistry.register(new ReverseEffect(this, glyphConfig));    // T2: 反転
        spellRegistry.register(new BlinkEffect(this, glyphConfig));      // T3: 瞬間移動
        spellRegistry.register(new GlideEffect(this, glyphConfig));      // T3: 滑空

        // ===== Effects - バフ/回復 =====
        spellRegistry.register(new HealEffect(this, glyphConfig));       // T2: 回復
        spellRegistry.register(new SaturationEffect(this, glyphConfig)); // T2: 満腹
        spellRegistry.register(new ShieldEffect(this, glyphConfig));     // T3: 盾
        spellRegistry.register(new InvisibilityEffect(this, glyphConfig));// T2: 透明
        spellRegistry.register(new BubbleEffect(this, glyphConfig));     // T1: 泡
        spellRegistry.register(new DispelEffect(this, glyphConfig));     // T2: 解呪
        spellRegistry.register(new JourneyEffect(this, glyphConfig));    // T3: 旅路の魔法
        spellRegistry.register(new ScaleEffect(this, glyphConfig));      // T2: スケール

        // ===== Effects - ブロック操作 =====
        spellRegistry.register(new BreakEffect(this, glyphConfig));      // T1: 破壊
        spellRegistry.register(new AdvancedBreakEffect(this, glyphConfig));// T3: 高度破壊
        spellRegistry.register(new LightEffect(this, glyphConfig));      // T1: 光明
        spellRegistry.register(new GrowEffect(this, glyphConfig));       // T2: 成長
        spellRegistry.register(new HarvestEffect(this, glyphConfig));    // T1: 収穫
        spellRegistry.register(new CutEffect(this, glyphConfig));        // T1: 刈取
        spellRegistry.register(new FellEffect(this, glyphConfig));       // T1: 伐採
        spellRegistry.register(new PlaceBlockEffect(this, glyphConfig)); // T1: 設置
        spellRegistry.register(new PhantomBlockEffect(this, glyphConfig));// T1: 幻影
        spellRegistry.register(new ExchangeEffect(this, glyphConfig));   // T2: 交換
        spellRegistry.register(new SmeltEffect(this, glyphConfig));      // T2: 精錬
        spellRegistry.register(new CrushEffect(this, glyphConfig));      // T2: 粉砕
        spellRegistry.register(new ExplosionEffect(this, glyphConfig));  // T2: 爆発
        spellRegistry.register(new EvaporateEffect(this, glyphConfig));  // T1: 蒸発
        spellRegistry.register(new ConjureWaterEffect(this, glyphConfig));// T1: 水生成
        spellRegistry.register(new IntangibleEffect(this, glyphConfig)); // T3: 透過

        // ===== Effects - 特殊/召喚 =====
        spellRegistry.register(new InteractEffect(this, glyphConfig));   // T1: 操作
        spellRegistry.register(new PickupEffect(this, glyphConfig));     // T1: 拾得
        spellRegistry.register(new RotateEffect(this, glyphConfig));     // T1: 回転
        spellRegistry.register(new InfuseEffect(this, glyphConfig));     // T2: 注入
        spellRegistry.register(new CraftEffect(this, glyphConfig));      // T1: 作業台
        spellRegistry.register(new RuneEffect(this, glyphConfig));       // T3: 罠術
        spellRegistry.register(new RewindEffect(this, glyphConfig));     // T3: 巻き戻し
        spellRegistry.register(new WololoEffect(this, glyphConfig));     // T1: 色彩
        spellRegistry.register(new NameEffect(this, glyphConfig));       // T2: 命名
        spellRegistry.register(new FireworkEffect(this, glyphConfig));   // T2: 花火
        spellRegistry.register(new PrestidigitationEffect(this, glyphConfig));// T1: 手品
        spellRegistry.register(new CryEffect(this, glyphConfig));        // T1: 鳴き声
        spellRegistry.register(new SummonSteedEffect(this, glyphConfig));// T1: 馬召喚
        spellRegistry.register(new SummonWolvesEffect(this, glyphConfig));// T2: 狼召喚
        spellRegistry.register(new AnimateEffect(this, glyphConfig));    // T2: ゴーレム召喚
        spellRegistry.register(new SummonUndeadEffect(this, glyphConfig));// T3: 不死召喚
        spellRegistry.register(new SummonVexEffect(this, glyphConfig));  // T3: ヴェックス召喚
        spellRegistry.register(new SummonDecoyEffect(this, glyphConfig));// T3: デコイ召喚

        // ===== Augments - 基本ペア（増幅/減衰）=====
        var amplify = new AmplifyAugment(this, glyphConfig);
        var dampen = new DampenAugment(this, glyphConfig);
        spellRegistry.register(amplify);                                 // 増幅
        spellRegistry.register(dampen);                                  // 減衰

        // ===== Augments - 範囲系 =====
        spellRegistry.register(new AoeAugment(this, glyphConfig));       // 幅
        spellRegistry.register(new AoeHeightAugment(this, glyphConfig)); // 上下
        spellRegistry.register(new AoeVerticalAugment(this, glyphConfig));// 奥行き
        var aoeRadius = new AoeRadiusAugment(this, glyphConfig);
        spellRegistry.register(aoeRadius);                               // 半径増加

        // ===== Augments - 時間系 =====
        var extendTime = new ExtendTimeAugment(this, glyphConfig);
        var durationDown = new DurationDownAugment(this, glyphConfig);
        spellRegistry.register(extendTime);                              // 延長
        spellRegistry.register(durationDown);                            // 短縮

        // ===== Augments - 射程/速度系 =====
        var extendReach = new com.arspaper.spell.augment.ExtendReachAugment(this, glyphConfig);
        var shrinkReach = new com.arspaper.spell.augment.ShrinkReachAugment(this, glyphConfig);
        spellRegistry.register(extendReach);                             // 延伸
        spellRegistry.register(shrinkReach);                             // 収縮
        var accelerate = new AccelerateAugment(this, glyphConfig);
        var decelerate = new DecelerateAugment(this, glyphConfig);
        spellRegistry.register(accelerate);                              // 加速
        spellRegistry.register(decelerate);                              // 減速

        // ===== Augments - 投射系 =====
        var pierce = new PierceAugment(this, glyphConfig);
        var split = new SplitAugment(this, glyphConfig);
        spellRegistry.register(pierce);                                  // 貫通
        spellRegistry.register(split);                                   // 分裂
        spellRegistry.register(new TrailAugment(this, glyphConfig));     // 連射
        spellRegistry.register(new TraceAugment(this, glyphConfig));     // 軌跡

        // ===== Augments - 特殊 =====
        var propagate = new PropagateAugment(this, glyphConfig);
        spellRegistry.register(propagate);                               // 伝播
        var linger = new LingerAugment(this, glyphConfig);
        spellRegistry.register(linger);                                  // 残留
        spellRegistry.register(new DelayAugment(this, glyphConfig));     // 遅延
        spellRegistry.register(new ExtractAugment(this, glyphConfig));   // 抽出
        var fortune = new FortuneAugment(this, glyphConfig);
        spellRegistry.register(fortune);                                 // 幸運
        spellRegistry.register(new RandomizeAugment(this, glyphConfig)); // 無作為

        // ===== 超増強 (14種) - 通常と同順で配置 =====
        spellRegistry.register(new SuperAugment(this, glyphConfig, amplify,     "超増幅", "増幅2個分の強化効果"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, dampen,      "超減衰", "減衰2個分の抑制効果"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, aoeRadius,   "超半径増加", "半径増加2個分の範囲拡大"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, extendTime,  "超延長", "延長2個分の持続時間延長"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, durationDown,"超短縮", "短縮2個分の持続時間短縮"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, extendReach, "超延伸", "延伸2個分の射程延長"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, shrinkReach, "超収縮", "収縮2個分の射程短縮"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, accelerate,  "超加速", "加速2個分の速度上昇"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, decelerate,  "超減速", "減速2個分の速度低下"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, pierce,      "超貫通", "貫通2個分の貫通効果"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, split,       "超分裂", "分裂2個分の弾数増加"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, propagate,   "超伝播", "伝播2個分のチェーン対象"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, linger,      "超残留", "残留2個分の持続時間"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, fortune,     "超幸運", "幸運2個分のドロップ増加"));
    }

    private void registerDefaultItems() {
        // Spell Books (3 tiers)
        for (SpellBookTier tier : SpellBookTier.values()) {
            itemRegistry.register(new SpellBook(this, spellRegistry, tier));
        }

        itemRegistry.register(new Wand(this));

        // SpellWand は廃止（アイテムバインドで代替）
        itemRegistry.register(new SourceBerry(this));

        // 設定ベース素材（materials.ymlから動的登録）
        for (MaterialConfig mat : materialConfigManager.getAll()) {
            itemRegistry.register(new com.arspaper.item.impl.ConfigurableMaterial(this, mat));
        }

        // 設定ベース防具（armors.ymlから動的登録）
        for (ArmorSetConfig armorSet : armorConfigManager.getAll()) {
            for (String slot : ArmorSetConfig.getSlotNames()) {
                itemRegistry.register(new ConfigurableArmor(this, armorSet, slot));
            }
        }

        // Thread Items (空 + 効果付き)
        for (ThreadType threadType : ThreadType.values()) {
            itemRegistry.register(new ThreadItem(this, threadType));
        }
    }

    private void registerDefaultBlocks() {
        ScribingTable scribingTable = new ScribingTable(this);
        SourceJar sourceJar = new SourceJar(this);
        VolcanicSourcelink volcanicSourcelink = new VolcanicSourcelink(this);
        MycelialSourcelink mycelialSourcelink = new MycelialSourcelink(this);
        AlchemicalSourcelink alchemicalSourcelink = new AlchemicalSourcelink(this);
        VitalicSourcelink vitalicSourcelink = new VitalicSourcelink(this);
        BotanicalSourcelink botanicalSourcelink = new BotanicalSourcelink(this);
        RitualCore ritualCore = new RitualCore(this);
        Pedestal pedestal = new Pedestal(this);

        CreativeSourceJar creativeSourceJar = new CreativeSourceJar(this);
        Waystone waystone = new Waystone(this);

        blockRegistry.register(scribingTable);
        blockRegistry.register(sourceJar);
        blockRegistry.register(creativeSourceJar);
        blockRegistry.register(volcanicSourcelink);
        blockRegistry.register(mycelialSourcelink);
        blockRegistry.register(alchemicalSourcelink);
        blockRegistry.register(vitalicSourcelink);
        blockRegistry.register(botanicalSourcelink);
        blockRegistry.register(ritualCore);
        blockRegistry.register(pedestal);
        blockRegistry.register(waystone);

        // カスタムブロックもアイテムとして取得できるようにする
        itemRegistry.register(scribingTable);
        itemRegistry.register(sourceJar);
        itemRegistry.register(creativeSourceJar);
        itemRegistry.register(volcanicSourcelink);
        itemRegistry.register(mycelialSourcelink);
        itemRegistry.register(alchemicalSourcelink);
        itemRegistry.register(vitalicSourcelink);
        itemRegistry.register(botanicalSourcelink);
        itemRegistry.register(ritualCore);
        itemRegistry.register(pedestal);
        itemRegistry.register(waystone);
        getServer().getPluginManager().registerEvents(waystone, this);

        // テレポートコンパス
        itemRegistry.register(new TeleportCompass(this));
    }

    private void registerListeners() {
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new CustomItemListener(itemRegistry), this);
        pluginManager.registerEvents(new CustomBlockListener(this, blockRegistry, blockParticleTask, sourcelinkTickTask), this);
        pluginManager.registerEvents(new ProjectileHitListener(), this);
        pluginManager.registerEvents(new GuiListener(), this);
        pluginManager.registerEvents(manaManager, this);
        armorManaListener = new ArmorManaListener(this);
        pluginManager.registerEvents(armorManaListener, this);
        pluginManager.registerEvents(new SourceBerryListener(this), this);
        pluginManager.registerEvents(new PhantomBlockListener(), this);
        pluginManager.registerEvents(new SummonedMobListener(this), this);
        pluginManager.registerEvents(new com.arspaper.enchant.EnchantBookListener(), this);
        pluginManager.registerEvents(new com.arspaper.enchant.SoulboundListener(), this);
        pluginManager.registerEvents(new com.arspaper.spell.SpellBindListener(), this);
        lootTableListener = new com.arspaper.loot.LootTableListener(this);
        pluginManager.registerEvents(lootTableListener, this);

        // SpellEffectリスナー登録（Listener実装のEffectのみ）
        for (var component : spellRegistry.getAll()) {
            if (component instanceof org.bukkit.event.Listener listener) {
                pluginManager.registerEvents(listener, this);
            }
        }
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

    public BlockParticleTask getBlockParticleTask() {
        return blockParticleTask;
    }

    public GlyphConfig getGlyphConfig() {
        return glyphConfig;
    }

    public SpellCaster getSpellCaster() {
        return spellCaster;
    }

    public ArmorConfigManager getArmorConfigManager() {
        return armorConfigManager;
    }

    public ThreadConfig getThreadConfig() {
        return threadConfig;
    }

    public SourcelinkTickTask getSourcelinkTickTask() {
        return sourcelinkTickTask;
    }

    public ArmorManaListener getArmorManaListener() {
        return armorManaListener;
    }

    /**
     * 設定ファイルを再読み込みする。
     */
    public void reloadGlyphConfig() {
        glyphConfig.reload();
    }

    public void reloadMaterialConfig() {
        materialConfigManager.reload();
        // 素材アイテムを登録/更新（既存IDも最新設定で再登録）
        for (MaterialConfig mat : materialConfigManager.getAll()) {
            itemRegistry.register(new com.arspaper.item.impl.ConfigurableMaterial(this, mat));
        }
    }

    public void reloadArmorConfig() {
        armorConfigManager.reload();
        // 防具アイテムを登録/更新（既存IDも最新設定で再登録）
        for (ArmorSetConfig armorSet : armorConfigManager.getAll()) {
            for (String slot : ArmorSetConfig.getSlotNames()) {
                itemRegistry.register(new ConfigurableArmor(this, armorSet, slot));
            }
        }
    }

    public void reloadLootConfig() {
        if (lootTableListener != null) {
            lootTableListener.reloadConfig();
        }
    }
}
