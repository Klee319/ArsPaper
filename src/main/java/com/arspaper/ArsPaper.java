package com.arspaper;

import com.arspaper.block.BlockParticleTask;
import com.arspaper.block.CustomBlockListener;
import com.arspaper.block.CustomBlockRegistry;
import com.arspaper.block.SourceJarConfig;
import com.arspaper.block.impl.CreativeSourceJar;
import com.arspaper.block.impl.InfinitySourceCore;
import com.arspaper.block.impl.Pedestal;
import com.arspaper.block.impl.RitualCore;
import com.arspaper.block.impl.ScribingTable;
import com.arspaper.block.impl.SourceJar;
import com.arspaper.block.impl.Waystone;
import com.arspaper.command.ArsCommand;
import com.arspaper.gui.GuiListener;
import com.arspaper.item.*;
import com.arspaper.item.impl.CatalystItem;
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

import com.arspaper.source.InfinityCoreTracker;
import com.arspaper.source.SourceNetwork;
import com.arspaper.source.SourcelinkTickTask;
import com.arspaper.source.sourcelink.AlchemicalSourcelink;
import com.arspaper.source.sourcelink.BotanicalSourcelink;
import com.arspaper.source.sourcelink.MycelialSourcelink;
import com.arspaper.source.sourcelink.VitalicSourcelink;
import com.arspaper.source.sourcelink.SourcelinkConfig;
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
    private InfinityCoreTracker infinityCoreTracker;
    private com.arspaper.source.SourceNetworkParticleTask sourceNetworkParticleTask;
    private RecipeManager recipeManager;
    private com.arspaper.recipe.UnlockGate unlockGate;
    private RitualRecipeRegistry ritualRecipeRegistry;
    private RitualEffectRegistry ritualEffectRegistry;
    private RitualManager ritualManager;
    private BlockParticleTask blockParticleTask;
    private GlyphConfig glyphConfig;
    private com.arspaper.item.FunctionalItemConfig functionalItemConfig;
    private SpellCaster spellCaster;
    private MaterialConfigManager materialConfigManager;
    private ArmorManaListener armorManaListener;
    private ThreadConfig threadConfig;
    private ThreadSetConfig threadSetConfig;
    private SpellBookConfig spellBookConfig;
    private CatalystConfig catalystConfig;
    private com.arspaper.loot.LootTableListener lootTableListener;
    private SourcelinkConfig sourcelinkConfig;
    private SourceJarConfig sourceJarConfig;
    private com.arspaper.world.WorldSettingsManager worldSettingsManager;

    @Override
    public void onEnable() {
        instance = this;
        // TrinityForge のホットリロード後に古いサービスを掴み続けないよう、再有効化時にキャッシュを破棄して再解決させる。
        com.arspaper.integration.TrinityForgeBridge.reset();
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
        // 統合版(Bedrock)向けの補正レシピ表。Geyser が素材の CustomModelData を落とすので、
        // 「素材が本当はどのカスタムアイテムか」を GeyserExtra へ渡すために書き出す。
        com.arspaper.recipe.BedrockRecipeExporter.exportTo(recipeManager, getDataFolder(), getLogger());
        // 作業台専用(3×3, method: workbench)レシピが2×2インベントリグリッドで成立するのを防ぐ。
        getServer().getPluginManager().registerEvents(
            new com.arspaper.recipe.WorkbenchGridGateListener(recipeManager), this);
        // 圧縮ブロック等、base material 共通・CMD違いのカスタムアイテムがプレーン素材レシピに
        // material のみで誤一致する over-match(最上位圧縮→下位に戻るバグ)を per-slot 厳密照合で防ぐ。
        getServer().getPluginManager().registerEvents(
            new com.arspaper.recipe.CustomIngredientCraftGuardListener(recipeManager), this);

        // 儀式エフェクトレジストリ
        ritualEffectRegistry = new RitualEffectRegistry();
        ritualEffectRegistry.register("weather", new WeatherRitualEffect());
        FlightRitualEffect flightEffect = new FlightRitualEffect();
        getServer().getPluginManager().registerEvents(flightEffect, this);
        ritualEffectRegistry.register("flight", flightEffect);
        ritualEffectRegistry.register("moonfall", new MoonfallRitualEffect());
        ritualEffectRegistry.register("sunrise", new SunriseRitualEffect());
        ritualEffectRegistry.register("repair", new RepairRitualEffect());
        ritualEffectRegistry.register("animal_summon", new AnimalSummonRitualEffect());
        ritualEffectRegistry.register("mob_summon", new MobSummonRitualEffect());
        ritualEffectRegistry.register("enchant_book", new EnchantBookRitualEffect());
        ritualEffectRegistry.register("thread_slot_expand", new ThreadSlotExpandRitualEffect());
        ritualEffectRegistry.register("thread_reroll", new ThreadRerollRitualEffect());

        // 儀式レシピ読み込み（UnifiedRecipeLoaderから）
        ritualRecipeRegistry = new RitualRecipeRegistry(this);
        ritualRecipeRegistry.registerRecipes(recipeLoader.getRitualRecipes());
        ritualManager = new RitualManager(ritualRecipeRegistry, ritualEffectRegistry, unlockGate);

        // TF は Ars より先に enable するため、catalog.yml 儀式はここで Ars 側へ取り込む。
        com.arspaper.integration.TrinityForgeBridge.repushCatalogRituals();
        // 同様に TF のカタログ作業台レシピも再登録させ、結果を Ars 実体(機能PDC付き)へ差し替える。
        com.arspaper.integration.TrinityForgeBridge.refreshCatalogRecipes();

        if (!com.arspaper.integration.TrinityForgeBridge.isAvailable()) {
            getLogger().severe("TrinityForge が見つかりません。魔法ダメージの対称パイプライン供給と全perkゲートが無効化されます（fail-open）。");
        }

        getLogger().info("ArsPaper enabled!");
    }

    @Override
    public void onDisable() {
        // TFのExternalItemRegistryに残った "arspaper" レイヤーを消す(空Mapで置換=レイヤーごと除去)。
        // 無いとArsPaperがこのセッションだけ無効化された場合に、もう存在しないカスタムアイテムの
        // material+CMD識別がTF側の custom: レシピ判定に残留してしまう。
        com.arspaper.integration.TrinityForgeBridge.registerExternalItems(java.util.List.of());
        if (manaManager != null) {
            manaManager.shutdown();
        }
        if (sourceNetwork != null) {
            sourceNetwork.shutdown();
        }
        if (sourcelinkTickTask != null) {
            sourcelinkTickTask.stop();
        }
        if (sourceNetworkParticleTask != null) {
            sourceNetworkParticleTask.stop();
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
            String[] resourceFiles = {"glyphs.yml", "items.yml", "materials.yml", "threads.yml", "sourcelinks.yml", "spellbooks.yml", "sourcejars.yml", "functional-items.yml"};
            for (String name : resourceFiles) {
                java.io.File existing = new java.io.File(getDataFolder(), name);
                if (existing.exists()) {
                    java.io.File backup = new java.io.File(getDataFolder(), name + ".bak");
                    // Files.move は失敗時に IOException を投げるため、サイレントな失敗を防ぐ。
                    // バックアップに失敗した場合は既存ファイルを保持し、上書き抽出をスキップして可視化する。
                    try {
                        java.nio.file.Files.move(existing.toPath(), backup.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("Backed up " + name + " → " + name + ".bak");
                    } catch (java.io.IOException e) {
                        getLogger().warning("Failed to back up " + name + " ("
                            + existing.getPath() + "): " + e.getMessage()
                            + " — 既存ファイルを保持し、このファイルの更新をスキップします");
                        continue;
                    }
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

        // 機能アイテム表示名設定（ワンド/コンパス/儀式ブロック等の表示名上書き）
        functionalItemConfig = new com.arspaper.item.FunctionalItemConfig(this);

        // スペルレジストリ
        spellRegistry = new SpellRegistry();
        registerDefaultGlyphs();

        // グリフ解放は glyphs.yml の tier ゲート＋TrinityForge skilltree の glyph-gate perk が担当する。
        // 2026-08-16: UnlockedGlyphs（アイテム右クリック解放用の集合）は削除した。入口は 2026-07-23 に
        // 撤去済みで呼び出し元がゼロだったうえ、PDC キーが筆記台の写本集合と衝突していたため。
        // 詳細は UsageGate の javadoc。

        // エンチャント定数読み込み
        com.arspaper.enchant.ArsEnchantments.loadConfig(getConfig());

        // マナマネージャー
        ManaConfig manaConfig = ManaConfig.fromConfig(getConfig());
        manaManager = new ManaManager(this, manaConfig);

        // ワールド別設定マネージャー
        worldSettingsManager = new com.arspaper.world.WorldSettingsManager(this);

        // スペルキャスター（シングルトン）
        spellCaster = new SpellCaster(manaManager);

        // 素材設定マネージャー
        materialConfigManager = new MaterialConfigManager(this);

        // スレッド設定
        threadConfig = new ThreadConfig(this);

        // スレッド・セット効果設定(thread-sets.yml, TrinityForge戦闘連携)
        threadSetConfig = new ThreadSetConfig(this);

        // スレッドの個体差(rollSeed+quality)は 2026-08-03 に TrinityForge の ItemData(通常アイテムと
        // 同じ導出経路)へ統合した。ThreadItem#createItemStack は TrinityForgeBridge#stampThreadIdentity
        // 経由でTFのrollSeed/qualityを刻むので、ここでの読み込みは不要。

        // 魔導書ティア設定（spellbooks.ymlから動的登録）
        spellBookConfig = new SpellBookConfig(this);

        // 触媒設定（spellbooks.ymlのcatalysts:節から動的登録）
        catalystConfig = new CatalystConfig(this);

        // ソースリンク設定
        sourcelinkConfig = new SourcelinkConfig(this);
        // ソースジャー容量・見た目
        sourceJarConfig = new SourceJarConfig(this);
        SourceJar.applyConfiguredCapacity(sourceJarConfig.capacityOf("source_jar"));

        // 解放ゲート（レシピ/儀式 perk ゲート + 修繕儀式コスト設定）
        unlockGate = new com.arspaper.recipe.UnlockGate(this);

        // カスタムアイテムレジストリ
        itemRegistry = new CustomItemRegistry();

        // カスタムブロックレジストリ
        blockRegistry = new CustomBlockRegistry();
        registerDefaultBlocks();
        registerDefaultItems();
        // sourcelinks.yml items: のカスタムid定義を登録する。既定ブロック/アイテムの後に行うことで、
        // 既存idとの衝突を検知してスキップできる (上書き事故防止)。
        registerCustomSourcelinks();
        // sourcejars.yml jars: の上位ジャーを登録する (source_jar / creative_source_jar 以外)。
        registerCustomSourceJars();

        // 全カスタムアイテム(素材/魔導書/触媒/スレッド等)の識別(material+CMD)をTrinityForgeの
        // ExternalItemRegistryへ反映する。custom:<id> レシピのper-slot識別(CatalogWorkbenchListener)
        // がArsPaperアイテムを認識できるようにするための登録で、必ず後続のrepushCatalogRituals/
        // refreshCatalogRecipes(TFはArsより先にenableするため、この後にTF側から呼ばれる)より前に置く。
        com.arspaper.integration.TrinityForgeBridge.registerExternalItems(itemRegistry.getAll());

        // 触媒ステをTrinityForgeへ動的登録（品質/ランダムロール解決・lore自動生成・戦闘連携を統一パイプラインへ委譲）
        registerCatalystStatsWithTrinityForge();

        // Sourceネットワーク
        sourceNetwork = new SourceNetwork(this);

        // SourcelinkティックTask
        sourcelinkTickTask = new SourcelinkTickTask(this, blockRegistry);
        sourcelinkTickTask.start();

        // 設置された infinity_source_core の位置追跡(柱6: 半径内のソースリンクへ補正を乗せる)
        infinityCoreTracker = new InfinityCoreTracker(this);
        infinityCoreTracker.start();

        // ドミニオンワンドの経路可視化Task(ワンド保持者にだけ・一定間隔で描画。config でOFF可)
        sourceNetworkParticleTask = new com.arspaper.source.SourceNetworkParticleTask(this);
        sourceNetworkParticleTask.start();

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
        spellRegistry.register(new FlareEffect(this, glyphConfig));      // T2: 閃炎（炎上中の対象にバースト）

        // ===== Effects - 移動/ユーティリティ =====
        spellRegistry.register(new LaunchEffect(this, glyphConfig));     // T1: 打ち上げ
        spellRegistry.register(new LeapEffect(this, glyphConfig));       // T1: 跳躍
        spellRegistry.register(new BounceEffect(this, glyphConfig));     // T2: 跳弾
        spellRegistry.register(new SpeedBoostEffect(this, glyphConfig)); // T2: 指向
        spellRegistry.register(new GaleEffect(this, glyphConfig));       // T2: 疾風
        spellRegistry.register(new SlowfallEffect(this, glyphConfig));   // T2: 低速落下
        spellRegistry.register(new LevitateEffect(this, glyphConfig));   // T2: 浮遊
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
        spellRegistry.register(new SenseMagicEffect(this, glyphConfig)); // T2: 魔力感知（発光+暗視）

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
        spellRegistry.register(new TossEffect(this, glyphConfig));       // T1: 投擲（インベントリからアイテム投出）
        spellRegistry.register(new ResetEffect(this, glyphConfig));      // T1: 初期化（チェーンリセット・演出）

        // ===== Augments — 対ペアで登録（GUI表示順 = 登録順）=====
        // filterAndSortPaletteで超増強がベースの直後に自動配置される

        // --- 増幅 / 減衰 ---
        var amplify = new AmplifyAugment(this, glyphConfig);
        var dampen = new DampenAugment(this, glyphConfig);
        spellRegistry.register(amplify);
        spellRegistry.register(dampen);

        // --- 延長 / 短縮 ---
        var extendTime = new ExtendTimeAugment(this, glyphConfig);
        var durationDown = new DurationDownAugment(this, glyphConfig);
        spellRegistry.register(extendTime);
        spellRegistry.register(durationDown);

        // --- 延伸 / 収縮 ---
        var extendReach = new com.arspaper.spell.augment.ExtendReachAugment(this, glyphConfig);
        var shrinkReach = new com.arspaper.spell.augment.ShrinkReachAugment(this, glyphConfig);
        spellRegistry.register(extendReach);
        spellRegistry.register(shrinkReach);

        // --- 加速 / 減速 ---
        var accelerate = new AccelerateAugment(this, glyphConfig);
        var decelerate = new DecelerateAugment(this, glyphConfig);
        spellRegistry.register(accelerate);
        spellRegistry.register(decelerate);

        // --- 範囲系（幅 / 上下 / 奥行き / 半径増加）---
        spellRegistry.register(new AoeAugment(this, glyphConfig));
        spellRegistry.register(new AoeHeightAugment(this, glyphConfig));
        spellRegistry.register(new AoeVerticalAugment(this, glyphConfig));
        var aoeRadius = new AoeRadiusAugment(this, glyphConfig);
        spellRegistry.register(aoeRadius);

        // --- 貫通 / 分裂 ---
        var pierce = new PierceAugment(this, glyphConfig);
        var split = new SplitAugment(this, glyphConfig);
        spellRegistry.register(pierce);
        spellRegistry.register(split);

        // --- 抽出 / 幸運 ---
        spellRegistry.register(new ExtractAugment(this, glyphConfig));
        var fortune = new FortuneAugment(this, glyphConfig);
        spellRegistry.register(fortune);

        // --- 伝播 / 残留 ---
        var propagate = new PropagateAugment(this, glyphConfig);
        spellRegistry.register(propagate);
        var linger = new LingerAugment(this, glyphConfig);
        spellRegistry.register(linger);

        // --- 投射制御（連射 / 軌跡 / 遅延）---
        spellRegistry.register(new TrailAugment(this, glyphConfig));
        spellRegistry.register(new TraceAugment(this, glyphConfig));
        var delay = new DelayAugment(this, glyphConfig);
        spellRegistry.register(delay);

        // --- ドロップ/ランダム系（無作為）---
        spellRegistry.register(new RandomizeAugment(this, glyphConfig));

        // ===== 超増強 (14種) — ベースと同順（filterAndSortPaletteでベースの隣に配置）=====
        spellRegistry.register(new SuperAugment(this, glyphConfig, amplify,     "超増幅", "増幅2個分の強化効果"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, dampen,      "超減衰", "減衰2個分の抑制効果"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, extendTime,  "超延長", "延長2個分の持続時間延長"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, durationDown,"超短縮", "短縮2個分の持続時間短縮"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, extendReach, "超延伸", "延伸2個分の射程延長"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, shrinkReach, "超収縮", "収縮2個分の射程短縮"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, accelerate,  "超加速", "加速2個分の速度上昇"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, decelerate,  "超減速", "減速2個分の速度低下"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, aoeRadius,   "超半径増加", "半径増加2個分の範囲拡大"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, pierce,      "超貫通", "貫通2個分の貫通効果"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, split,       "超分裂", "分裂2個分の弾数増加"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, fortune,     "超幸運", "幸運2個分のドロップ増加"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, propagate,   "超伝播", "伝播2個分のチェーン対象"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, linger,      "超残留", "残留2個分の持続時間"));
        spellRegistry.register(new SuperAugment(this, glyphConfig, delay,       "超遅延", "遅延2個分の遅延時間"));
    }

    private void registerDefaultItems() {
        // 設定ベース魔導書ティア（spellbooks.ymlから動的登録）
        for (SpellBookTierData tier : spellBookConfig.all()) {
            itemRegistry.register(new SpellBook(this, spellRegistry, tier));
        }

        itemRegistry.register(new Wand(this));

        // SpellWand は廃止（アイテムバインドで代替）
        itemRegistry.register(new SourceBerry(this));

        // 設定ベース素材（materials.ymlから動的登録）
        // ⚠ id がカスタムブロック(例: infinity_source_core)と衝突する場合はスキップする。
        //   registerDefaultBlocks() がこの前に実行済みで、ブロック側が既に見た目/設置挙動を
        //   itemRegistry へ登録している ―― ここで無条件に上書きすると「置けるはずのブロックが
        //   非設置のConfigurableMaterialに化ける」(このメソッドの呼び出し順に依存する無言の事故)。
        //   materials.yml 側のレシピ(recipe:)はブロックのJavaクラスと独立に読まれるため、
        //   スキップしてもクラフト自体は成立する。
        for (MaterialConfig mat : materialConfigManager.getAll()) {
            if (blockRegistry.has(mat.id())) {
                continue;
            }
            itemRegistry.register(new com.arspaper.item.impl.ConfigurableMaterial(this, mat));
        }

        // Thread Items (空 + 効果付き)
        for (ThreadType threadType : ThreadType.values()) {
            itemRegistry.register(new ThreadItem(this, threadType));
        }

        // 設定ベース触媒（spellbooks.ymlのcatalysts:節から動的登録）
        for (CatalystData catalyst : catalystConfig.all()) {
            itemRegistry.register(new CatalystItem(this, catalyst));
        }
    }

    /**
     * 触媒(catalysts.yml)のステをTrinityForgeの動的item-stats登録APIへ反映する。
     * 既存の{@link com.arspaper.integration.TrinityForgeBridge#CATALYST_NAMESPACE}配下の登録を
     * 一旦クリアしてから全触媒を再登録するため、削除された触媒の残留登録も除去される。
     */
    private void registerCatalystStatsWithTrinityForge() {
        com.arspaper.integration.TrinityForgeBridge.clearCatalystStats();
        for (CatalystData catalyst : catalystConfig.all()) {
            com.arspaper.integration.TrinityForgeBridge.registerCatalystStats(
                catalyst.material(), catalyst.customModelData(),
                catalyst.fixedStats(), catalyst.perQualityStats(), catalyst.randomStats());
        }
    }

    private void registerDefaultBlocks() {
        ScribingTable scribingTable = new ScribingTable(this);
        SourceJar sourceJar = new SourceJar(this);
        VolcanicSourcelink volcanicSourcelink = new VolcanicSourcelink(this);
        volcanicSourcelink.setFuelValues(sourcelinkConfig.getVolcanicMaterials());
        MycelialSourcelink mycelialSourcelink = new MycelialSourcelink(this);
        mycelialSourcelink.setFoodValues(sourcelinkConfig.getMycelialMaterials());
        AlchemicalSourcelink alchemicalSourcelink = new AlchemicalSourcelink(this);
        alchemicalSourcelink.setAlchemyValues(sourcelinkConfig.getAlchemicalMaterials());
        VitalicSourcelink vitalicSourcelink = new VitalicSourcelink(this);
        BotanicalSourcelink botanicalSourcelink = new BotanicalSourcelink(this);
        RitualCore ritualCore = new RitualCore(this);
        Pedestal pedestal = new Pedestal(this);

        CreativeSourceJar creativeSourceJar = new CreativeSourceJar(this);
        Waystone waystone = new Waystone(this);
        // 到達証明「infinity_source_core」を設置可能にする(柱6)。既存の materials.yml の
        // 儀式レシピ(result: custom:infinity_source_core)はそのまま流用し、こちらは見た目/設置挙動だけを持つ。
        InfinitySourceCore infinitySourceCore = new InfinitySourceCore(this);

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
        blockRegistry.register(infinitySourceCore);

        // カスタムブロックもアイテムとして取得できるようにする
        // (infinity_source_core は materials.yml にも同idの定義が残っているが、
        //  registerDefaultItems() 側でブロック登録済みidをスキップするのでここが最終的に勝つ)
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
        itemRegistry.register(infinitySourceCore);
        getServer().getPluginManager().registerEvents(waystone, this);

        // テレポートコンパス
        itemRegistry.register(new TeleportCompass(this));
    }

    /** 固定5種のソースリンクid (これ以外の items.<id> はカスタム定義として type から実体を作る)。 */
    private static final java.util.Set<String> FIXED_SOURCELINK_IDS = java.util.Set.of(
        "volcanic_sourcelink", "mycelial_sourcelink", "alchemical_sourcelink",
        "vitalic_sourcelink", "botanical_sourcelink");

    /**
     * sourcelinks.yml {@code items:} のカスタムid定義から、typeに対応する挙動のソースリンクを
     * 生成してブロック/アイテムレジストリへ登録する。見た目 (material / display-name / CMD / lore)
     * は各インスタンスが自身のidで {@code items.<id>} を参照するため自動的に反映される。
     * 燃料テーブルはtypeごとの共有設定 (volcanic.materials 等) を使う。
     *
     * <p>再登録 (reload時) はレジストリのMapを同idで上書きするだけなので冪等。設定から消えたidの
     * 登録解除は再起動が必要 (残っていても実害はない: 設置済みブロックが動き続けるだけ)。
     */
    private void registerCustomSourcelinks() {
        for (SourcelinkConfig.ItemDef def : sourcelinkConfig.items().values()) {
            String id = def.id();
            if (FIXED_SOURCELINK_IDS.contains(id) || blockRegistry.has(id)) {
                continue;
            }
            // 既存の非ブロックアイテム (wand / source_berry 等) と同じ id は上書きしない。
            if (itemRegistry.has(id)) {
                getLogger().warning("sourcelinks.yml: items." + id
                    + " conflicts with an existing item id — skipped (choose another id)");
                continue;
            }
            com.arspaper.source.sourcelink.Sourcelink link = switch (def.type()) {
                case "volcanic" -> {
                    VolcanicSourcelink v = new VolcanicSourcelink(this, id);
                    v.setFuelValues(sourcelinkConfig.getVolcanicMaterials());
                    yield v;
                }
                case "mycelial" -> {
                    MycelialSourcelink m = new MycelialSourcelink(this, id);
                    m.setFoodValues(sourcelinkConfig.getMycelialMaterials());
                    yield m;
                }
                case "alchemical" -> {
                    AlchemicalSourcelink a = new AlchemicalSourcelink(this, id);
                    a.setAlchemyValues(sourcelinkConfig.getAlchemicalMaterials());
                    yield a;
                }
                case "vitalic" -> new VitalicSourcelink(this, id);
                case "botanical" -> new BotanicalSourcelink(this, id);
                default -> null;
            };
            if (link == null) {
                getLogger().warning("sourcelinks.yml: items." + id + " type '" + def.type()
                    + "' is unknown — skipped");
                continue;
            }
            blockRegistry.register(link);
            itemRegistry.register(link);
            getLogger().info("Registered custom sourcelink '" + id + "' (type=" + def.type() + ")");
        }
    }

    /**
     * sourcejars.yml の {@code jars:} に書かれた上位ジャーをブロック/アイテムとして登録する
     * (2026-07-31 追加)。
     *
     * <p>それまでジャーは {@code source_jar} / {@code creative_source_jar} の2種だけがハードコードで
     * 登録されていたため、yml に上位ジャーを足しても<b>ブロックとして存在せず、置くことすらできなかった</b>。
     * 容量も static 1 値だったので、仮に置けても全ジャー同容量で「上位ジャー」に意味が無かった。
     * ここで登録し、容量は {@link SourceJar#maxSource(org.bukkit.block.TileState)} が個体ごとに引く。
     *
     * <p>見た目 (material / display-name / CMD / lore) は {@link SourceJar} 側が自身のidで
     * {@code jars.<id>} を参照するので、yml に書くだけで反映される。カスタムソースリンクと同じ形。
     * 再登録(reload)は同idの上書きなので冪等。設定から消したidの登録解除は再起動が必要
     * (残っていても設置済みブロックが動き続けるだけで実害は無い)。
     */
    private void registerCustomSourceJars() {
        for (String id : sourceJarConfig.all().keySet()) {
            if (blockRegistry.has(id)) {
                continue;
            }
            // 既存の非ブロックアイテム (source_berry 等) と同じ id は上書きしない。
            if (itemRegistry.has(id)) {
                getLogger().warning("sourcejars.yml: jars." + id
                    + " conflicts with an existing item id — skipped (choose another id)");
                continue;
            }
            SourceJar jar = new SourceJar(this, id);
            blockRegistry.register(jar);
            itemRegistry.register(jar);
            getLogger().info("Registered source jar '" + id + "' (capacity="
                + sourceJarConfig.capacityOf(id) + ")");
        }
    }

    private void registerListeners() {
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new CustomItemListener(itemRegistry), this);
        // グリフ解放はScribingTable(glyphs.yml unlock-cost) + skilltree glyph-gateに一本化 (2026-07-23: 旧glyph-unlock-items.yml右クリック解放ルートを削除)。
        pluginManager.registerEvents(new CustomBlockListener(this, blockRegistry, blockParticleTask, sourcelinkTickTask), this);
        pluginManager.registerEvents(new ProjectileHitListener(), this);
        pluginManager.registerEvents(new GuiListener(), this);
        pluginManager.registerEvents(manaManager, this);
        pluginManager.registerEvents(new com.arspaper.mana.ManaRecoveryListener(manaManager), this);
        armorManaListener = new ArmorManaListener(this);
        pluginManager.registerEvents(armorManaListener, this);
        pluginManager.registerEvents(new ThreadGuiOpenListener(this), this);
        // 装備を手に持って真上+スニーク → 装着スレッドの内訳をチャットへ(lore は1行要約だけにした分の受け皿)。
        pluginManager.registerEvents(new com.arspaper.item.ThreadStatChatListener(), this);
        // 魂縛(W-259): ダンジョン産スレッドを最初に拾った人へ焼き付ける。
        // 実効ゲート(装着の拒否)は ThreadGui 側にある ── ここは刻印だけ。
        pluginManager.registerEvents(new com.arspaper.item.ThreadSoulbindListener(), this);
        pluginManager.registerEvents(new SourceBerryListener(this), this);
        pluginManager.registerEvents(new PhantomBlockListener(), this);
        pluginManager.registerEvents(new SummonedMobListener(this), this);
        pluginManager.registerEvents(new com.arspaper.enchant.EnchantBookListener(), this);
        pluginManager.registerEvents(new com.arspaper.enchant.SoulboundListener(), this);
        // 2026-08-14: lapis-cost-reduction(LapisCostReductionListener)はユーザー判断
        // 「ラピス効率は使わない」で機構ごと廃止した。TF 側の stat 語彙・lore・base-stats・
        // skilltree/enchanting.yml のノードBも同時に削除済み(ノードBの効果は
        // enchant-cost-reduction へ差し替え)。
        pluginManager.registerEvents(new com.arspaper.spell.SpellBindListener(), this);
        // W-191: スケール魔法の解除はスケジューラだけでは足りない（属性修飾子は NBT に残る）。
        // 参加時・チャンク読み込み時に PDC の終了時刻を読み直して剥がす。
        pluginManager.registerEvents(new com.arspaper.spell.effect.ScaleRestoreListener(), this);
        lootTableListener = new com.arspaper.loot.LootTableListener(this);
        pluginManager.registerEvents(lootTableListener, this);
        // 要件⑥ ocean-thread-catch / ruins-thread-drop: TF側ドロップテーブル(2026-07-23
        // stat-gate-overhaul §4)へ移設されたため、fork側の重複ドロップリスナーは廃止(W2d-2)。
        // クラフトレシピの perk 解放ゲート
        pluginManager.registerEvents(new com.arspaper.recipe.RecipeUnlockGate(unlockGate), this);
        // 鍛冶台（ネザライト強化等）の perk 解放ゲート。PrepareItemCraftEvent を通らない経路を埋める。
        pluginManager.registerEvents(new com.arspaper.recipe.SmithingTableUnlockGate(unlockGate), this);

        // SpellEffectリスナー登録（Listener実装のEffectのみ）
        for (var component : spellRegistry.getAll()) {
            if (component instanceof org.bukkit.event.Listener listener) {
                pluginManager.registerEvents(listener, this);
            }
        }

        // W-191: 既にオンラインの全員を見直す。/reload や再有効化では PlayerJoinEvent が
        // 飛ばないので、ここを通さないと縮んだままの人が残る。
        com.arspaper.spell.effect.ScaleEffect.restoreAll();
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

    public com.arspaper.recipe.UnlockGate getUnlockGate() {
        return unlockGate;
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

    public com.arspaper.item.FunctionalItemConfig getFunctionalItemConfig() {
        return functionalItemConfig;
    }

    public SpellCaster getSpellCaster() {
        return spellCaster;
    }

    public ThreadConfig getThreadConfig() {
        return threadConfig;
    }

    public ThreadSetConfig getThreadSetConfig() {
        return threadSetConfig;
    }

    public SpellBookConfig getSpellBookConfig() {
        return spellBookConfig;
    }

    public CatalystConfig getCatalystConfig() {
        return catalystConfig;
    }

    public MaterialConfigManager getMaterialConfigManager() {
        return materialConfigManager;
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

    public void reloadFunctionalItemConfig() {
        functionalItemConfig.reload();
    }

    public void reloadMaterialConfig() {
        materialConfigManager.reload();
        // 素材アイテムを登録/更新（既存IDも最新設定で再登録）
        // registerDefaultItems() と同じ理由でカスタムブロックidはスキップする。
        for (MaterialConfig mat : materialConfigManager.getAll()) {
            if (blockRegistry.has(mat.id())) {
                continue;
            }
            itemRegistry.register(new com.arspaper.item.impl.ConfigurableMaterial(this, mat));
        }
    }

    public void reloadSpellBookConfig() {
        spellBookConfig.reload();
        // 魔導書アイテムを登録/更新（既存IDも最新設定で再登録）
        for (SpellBookTierData tier : spellBookConfig.all()) {
            itemRegistry.register(new SpellBook(this, spellRegistry, tier));
        }
    }

    /**
     * spellbooks.ymlのcatalysts:節を再読み込みし、触媒アイテムの再登録＋
     * TrinityForge動的item-stats登録の再反映まで行う。
     */
    public void reloadCatalystConfig() {
        catalystConfig.reload();
        for (CatalystData catalyst : catalystConfig.all()) {
            itemRegistry.register(new CatalystItem(this, catalyst));
        }
        registerCatalystStatsWithTrinityForge();
    }

    public void reloadLootConfig() {
        if (lootTableListener != null) {
            lootTableListener.reloadConfig();
        }
    }

    public com.arspaper.world.WorldSettingsManager getWorldSettingsManager() {
        return worldSettingsManager;
    }

    public SourcelinkConfig getSourcelinkConfig() {
        return sourcelinkConfig;
    }

    public SourceJarConfig getSourceJarConfig() {
        return sourceJarConfig;
    }

    /** 設置された infinity_source_core の位置追跡(柱6)。{@code onEnable} 完了前は null。 */
    public InfinityCoreTracker getInfinityCoreTracker() {
        return infinityCoreTracker;
    }

    public void reloadSourcelinkConfig() {
        sourcelinkConfig.reload();
        // 登録済みソースリンクに新しい値を適用 (カスタムidのインスタンスも instanceof で拾われる)
        for (var block : blockRegistry.getAll()) {
            if (block instanceof VolcanicSourcelink v) {
                v.setFuelValues(sourcelinkConfig.getVolcanicMaterials());
            } else if (block instanceof MycelialSourcelink m) {
                m.setFoodValues(sourcelinkConfig.getMycelialMaterials());
            } else if (block instanceof AlchemicalSourcelink a) {
                a.setAlchemyValues(sourcelinkConfig.getAlchemicalMaterials());
            }
        }
        // reloadで新しく追加されたカスタムソースリンクを登録 (既存idはスキップされる)
        registerCustomSourcelinks();
        // 転送周期/経路パーティクル間隔は Bukkit のタイマー周期なので、張り直さないと反映されない。
        if (sourcelinkTickTask != null) {
            sourcelinkTickTask.restart();
        }
        if (sourceNetwork != null) {
            sourceNetwork.restartTransferTask();
        }
        if (sourceNetworkParticleTask != null) {
            sourceNetworkParticleTask.restart();
        }
    }

    public void reloadSourceJarConfig() {
        if (sourceJarConfig == null) {
            sourceJarConfig = new SourceJarConfig(this);
        } else {
            sourceJarConfig.reload();
        }
        SourceJar.applyConfiguredCapacity(sourceJarConfig.capacityOf("source_jar"));
        // reloadで新しく追加された上位ジャーを登録する (既存idはスキップされる)。
        if (blockRegistry != null) {
            registerCustomSourceJars();
        }
    }
}
