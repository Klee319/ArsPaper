package com.arspaper.source.sourcelink;

import com.arspaper.block.BlockKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Vitalic Sourcelink - 生命の力でSourceを生成。
 * バニラのBARREL（樽）をベースに使用。
 *
 * 基本の定期生成に加え、近くでmobが死亡するとボーナスSourceをバッファに蓄積。
 * バッファ分はティック時に隣接Source Jarへ供給される。
 */
public class VitalicSourcelink extends Sourcelink {

    private static final int SOURCE_PER_TICK = 5;
    /** mob死亡時のボーナスSource */
    public static final int SOURCE_PER_KILL = 15;
    /**
     * mob死亡検知範囲（ブロック）の既定値。
     *
     * <p>2026-08-01: 実際に使う値は {@code sourcelinks.yml} の
     * {@code transfer.sourcelink.detection-radius.vitalic}（既定10 = 移設前と同値）。
     * 参照元は {@link com.arspaper.source.SourcelinkTickTask#onEntityDeath}。
     */
    public static final int DETECTION_RADIUS =
            com.arspaper.source.SourceTransferConfig.DEFAULT_VITALIC_DETECTION_RADIUS;

    public VitalicSourcelink(JavaPlugin plugin) {
        super(plugin, "vitalic_sourcelink");
    }

    /** カスタムソースリンク (sourcelinks.yml items.<id> type: vitalic) 用: 任意idで同じ挙動の別ブロックを作る。 */
    public VitalicSourcelink(JavaPlugin plugin, String blockId) {
        super(plugin, blockId);
    }

    @Override
    public Material getBlockMaterial() {
        return materialOr(Material.BARREL);
    }

    @Override
    public Component getDisplayName() {
        return displayNameOr(Component.text("バイタリックソースリンク", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
    }

    @Override
    public int getCustomModelData() {
        return cmdOr(200006);
    }

    @Override
    public ItemStack createItemStack() {
        return withConfiguredOrDefaultLore(super.createItemStack(), List.of(
                Component.text("生命の力でソースを生成", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("近くでmobが倒されるとボーナス生成", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
        ));
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.BONE);
        head.editMeta(meta -> meta.setCustomModelData(getCustomModelData()));
        return head;
    }

    @Override
    public SourceYield generateSource(Block block) {
        // バッファ(mob死亡ボーナス分) + 受動の基本生成。
        // ⚠ SOURCE_PER_TICK は「バッファ由来ではない」ので passive 側へ入れる。ここを fromBuffer に
        //    混ぜると、隣接ジャーが無い/満杯のとき毎周期 +5 がバッファへ積み上がり、
        //    ジャーの容量という上限が消えて実質無制限の貯蔵庫になる(2026-08-01 の実バグ)。
        // 受動生成にも階梯の生成量倍率(items.<id>.yield-multiplier)を掛ける
        // (バイタリックは燃料を焼べないので、ここを掛けないと上位階梯の生成量が伸びない)。
        return SourceYield.of(drainBuffer(block), scaleGeneratedYield(SOURCE_PER_TICK));
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        int buffer = getBuffer(tileState);
        player.sendMessage(Component.text(
            "バイタリックソースリンク - ボーナス蓄積: " + buffer, NamedTextColor.GREEN
        ));
    }

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        tileState.getPersistentDataContainer().set(
            BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0
        );
        tileState.update();
    }
}
