package com.arspaper.spell;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * グリフ1種ごとの GUI アイコン。
 *
 * <p><b>なぜ要るか。</b> 2026-08-22 のユーザー報告
 * 「Arsグリフの設定でアイテムアイコンを付けてほしい。現状どれがどの魔法かぱっと見で分からない」。
 * それまでアイコンは<b>種類ごとの3種類だけ</b>（形態=ダイヤ / 効果=エメラルド / 増強=アメジスト）で、
 * 120 個のグリフが 3 種類の絵に潰れていた。筆記台は 36 個/ページ・グリフ設定は 28 個/ページ
 * 並ぶので、名前を1つずつ読むまで何も判別できない。
 *
 * <p><b>ここが唯一の定義</b>。3画面（筆記台＝グリフ解放 / グリフ解放素材＝グリフレシピ /
 * 呪文編集＝グリフ設定）はすべて {@link #iconFor} を通す。画面ごとに switch を書くと必ずずれる。
 *
 * <p><b>既定値を Java に置いているのは意図的。</b> {@code glyphs.yml} は
 * {@code saveResource("glyphs.yml", false)} で書き出されるので、<b>jar を差し替えても
 * 配備先の yml には新しいキーが一切増えない</b>。yml 側に既定を書くと、既存サーバでは
 * 永久に反映されない。yml の {@code icon:} は<b>上書き専用</b>（手で書けば効く）。
 */
public final class GlyphIcons {

    private GlyphIcons() {
    }

    /** 種類ごとの最後の砦。未知のグリフ（将来追加分）がここへ落ちる。 */
    private static final Map<SpellComponent.ComponentType, Material> TYPE_FALLBACK = Map.of(
            SpellComponent.ComponentType.FORM, Material.DIAMOND,
            SpellComponent.ComponentType.EFFECT, Material.EMERALD,
            SpellComponent.ComponentType.AUGMENT, Material.AMETHYST_SHARD);

    /**
     * グリフID（名前空間なしのキー）→ アイコン。
     *
     * <p>並びは {@code ArsPaper#registerComponents} の登録順に揃えてある
     * （＝ {@link GlyphOrder} が作る画面上の並び順）。差分を読むときに突き合わせやすいため。
     *
     * <p><b>材質が重複しないのは「同じ種類の中」まで</b>。形態と増強で同じ材質を使うことはある。
     * 1画面に同種のグリフが固まって並ぶので、種類内で一意なら見分けはつく。
     */
    private static final Map<String, Material> ICONS = icons();

    private static Map<String, Material> icons() {
        Map<String, Material> m = new LinkedHashMap<>();

        // ===== 形態（8種）=====
        m.put("projectile", Material.ARROW);              // 投射
        m.put("touch", Material.STICK);                   // 接触
        m.put("self", Material.PLAYER_HEAD);              // 自己
        m.put("underfoot", Material.IRON_BOOTS);          // 足元
        m.put("overhead", Material.IRON_HELMET);          // 頭上
        m.put("burst", Material.FIRE_CHARGE);             // 炸裂
        m.put("orbit", Material.ENDER_EYE);               // 旋回
        m.put("beam", Material.END_ROD);                  // 照射

        // ===== 効果 / 攻撃系（21種）=====
        m.put("harm", Material.IRON_SWORD);               // 害悪
        m.put("ignite", Material.FLINT_AND_STEEL);        // 炎上
        m.put("freeze", Material.ICE);                    // 凍結
        m.put("knockback", Material.PISTON);              // 吹飛
        m.put("pull", Material.FISHING_ROD);              // 引寄
        m.put("gravity", Material.ANVIL);                 // 重力
        m.put("snare", Material.COBWEB);                  // 拘束
        m.put("scorch", Material.BLAZE_POWDER);           // 焦熱
        m.put("cold_snap", Material.PACKED_ICE);          // 凍裂
        m.put("crush_wave", Material.PRISMARINE_SHARD);   // 粉砕波
        m.put("windshear", Material.BREEZE_ROD);          // 烈風
        m.put("wind_burst", Material.WIND_CHARGE);        // 突風
        m.put("lightning", Material.LIGHTNING_ROD);       // 落雷
        m.put("wither", Material.WITHER_SKELETON_SKULL);  // 衰弱
        m.put("hex", Material.FERMENTED_SPIDER_EYE);      // 呪詛
        m.put("fangs", Material.BONE_BLOCK);              // 牙
        m.put("sonic_boom", Material.ECHO_SHARD);         // ソニックブーム
        m.put("heavy_impact", Material.MACE);             // ヘビーインパクト
        m.put("solar", Material.SUNFLOWER);               // 日輪
        m.put("lunar", Material.GLOWSTONE);               // 月輪
        m.put("flare", Material.BLAZE_ROD);               // 閃炎

        // ===== 効果 / 移動系（9種）=====
        m.put("launch", Material.SLIME_BLOCK);            // 打ち上げ
        m.put("leap", Material.RABBIT_FOOT);              // 跳躍
        m.put("bounce", Material.SLIME_BALL);             // 跳弾
        m.put("speed_boost", Material.GOLDEN_BOOTS);      // 指向
        m.put("gale", Material.FEATHER);                  // 疾風
        m.put("slowfall", Material.PHANTOM_MEMBRANE);     // 低速落下
        m.put("levitate", Material.SHULKER_SHELL);        // 浮遊
        m.put("blink", Material.ENDER_PEARL);             // 瞬間移動
        m.put("glide", Material.ELYTRA);                  // 滑空

        // ===== 効果 / 生存系（9種）=====
        m.put("heal", Material.GOLDEN_APPLE);             // 回復
        m.put("saturation", Material.BREAD);              // 満腹
        m.put("shield", Material.SHIELD);                 // 盾
        m.put("invisibility", Material.GLASS_BOTTLE);     // 透明
        m.put("bubble", Material.SOUL_SAND);              // 泡
        m.put("dispel", Material.MILK_BUCKET);            // 解呪
        m.put("journey", Material.FILLED_MAP);            // 旅路の魔法
        m.put("scale", Material.PUFFERFISH);              // スケール
        m.put("sense_magic", Material.SPYGLASS);          // 魔力感知

        // ===== 効果 / ブロック系（16種）=====
        m.put("break", Material.IRON_PICKAXE);            // 破壊
        m.put("advanced_break", Material.NETHERITE_PICKAXE); // 高度破壊
        m.put("light", Material.TORCH);                   // 光明
        m.put("grow", Material.BONE_MEAL);                // 成長
        m.put("harvest", Material.WHEAT);                 // 収穫
        m.put("cut", Material.SHEARS);                    // 刈取
        m.put("fell", Material.IRON_AXE);                 // 伐採
        m.put("place_block", Material.BRICKS);            // 設置
        m.put("phantom_block", Material.GLASS);           // 幻影
        m.put("exchange", Material.STONECUTTER);          // 交換
        m.put("smelt", Material.FURNACE);                 // 精錬
        m.put("crush", Material.GRAVEL);                  // 粉砕
        m.put("explosion", Material.TNT);                 // 爆発
        m.put("evaporate", Material.SPONGE);              // 蒸発
        m.put("conjure_water", Material.WATER_BUCKET);    // 水生成
        m.put("intangible", Material.STRUCTURE_VOID);     // 透過

        // ===== 効果 / ユーティリティ系（20種）=====
        m.put("interact", Material.LEVER);                // 操作
        m.put("pickup", Material.HOPPER);                 // 拾得
        m.put("rotate", Material.COMPASS);                // 回転
        m.put("infuse", Material.BREWING_STAND);          // 注入
        m.put("craft", Material.CRAFTING_TABLE);          // 作業台
        m.put("rune", Material.STONE_PRESSURE_PLATE);     // 罠術
        m.put("rewind", Material.RECOVERY_COMPASS);       // 巻き戻し
        m.put("wololo", Material.PINK_DYE);               // 色彩
        m.put("name", Material.NAME_TAG);                 // 命名
        m.put("firework", Material.FIREWORK_ROCKET);      // 花火
        m.put("prestidigitation", Material.FIREWORK_STAR);// 手品
        m.put("cry", Material.GOAT_HORN);                 // 鳴き声
        m.put("summon_steed", Material.SADDLE);           // 馬召喚
        m.put("summon_wolves", Material.BONE);            // 狼召喚
        m.put("animate", Material.ARMOR_STAND);           // ゴーレム召喚
        m.put("summon_undead", Material.ZOMBIE_HEAD);     // 不死召喚
        m.put("summon_vex", Material.SOUL_LANTERN);       // ヴェックス召喚
        m.put("summon_decoy", Material.CARVED_PUMPKIN);   // デコイ召喚
        m.put("toss", Material.SNOWBALL);                 // 投擲
        m.put("reset", Material.BUCKET);                  // 初期化

        // ===== 増強（22種）=====
        m.put("amplify", Material.GLOWSTONE_DUST);        // 増幅
        m.put("dampen", Material.REDSTONE);               // 減衰
        m.put("extend_time", Material.CLOCK);             // 延長
        m.put("duration_down", Material.SAND);            // 短縮
        m.put("extend_reach", Material.LEAD);             // 延伸
        m.put("shrink_reach", Material.STRING);           // 収縮
        m.put("accelerate", Material.SUGAR);              // 加速
        m.put("decelerate", Material.SOUL_SOIL);          // 減速
        m.put("aoe", Material.LIME_DYE);                  // 範囲（幅）
        m.put("aoe_height", Material.LIGHT_BLUE_DYE);     // 範囲（上下）
        m.put("aoe_vertical", Material.CYAN_DYE);         // 範囲（奥行き）
        m.put("aoe_radius", Material.YELLOW_DYE);         // 半径増加
        m.put("pierce", Material.SPECTRAL_ARROW);         // 貫通
        m.put("split", Material.AMETHYST_CLUSTER);        // 分裂
        m.put("extract", Material.ENCHANTED_BOOK);        // 抽出
        m.put("fortune", Material.GOLD_INGOT);            // 幸運
        m.put("propagate", Material.SCULK_CATALYST);      // 伝播
        m.put("linger", Material.LINGERING_POTION);       // 残留
        m.put("rapid_fire", Material.CROSSBOW);           // 連射
        m.put("trace", Material.TARGET);                  // 軌跡
        m.put("delay", Material.REPEATER);                // 遅延
        m.put("randomize", Material.SUSPICIOUS_STEW);     // 無作為

        // ===== 超増強（15種）— ベースの「格上版」に見える材質を選ぶ =====
        m.put("super_amplify", Material.SEA_LANTERN);
        m.put("super_dampen", Material.REDSTONE_BLOCK);
        m.put("super_extend_time", Material.DAYLIGHT_DETECTOR);
        m.put("super_duration_down", Material.RED_SAND);
        m.put("super_extend_reach", Material.IRON_CHAIN);  // 1.21.9 で CHAIN -> IRON_CHAIN に改名
        m.put("super_shrink_reach", Material.TRIPWIRE_HOOK);
        m.put("super_accelerate", Material.POWERED_RAIL);
        m.put("super_decelerate", Material.HONEY_BLOCK);
        m.put("super_aoe_radius", Material.ORANGE_DYE);
        m.put("super_pierce", Material.TIPPED_ARROW);
        m.put("super_split", Material.AMETHYST_BLOCK);
        m.put("super_fortune", Material.GOLD_BLOCK);
        m.put("super_propagate", Material.SCULK_SHRIEKER);
        m.put("super_linger", Material.DRAGON_BREATH);
        m.put("super_delay", Material.COMPARATOR);

        // Map.copyOf にしないのは意図的 —— 不変Mapは get(null) で NPE を投げる。
        return java.util.Collections.unmodifiableMap(m);
    }

    /** 定義済みのグリフIDと材質。テストと突き合わせ用。 */
    public static Map<String, Material> defaults() {
        return ICONS;
    }

    /**
     * <b>未解放グリフのアイコン。</b>
     *
     * <p>2026-08-22 のユーザー報告「解放したのか解放してないのか直感的にわからなくなった。
     * 今まで解放してるやつだけカーソル移動すればよかったのに、今は一個ずつ見ないといけない」。
     * 全 120 種へ個別アイコンを付けた結果、<b>解放状態が絵から消えた</b>のがこの不満の正体で、
     * 名前の色と lore だけでは 36 個/ページの一覧を見渡したときに読み取れない。
     *
     * <p>そこで<b>未解放だけ</b>を1種類の絵に潰す。解放済みは個別アイコンのままなので、
     * 「どれがどの魔法か分からない」（同日の別報告）へは戻らない ——
     * <b>実際に使うのは解放済みの側</b>だからこの非対称でよい。
     *
     * <p><b>材質は石炭＝{@code fc97b4f} 以前の仕様に戻したもの</b>（2026-08-22 指示）。
     * 一度 {@code GRAY_DYE} にしたが、<b>スキルツリーの未解放パークが南京錠アイコン</b>
     * （TF 側 {@code SkillTreeGuiVisuals}、リソパの {@code gui/node_locked}）なので、
     * 未解放の絵はそちらと役割を分けたい。既存プレイヤーが覚えている絵をそのまま使う方が
     * 学習コストがゼロで済むという判断。
     */
    public static final Material LOCKED_ICON = Material.COAL;

    /**
     * 解放状態を織り込んだアイコン。<b>解放/未解放を並べる画面は必ずこちらを通す。</b>
     *
     * @param comp 対象グリフ
     * @param config {@code glyphs.yml}。{@code icon:} を書いてあればそれが最優先。null 可
     * @param unlocked 解放済みなら true。false なら {@link #LOCKED_ICON} に潰す
     */
    public static Material iconFor(SpellComponent comp, GlyphConfig config, boolean unlocked) {
        if (!unlocked) {
            return LOCKED_ICON;
        }
        return iconFor(comp, config);
    }

    /**
     * 解放状態を織り込んだ解決。{@link #iconFor(SpellComponent, GlyphConfig, boolean)} の本体で、
     * テストからも直接叩く（{@code SpellComponent} を組み立てずに全キーを回せる）。
     *
     * <p>未解放は {@code icon:} の上書きより優先して {@link #LOCKED_ICON} にする ——
     * yml を書いた1個だけ解放済みに見えるのを避けるため。
     */
    public static Material resolveForState(String glyphKey, SpellComponent.ComponentType type,
                                           String override, boolean unlocked) {
        if (!unlocked) {
            return LOCKED_ICON;
        }
        return resolve(glyphKey, type, override);
    }

    /**
     * グリフのアイコン。3画面から必ずここを通す。
     *
     * <p>解放状態を持つ画面は {@link #iconFor(SpellComponent, GlyphConfig, boolean)} を使うこと。
     * こちらは「解放状態と無関係に、そのグリフの絵が欲しい」場所（詳細表示など）向け。
     *
     * @param comp 対象グリフ
     * @param config {@code glyphs.yml}。{@code icon:} を書いてあればそれが最優先。null 可
     */
    public static Material iconFor(SpellComponent comp, GlyphConfig config) {
        if (comp == null) {
            return Material.PAPER;
        }
        String key = comp.getId().getKey();
        return resolve(key, comp.getType(), config == null ? null : config.iconOverride(key));
    }

    /**
     * yml を介さない解決。{@link #iconFor} の本体で、テストからも直接叩く。
     *
     * <p>未定義のグリフは種類ごとの既定へ落とす —— <b>例外にしない</b>のは、
     * 新しいグリフを1個足しただけで筆記台が開かなくなるのを避けるため。
     * 定義漏れは {@code GlyphIconCoverageTest} が先に落とす。
     */
    public static Material resolve(String glyphKey, SpellComponent.ComponentType type, String override) {
        Material parsed = parse(override);
        if (parsed != null) {
            return parsed;
        }
        Material icon = glyphKey == null ? null : ICONS.get(glyphKey);
        if (icon != null) {
            return icon;
        }
        return TYPE_FALLBACK.getOrDefault(type, Material.PAPER);
    }

    /**
     * {@code icon: BLAZE_ROD} の解釈。綴り違いは黙って無視して既定へ落とす
     * （yml の打ち間違いで筆記台が開かなくなる方が困る）。
     *
     * <p>{@code Material#matchMaterial} ではなく {@code getMaterial} を使うのは、
     * 前者が旧名の解決でサーバ({@code Bukkit.getUnsafe()})に触れる場合があるため。
     */
    private static Material parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Material.getMaterial(name.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
