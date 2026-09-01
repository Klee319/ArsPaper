package com.arspaper.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧いTF作業台レシピで作られた catalog_id だけのスレッドを、装着時に救済する配線を固定する。
 * ThreadType は Bukkit の初期化を要するため、ここでは実行時生成ではなく実装契約を検証する。
 */
class ThreadLegacyCatalogRecoveryWiringTest {

    @Test
    @DisplayName("catalog_idだけの旧スレッドをArs識別子へ復元してから装着判定する")
    void recoversLegacyCatalogThreadBeforeSocketEligibilityCheck() throws IOException {
        String item = Files.readString(Path.of("src/main/java/com/arspaper/item/impl/ThreadItem.java"));
        String gui = Files.readString(Path.of("src/main/java/com/arspaper/gui/ThreadGui.java"));

        assertTrue(item.contains("PdcHelper.getCrossPluginItemId(item)"),
                "TF catalog_id を読まずに復元すると、旧い作業台クラフト品を救済できない");
        assertTrue(item.contains("ItemKeys.CUSTOM_ITEM_ID")
                        && item.contains("ItemKeys.THREAD_ITEM_TYPE"),
                "装着判定に必要な Ars の2つの識別子を復元していない");
        assertTrue(gui.contains("ThreadItem.restoreFunctionalMetadata(item)"),
                "装着可否の判定より前に旧個体の識別情報を復元していない");
    }
}
