package com.arspaper.spell;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * プレイヤーごとの恒久解放グリフ集合を PDC に永続化するヘルパー。
 *
 * <p>UsageGate の perk ゲートとは独立した加算専用(OR)の解放ルート。
 * 一度解放したグリフは perk の有無に関わらず使用可能になる。
 *
 * <p>シリアライズは ASCII unit separator (0x1F) 区切りの単一文字列で行う
 * （com.trinityforge.pdc.PlayerData.heldPerks() と同じ idiom を踏襲。
 * ただし本クラスはこの fork 独自の PDC キーを使用し、TrinityForge 側の
 * キーとは衝突しない）。
 */
public final class UnlockedGlyphs {

    /** ASCII unit separator (0x1F): グリフキーに出現しない安全な区切り文字。 */
    private static final String DELIM = String.valueOf((char) 0x1F);

    private final NamespacedKey key;

    public UnlockedGlyphs(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "unlocked_glyphs");
    }

    /** プレイヤーが解放済みのグリフキー集合（不変）。 */
    public Set<String> unlocked(Player player) {
        String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return Set.copyOf(deserialize(raw));
    }

    /** 指定グリフキーが解放済みかどうか。 */
    public boolean contains(Player player, String glyphKey) {
        return unlocked(player).contains(glyphKey);
    }

    /** 指定グリフキー群を既存の解放集合に加算(OR)し、永続化する。 */
    public void add(Player player, Collection<String> glyphKeys) {
        Objects.requireNonNull(glyphKeys, "glyphKeys");
        Set<String> merged = new HashSet<>(unlocked(player));
        for (String glyphKey : glyphKeys) {
            if (glyphKey != null && !glyphKey.isBlank()) {
                merged.add(glyphKey);
            }
        }
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, serialize(merged));
    }

    /**
     * 区切り文字混入ガード付きでSetを直列化する。
     * Bukkit型に依存しない純粋関数（テストフレームワーク未整備のためユニットテストは
     * 追加していないが、単体テスト可能な形に切り出してある）。
     */
    static String serialize(Collection<String> glyphKeys) {
        List<String> cleaned = glyphKeys.stream()
            .filter(s -> s != null && !s.isBlank())
            .toList();
        for (String s : cleaned) {
            if (s.indexOf(0x1F) >= 0) {
                throw new IllegalArgumentException("glyph key must not contain the 0x1F delimiter: " + s);
            }
        }
        return String.join(DELIM, cleaned);
    }

    /**
     * 直列化文字列をグリフキーのリストへ復元する。
     * Bukkit型に依存しない純粋関数（上記と同様、単体テスト可能な形に切り出してある）。
     */
    static List<String> deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(DELIM))
            .filter(s -> !s.isBlank())
            .toList();
    }
}
