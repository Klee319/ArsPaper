package com.arspaper.block;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * sourcejars.yml からソースジャーの容量・見た目定義を読む。
 */
public final class SourceJarConfig {

    /**
     * sourcejars.yml が読めなかった/ジャーidが引けなかったときの容量。
     *
     * <p>出荷 yml の {@code jars.source_jar.capacity} と同じ値にしておくこと
     * (ズレていると「設定が読めない時だけ容量が変わる」= 再現しない不具合になる)。
     * 2026-08-08 に 10,000 → 20,000。理由は sourcejars.yml の該当コメント。
     */
    public static final int FALLBACK_CAPACITY = 20000;

    public record JarDef(
            String id,
            Material material,
            String displayName,
            int customModelData,
            int capacity,
            List<String> lore,
            double transferMultiplier) {

        /** 旧来の6引数呼び出し（{@code transfer-multiplier} 未指定 = 1.0）。 */
        public JarDef(String id, Material material, String displayName,
                      int customModelData, int capacity, List<String> lore) {
            this(id, material, displayName, customModelData, capacity, lore, 1.0);
        }

        /** capacity &lt; 0 → 無限。 */
        public boolean infinite() {
            return capacity < 0;
        }

        public int effectiveCapacity() {
            return infinite() ? Integer.MAX_VALUE : Math.max(0, capacity);
        }
    }

    private final JavaPlugin plugin;
    private Map<String, JarDef> jars = Map.of();

    public SourceJarConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "sourcejars.yml");
        if (!file.exists()) {
            plugin.saveResource("sourcejars.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Logger log = plugin.getLogger();
        ConfigurationSection root = yaml.getConfigurationSection("jars");
        Map<String, JarDef> parsed = new LinkedHashMap<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) {
                    continue;
                }
                try {
                    Material mat = Material.valueOf(sec.getString("material", "DECORATED_POT")
                            .trim().toUpperCase(Locale.ROOT));
                    int capacity = sec.getInt("capacity", FALLBACK_CAPACITY);
                    parsed.put(id, new JarDef(
                            id,
                            mat,
                            sec.getString("display-name", id),
                            sec.getInt("custom-model-data", 0),
                            capacity,
                            List.copyOf(sec.getStringList("lore")),
                            readTransferMultiplier(sec, id, log)));
                } catch (IllegalArgumentException ex) {
                    log.warning("[sourcejars.yml] jar '" + id + "' invalid: " + ex.getMessage());
                }
            }
        }
        if (parsed.isEmpty()) {
            parsed.put("source_jar", new JarDef("source_jar", Material.DECORATED_POT,
                    "ソースジャー", 200002, FALLBACK_CAPACITY, List.of("魔法のソースエネルギーを貯蔵")));
            parsed.put("creative_source_jar", new JarDef("creative_source_jar", Material.DECORATED_POT,
                    "クリエイティブソースジャー", 200003, -1, List.of("無限のソースエネルギーを供給")));
        }
        this.jars = Collections.unmodifiableMap(parsed);
        log.info("[sourcejars.yml] loaded " + jars.size() + " jar definition(s)");
    }

    public Optional<JarDef> get(String id) {
        return Optional.ofNullable(jars.get(id));
    }

    public int capacityOf(String id) {
        return get(id).map(JarDef::effectiveCapacity).orElse(FALLBACK_CAPACITY);
    }

    /**
     * {@code jars.<id>.transfer-multiplier} — <b>網（ドミニオンワンドで結んだ経路）で
     * このジャーが送信元になったときの転送量倍率</b>（2026-08-25 / W-257）。
     *
     * <p>未設定・不正値は 1.0。それまで網には階梯倍率が一切掛からず、
     * <b>上位ジャーにしても毎秒2.5点で固定</b>だった（隣接供給の
     * {@code items.<id>.transfer-multiplier} は「ソースリンク→隣のジャー」にしか効かない）。
     * 容量だけが階梯で伸びて速度が伸びないと、上位ジャーは「大きいだけで遅い箱」になる。
     */
    public double transferMultiplierOf(String id) {
        return get(id).map(JarDef::transferMultiplier).orElse(1.0);
    }

    /**
     * 倍率の読み取り。0以下・非有限は設定ミスとして警告のうえ 1.0 へ倒す
     * （{@code SourcelinkConfig#readTransferMultiplier} と同じ方針 —— 壊す方向の typo を
     * 黙って通すと「なぜか転送が止まった」になり原因が追えない）。
     */
    private static double readTransferMultiplier(ConfigurationSection sec, String id, Logger log) {
        if (!sec.isSet("transfer-multiplier")) {
            return 1.0;
        }
        double raw = sec.getDouble("transfer-multiplier", 1.0);
        if (!Double.isFinite(raw) || raw <= 0) {
            log.warning("[sourcejars.yml] jar '" + id + "'.transfer-multiplier=" + raw
                    + " is invalid (must be > 0) — falling back to 1.0");
            return 1.0;
        }
        return raw;
    }

    public Map<String, JarDef> all() {
        return jars;
    }

    /**
     * sourcejars.yml に定義済みのジャーidかどうか。
     *
     * <p>2026-07-31 追加。儀式のソース吸い出し(RitualManager)・ソースリンクの注ぎ込み(Sourcelink)・
     * パーティクル(BlockParticleTask)が {@code "source_jar".equals(blockId)} と決め打ちしていたため、
     * yml に上位ジャーを足しても「置けるが儀式もソースリンクも見ないブロック」になっていた。
     * 判定をこの1箇所へ集約して、yml に足すだけで全経路が追従するようにする。
     */
    public boolean isJar(String id) {
        return id != null && jars.containsKey(id);
    }
}
