package com.arspaper.mana;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * マナシステムの設定値。
 * config.ymlから読み込む。
 *
 * <h2>マナ基礎値3項目の所在(2026-07-25 → 2026-08-16 で往復している)</h2>
 * <ul>
 *   <li>2026-07-25 (config editor T2): {@code default-max} / {@code default-regen-rate} /
 *       {@code regen-interval-ticks} / {@code recovery.*}(on-hit/on-attack/idle 系7項目)を
 *       {@code config.yml} から削除し、TrinityForge の「プレイヤー基礎ステータス」
 *       ({@code combat/base-stats.yml})へ移設した。</li>
 *   <li><b>2026-08-16(今回)</b>: このうち<b>3項目だけ</b>
 *       ({@code mana.default-max} / {@code mana.default-regen-rate} /
 *       {@code mana.regen-interval-ticks})を config.yml へ戻した。TF 側の
 *       {@code combat/base-stats.yml} は {@code stats/lore.yml} に登録が無いキーを
 *       設定エディタのどの画面にも出さないため、この3項目だけが
 *       <b>手編集でしか変えられない設定</b>として取り残されていたのが理由
 *       (エディタの「ArsPaper 全体設定 (config)」画面から編集できるようにした)。</li>
 * </ul>
 *
 * <p>⚠ <b>残り5項目({@code mana-onhit-percent} / {@code mana-onattack-percent} /
 * {@code mana-idle-seconds} / {@code mana-idle-bonus-percent} / {@code mana-idle-bonus-flat})は
 * 今も TrinityForge 側が真源</b>で、{@link ManaRecoveryListener} / {@link ManaManager} が
 * {@link ManaBaseStats} 経由で都度取得する(TF側の {@code /trinityforge reload} に即追随する)。
 * 「マナ関連はすべて config.yml にある」と誤読しないこと。
 */
public record ManaConfig(
    // ---- マナ基礎値(2026-08-16 に TF combat/base-stats.yml から出戻り) ----
    // 最大マナの土台。グリフ/防具/スレッド/エンチャント/スキルの加算はこの上に乗る。
    int defaultMax,
    // 1インターバルあたりの自然回復量。
    int defaultRegenRate,
    // 自然回復の周期(tick)。20 = 1秒。
    // ※ 実際にスケジュールされる周期は ManaManager の生成時に焼き込まれるので、
    //   この値を変えても /ars reload では反映されずサーバ再起動が要る。
    int regenIntervalTicks,
    int manaPerGlyphUnlock,
    // マナ最大値%上昇の上限（%）
    int maxPercentCap,
    // 要件⑥ source-auto-consume: マナ不足時にインベントリから消費してマナへ変換するアイテム。
    // itemId(Arsカスタムid または TFカタログid) -> マナ変換量とCT。
    Map<String, SourceAutoConsumeItem> sourceAutoConsumeItems,
    // 2026-08-14 追加: 自動消費のクールタイム(秒)。0以下でCT無し(従来挙動)。
    // スキルツリー ars_smithing.yml A-2「ソースベリー活用」の説明文は当初から「100マナ/10CT」と
    // 書いてあったが、CT判定は一度も実装されておらずマナ不足のたびに無制限に変換できていた。
    // アイテム側で cooldown-seconds を書いていない場合の既定値として使う。
    int sourceAutoConsumeCooldownSeconds
) {
    /** CT未設定時の既定値(秒)。ノード説明「100マナ/10CT」の 10 をそのまま秒として採る。 */
    public static final int DEFAULT_SOURCE_AUTO_CONSUME_COOLDOWN_SECONDS = 10;

    // マナ基礎値の既定。稼働中サーバの plugins/ArsPaper/config.yml には
    // ArsPaper#updateResourceFiles が config.yml を再抽出しない都合で新キーが降ってこないため、
    // 「キーが1行も無いサーバでの実効値」がそのままこの3定数になる。
    // 移設前(TF combat/base-stats.yml の mana-max-base / mana-regen-base /
    // mana-regen-interval-ticks)と同値にしてバランスを動かさないこと。
    /** 最大マナの土台の既定値。 */
    public static final int DEFAULT_MAX_MANA = 100;
    /** 1インターバルあたりの自然回復量の既定値。 */
    public static final int DEFAULT_REGEN_RATE = 5;
    /** 自然回復の周期(tick)の既定値。20 = 1秒。 */
    public static final int DEFAULT_REGEN_INTERVAL_TICKS = 20;

    /**
     * 自動消費アイテム1件分の設定。
     *
     * <p>2026-08-14: CTを全体1本からアイテム単位へ拡張した(ユーザー指示「マナ回復量とCTが
     * それぞれ設定できるべき」)。CTはアイテムごとに独立して進むので、ソースベリーを使った直後でも
     * 別アイテムは即使える。
     *
     * @param manaPerItem     1個あたりのマナ変換量(正の整数)
     * @param cooldownSeconds このアイテム専用のCT(秒)。{@code null} なら全体既定
     *                        ({@link #sourceAutoConsumeCooldownSeconds})を使う。
     *                        <b>0 は「CT無し」という別の意味</b>なので null と混同しないこと。
     */
    public record SourceAutoConsumeItem(int manaPerItem, Integer cooldownSeconds) {
        /** 全体既定を当てはめた実効CT(秒)。 */
        public int effectiveCooldownSeconds(int defaultCooldownSeconds) {
            return cooldownSeconds != null ? Math.max(0, cooldownSeconds) : Math.max(0, defaultCooldownSeconds);
        }
    }

    /**
     * config.yml からマナ設定を読む。
     *
     * <p>マナ基礎値3項目のガード方針(2026-08-16 移設時に決めたもの)。移設前は TF 側が
     * 「0 のキーはロード時に捨てる」規約だったため<b>0 は原理的に届かなかった</b>が、
     * ここは {@code getInt} なので<b>設定エディタから 0 を保存できてしまう</b>。
     * 数値以外を書いた場合は {@code getInt} が既定値を返す(Bukkit の仕様)。
     * <ul>
     *   <li>{@code default-max}: 1以上へクランプ。0 だと魔法が一切撃てず、
     *       マナバーの割合表示も意味を失う。「土台0＋グリフ加算だけで伸ばす」構成を潰さないよう、
     *       既定値100へ戻すのではなく 1 で止める。</li>
     *   <li>{@code default-regen-rate}: 0 は「自然回復なし」という正当な設定なので許可し、
     *       負値だけ 0 へ寄せる(負の回復＝毎周期マナが減る、は事故しか生まない)。</li>
     *   <li>{@code regen-interval-ticks}: <b>1以上へクランプ必須</b>。0 以下を
     *       {@code runTaskTimer} の period に渡すと周期タスクが壊れる。</li>
     * </ul>
     */
    public static ManaConfig fromConfig(FileConfiguration config) {
        return new ManaConfig(
            Math.max(1, config.getInt("mana.default-max", DEFAULT_MAX_MANA)),
            Math.max(0, config.getInt("mana.default-regen-rate", DEFAULT_REGEN_RATE)),
            Math.max(1, config.getInt("mana.regen-interval-ticks", DEFAULT_REGEN_INTERVAL_TICKS)),
            config.getInt("mana.per-glyph-unlock-bonus", 5),
            // 既存挙動を変えない安全デフォルト（上昇上限100%）。
            clampPercent(config.getInt("mana.max-percent-cap", 100)),
            parseSourceAutoConsumeItems(config),
            // 負値は0(CT無し)として扱う。上限は設けない(運用で長いCTを置きたい場合がある)。
            Math.max(0, config.getInt("mana.source-auto-consume.cooldown-seconds",
                DEFAULT_SOURCE_AUTO_CONSUME_COOLDOWN_SECONDS))
        );
    }

    /**
     * {@code mana.source-auto-consume.items} をパースする。
     * 非正値/id空欄のエントリは警告してスキップする（設定ミスで消費0個変換が成立しないよう防ぐ）。
     *
     * <p>2種類の記法を読む。<b>旧記法(数値のみ)を読めなくすると、既存 config.yml の自動消費が
     * まるごと無効化される</b>ので必ず両方扱う:
     * <pre>
     *   source_berry: 100                 # 旧記法: マナ変換量のみ。CTは全体既定
     *   source_berry:                     # 新記法(2026-08-14): アイテムごとにCTを持てる
     *     mana: 100
     *     cooldown-seconds: 5             # 省略時は全体既定。0 は「CT無し」
     * </pre>
     */
    private static Map<String, SourceAutoConsumeItem> parseSourceAutoConsumeItems(FileConfiguration config) {
        Map<String, SourceAutoConsumeItem> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("mana.source-auto-consume.items");
        if (section == null) {
            return result;
        }
        for (String rawId : section.getKeys(false)) {
            if (rawId == null || rawId.isBlank()) {
                Bukkit.getLogger()
                    .warning("[ArsPaper] mana.source-auto-consume.items に空のidが指定されました。スキップします。");
                continue;
            }
            String id = normalizeItemId(rawId);
            if (id.isEmpty()) {
                Bukkit.getLogger().warning(
                    "[ArsPaper] mana.source-auto-consume.items の '" + rawId + "' は id が空です。スキップします。");
                continue;
            }
            if (result.containsKey(id)) {
                Bukkit.getLogger().warning(
                    "[ArsPaper] mana.source-auto-consume.items に '" + id
                        + "' が重複しています(custom: 有無の違いを含む)。先に書かれた方を使います: " + rawId);
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(rawId);
            int manaPerItem = entry != null ? entry.getInt("mana", 0) : section.getInt(rawId, 0);
            if (manaPerItem <= 0) {
                Bukkit.getLogger().warning(
                    "[ArsPaper] mana.source-auto-consume.items." + id
                        + " のマナ変換量は正の整数である必要があります。スキップします: " + manaPerItem);
                continue;
            }
            // キーが無い(=全体既定に従う)のと 0(=CT無し)を区別するため contains で見る。
            Integer cooldownSeconds = null;
            if (entry != null && entry.contains("cooldown-seconds")) {
                int raw = entry.getInt("cooldown-seconds", DEFAULT_SOURCE_AUTO_CONSUME_COOLDOWN_SECONDS);
                if (raw < 0) {
                    Bukkit.getLogger().warning(
                        "[ArsPaper] mana.source-auto-consume.items." + id
                            + ".cooldown-seconds は0以上である必要があります。0(CT無し)として扱います: " + raw);
                }
                cooldownSeconds = Math.max(0, raw);
            }
            result.put(id, new SourceAutoConsumeItem(manaPerItem, cooldownSeconds));
        }
        return result;
    }

    /**
     * 設定キーのアイテムidを、実行時に照合する形へ正規化する。
     *
     * <p><b>2026-08-14 バグ修正</b>: 出荷 config.yml は {@code custom:source_berry: 100} と書かれていたが、
     * 照合側の {@code PdcHelper#getCrossPluginItemId} が返すのは PDC に入っている素のid
     * ({@code source_berry}) なので<b>1件も一致せず、ソース自動消費は一度も発動していなかった</b>。
     * 設定エディタのアイテム選択UI(materialInput)がカスタム品を {@code custom:} 付きで書き出すため、
     * 人手で直しても編集し直すたびに戻る。読み込み側で落とすのが唯一の恒久策。
     *
     * <p>{@code list:} は落とさない ── 互換リストはレシピ素材の語彙で、ここでは1個のアイテムidしか
     * 意味を持たないため、書かれていたら「一致しないid」として扱う方が誤爆しない。
     */
    public static String normalizeItemId(String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.regionMatches(true, 0, "custom:", 0, "custom:".length())) {
            id = id.substring("custom:".length()).trim();
        }
        return id;
    }

    /** %系設定を 0..1000 にクランプする。 */
    private static int clampPercent(int value) {
        return Math.max(0, Math.min(1000, value));
    }
}
