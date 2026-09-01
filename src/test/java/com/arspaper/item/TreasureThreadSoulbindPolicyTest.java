package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-259 スレッド魂縛の判定と<b>配線</b>を固定する。対象は catalog の bind-type
 * （作れる種は TRADEABLE、作れない種は SOULBOUND）。CMD 帯は TF 未ロード時のフォールバック。
 *
 * <p>純関数の検査だけだと「policy は正しいが誰も呼んでいない」で緑になる ──
 * 実際、TF 側の {@code PickupQualityListener} は PDC の都合でこの10種に一度も発火しない。
 * そこで装着ゲートがソース上に存在することも合わせて縛る。
 */
class TreasureThreadSoulbindPolicyTest {

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    /**
     * ⚠ {@link ThreadType} をテストから触ってはいけない。{@code PotionEffectType} の
     * 静的初期化を踏むのでサーバ無しではクラスロードできず、
     * {@code ExceptionInInitializerError} でテストが<b>中断</b>する
     * (赤にはなるが「何を検査したか」が消える)。CMD 側の純関数と
     * {@code ThreadType.java} のソース走査で代替する。
     */
    @Test
    @DisplayName("CMD フォールバックはトレジャー帯(300070-300079)だけ — TF 未ロード時用")
    void onlyTreasureThreadsAreSoulbound() {
        // catalog の bind-type が正。こちらは TrinityForge が居ないときの後方互換。
        assertTrue(TreasureThreadSoulbindPolicy.isSoulboundCmd(300070), "渦動 = 帯の下限(含む)");
        assertTrue(TreasureThreadSoulbindPolicy.isSoulboundCmd(300079), "護法 = 帯の上限(含む)");

        assertFalse(TreasureThreadSoulbindPolicy.isSoulboundCmd(300069),
                "剛靭(300069)はトレジャー帯の外。帯の1つ手前という境界でもある");
        assertFalse(TreasureThreadSoulbindPolicy.isSoulboundCmd(300080),
                "隠密(300080)はトレジャー帯の外。帯の1つ先という境界でもある");
        assertFalse(TreasureThreadSoulbindPolicy.isSoulboundCmd(300006),
                "暗視(300006)はトレジャー帯の外");
        assertFalse(TreasureThreadSoulbindPolicy.isSoulbound(null),
                "未知の id は縛らない(未知を弾くと設定ミスで装着不能になる)");
    }

    @Test
    @DisplayName("未刻印は誰でも使える / 刻印済みは本人だけ")
    void ownershipGate() {
        UUID owner = UUID.nameUUIDFromBytes("owner".getBytes(StandardCharsets.UTF_8));
        UUID other = UUID.nameUUIDFromBytes("other".getBytes(StandardCharsets.UTF_8));

        // チェストの中・地面に落ちている間はまだ持ち主が居ない。ここを false にすると
        // 「拾えるのに誰も使えない」アイテムになる。
        assertTrue(TreasureThreadSoulbindPolicy.mayUse(null, owner));
        assertTrue(TreasureThreadSoulbindPolicy.mayUse(owner, owner));
        assertFalse(TreasureThreadSoulbindPolicy.mayUse(owner, other));
        assertFalse(TreasureThreadSoulbindPolicy.mayUse(owner, null));
    }

    @Test
    @DisplayName("装着ゲートが ThreadGui に配線されている(刻印だけでは譲渡を止められない)")
    void socketGateIsWired() throws IOException {
        String gui = read("src/main/java/com/arspaper/gui/ThreadGui.java");
        assertTrue(gui.contains("TreasureThreadSoulbindPolicy.isSoulbound(threadType)"),
                "ThreadGui が魂縛の判定を呼んでいない。刻印だけでは"
                        + "『他人の個体を挿して装備ごと渡す』を止められない");
        assertTrue(gui.contains("TreasureThreadSoulbindPolicy.mayUse("),
                "ThreadGui が所有者判定を呼んでいない");

        String plugin = read("src/main/java/com/arspaper/ArsPaper.java");
        assertTrue(plugin.contains("new com.arspaper.item.ThreadSoulbindListener(this)"),
                "刻印リスナーが registerEvents されていない = 所有者が永久に付かない"
                        + "(ゲートは通るが誰も縛られない、という無言の無効化)");

        String listener = read("src/main/java/com/arspaper/item/ThreadSoulbindListener.java");
        assertTrue(listener.contains("InventoryCloseEvent"),
                "チェスト取り出しは EntityPickupItemEvent を飛ばすので、閉じたときに刻印する経路が要る");
        assertTrue(listener.contains("PlayerJoinEvent"),
                "導入前からインベントリに居る個体は拾得も閉じるも飛ばないので、参加時に走査する");
        assertTrue(listener.contains("PlayerInventorySlotChangeEvent"),
                "HuskSync は参加直後は空で、あとからスロットへ直接書き込む。閉じるのを待たない");
        assertTrue(listener.contains("getEnderChest()"),
                "エンダーチェストの未刻印はインベントリ走査だけでは届かない");
        assertTrue(listener.contains("JOIN_REFRESH_DELAY_TICKS = 40L"),
                "参加直後は HuskSync が空のことがある。TF の join 再適用と同じ 40tick 後にもう一度走査する");
        assertTrue(listener.contains("applyCatalogBindType")
                        || listener.contains("catalogAutoStampsOwner")
                        || read("src/main/java/com/arspaper/item/TreasureThreadSoulbindPolicy.java")
                                .contains("catalogAutoStampsOwner"),
                "魂縛対象は catalog の bind-type を読むこと(CMD 帯のハードコードだけだとエディタ設定が効かない)");
    }

    @Test
    @DisplayName("抜け道3つが全部塞がっている(挿してから譲渡 / 挿して外して洗浄 / セット効果だけ乗る)")
    void theThreeLoopholesAreClosed() throws IOException {
        // ユーザーが最初に指摘した抜け道:
        //   「そもそも他の人にスレッド付きの武器とかが渡された場合にどうやって対処しよう」
        // ThreadGui のゲートは「他人のスレッドを挿す」しか止められないので、
        // 【自分で挿してから装備ごと渡す】には効かない。装着時にも切る必要がある。
        String armor = read("src/main/java/com/arspaper/item/ArmorManaListener.java");
        assertTrue(armor.contains("TreasureThreadSoulbindPolicy.mayUse(equipped.owner(), player.getUniqueId())"),
                "装着時のゲートが無い = 自分で挿してから装備ごと渡せば効果が乗る");

        // ⚠ counts への加算より前で切ること。数えてしまうと thread-sets.yml の
        //   セット効果だけが他人にも乗る(本体は切れているので気づきにくい)。
        int gate = armor.indexOf("TreasureThreadSoulbindPolicy.isSoulbound(thread)");
        int counted = armor.indexOf("totals.counts.merge(thread, 1, Integer::sum)");
        assertTrue(gate > 0 && counted > gate,
                "魂縛の判定が counts への加算より後にある = セット効果だけ他人にも乗る");

        // 取り外しは createThreadItemStack で新品を作るので、書き戻さないと
        // 「挿して外す」だけで未刻印の個体が手に入る(魂縛の洗浄)。
        String gui = read("src/main/java/com/arspaper/gui/ThreadGui.java");
        assertTrue(gui.contains("ownerAt(slotIndex)"),
                "取り外し時に所有者を渡していない = 挿して外すだけで魂縛を洗浄できる");
        String restore = read("src/main/java/com/arspaper/gui/SocketedThreadReturn.java");
        assertTrue(restore.contains("restoreSoulboundOwner(threadItem, ownerUuid)"),
                "SocketedThreadReturn が所有者を書き戻していない");
        assertTrue(gui.contains("setOwnerAt(slotIndex, threadOwner == null"),
                "装着時に所有者を装備側へ写していない = 書き戻す元が無い");
    }

    @Test
    @DisplayName("帯の中に実在するスレッドがちょうど10種ある(帯だけ動いて中身が付いてこない事故の検出)")
    void bandMatchesTheActualThreadTypes() throws IOException {
        // ⚠ トレジャースレッド10種は ThreadType.java に定数として書かれて【いない】。
        //   threads.yml に custom-model-data を書いて ThreadType.register() で
        //   実行時登録される枠なので、CMD の真源は yml 側。
        //   (ThreadType.java を走査すると 0 件になり、isSoulbound は「常に false」で
        //    静かに通ってしまう ── この検査はまさにそれを捕まえるために置いてある)
        String source = read("src/main/resources/threads.yml");
        int inBand = 0;
        for (int cmd = TreasureThreadSoulbindPolicy.TREASURE_CMD_MIN;
             cmd < TreasureThreadSoulbindPolicy.TREASURE_CMD_MAX; cmd++) {
            if (source.contains("custom-model-data: " + cmd)) {
                inBand++;
            }
        }
        assertTrue(inBand == 10,
                "帯 " + TreasureThreadSoulbindPolicy.TREASURE_CMD_MIN + "-"
                        + (TreasureThreadSoulbindPolicy.TREASURE_CMD_MAX - 1) + " のスレッドが "
                        + inBand + " 種(トレジャースレッドは10種のはず)。増やしたなら帯の上限も動かすこと");
    }
}
