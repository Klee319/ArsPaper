package com.arspaper.enchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;

/**
 * 回生エンチャント - アイテムが壊れる瞬間にエンチャントを消費して耐久値を全回復する。
 *
 * 耐久値が0になるダメージを受けた時:
 * 1. アイテムの破壊をキャンセル
 * 2. 回生エンチャントをアイテムから削除
 * 3. 耐久値を全回復（ダメージ=0）
 * 4. Loreから回生の表示を除去
 *
 * 再度付けたい場合は儀式でエンチャント本を作り直して金床で適用する。
 */
public class SoulboundListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!ArsEnchantments.hasSoulbound(item)) return;
        if (!(item.getItemMeta() instanceof Damageable damageable)) return;

        int maxDamage = damageable.hasMaxDamage()
            ? damageable.getMaxDamage()
            : item.getType().getMaxDurability();
        int currentDamage = damageable.getDamage();
        int newDamage = currentDamage + event.getDamage();

        // まだ壊れない → 通常通りダメージを受ける
        if (newDamage < maxDamage) return;

        // 壊れる瞬間 → 回生発動
        event.setCancelled(true);

        item.editMeta(meta -> {
            // エンチャント削除
            meta.removeEnchant(ArsEnchantments.getSoulbound());

            // 耐久値全回復
            if (meta instanceof Damageable d) {
                d.setDamage(0);
            }

            // Loreから回生の表示を除去
            if (meta.lore() != null) {
                String displayName = ArsEnchantments.getDisplayName("soulbound");
                List<Component> lore = new ArrayList<>(meta.lore());
                lore.removeIf(line -> {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(line);
                    return plain.contains(displayName);
                });
                meta.lore(lore);
            }
        });

        // エフェクト
        Player player = event.getPlayer();
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
            player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
        player.sendMessage(Component.text("回生が発動し装備が復活した！", NamedTextColor.GREEN));
    }
}
