package com.arspaper.gui;

import com.arspaper.ArsPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 複数の防具部位にbackpackスレッドがある場合に、開く部位を選ばせる選択GUI。
 * ボタン式（Geyser互換）。クリックでその部位のバックパックを開く。
 */
public class BackpackSelectionGui extends BaseGui {

    private static final int[] BUTTON_SLOTS = {10, 12, 14, 16};

    private final List<EquipmentSlot> slots;
    private final Map<Integer, EquipmentSlot> buttonToSlot = new HashMap<>();

    public BackpackSelectionGui(Player viewer, List<EquipmentSlot> slots) {
        super(viewer, 3, Component.text("バックパック選択", NamedTextColor.DARK_GREEN));
        this.slots = slots;
    }

    @Override
    public void render() {
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        buttonToSlot.clear();

        for (int i = 0; i < slots.size() && i < BUTTON_SLOTS.length; i++) {
            EquipmentSlot s = slots.get(i);
            ItemStack armor = viewer.getInventory().getItem(s);
            Material icon = (armor != null && !armor.getType().isAir()) ? armor.getType() : Material.CHEST;
            int pos = BUTTON_SLOTS[i];
            inventory.setItem(pos, createButton(icon,
                Component.text(slotLabel(s), NamedTextColor.GOLD),
                List.of(Component.text("クリックで開く", NamedTextColor.GRAY))));
            buttonToSlot.put(pos, s);
        }
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        EquipmentSlot armorSlot = buttonToSlot.get(slot);
        if (armorSlot == null) return true;

        // クリックイベント内での直接openは避け、1tick後に開く
        clicker.closeInventory();
        Bukkit.getScheduler().runTask(ArsPaper.getInstance(), () -> {
            ItemStack armor = clicker.getInventory().getItem(armorSlot);
            if (armor != null && BackpackGui.countBackpackThreads(armor) > 0) {
                BackpackGui.open(clicker, armor, armorSlot);
            }
        });
        return true;
    }

    private static String slotLabel(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "ヘルメット";
            case CHEST -> "チェストプレート";
            case LEGS -> "レギンス";
            case FEET -> "ブーツ";
            default -> slot.name();
        };
    }
}
