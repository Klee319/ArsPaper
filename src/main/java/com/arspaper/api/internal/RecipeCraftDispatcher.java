package com.arspaper.api.internal;

import com.arspaper.api.ArsAPI;
import com.arspaper.api.event.ArsItemCraftedEvent;
import com.arspaper.api.event.ArsRecipeCraftPreEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.Keyed;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * 作業台クラフトの ArsRecipeCraftPreEvent / ArsItemCraftedEvent を発火する。
 * ArsPaper が登録した NamespacedKey のレシピのみ対象 (バニラレシピは無視)。
 */
public final class RecipeCraftDispatcher implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!ArsAPI.isInitialized()) return;
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();
        // ArsPaper 登録レシピのみ
        if (!"arspaper".equals(key.getNamespace())) return;

        CraftingInventory inv = event.getInventory();
        ItemStack preview = inv.getResult();
        if (preview == null || preview.getType().isAir()) return;

        HumanEntity viewer = event.getView().getPlayer();
        if (!(viewer instanceof Player p)) return;

        List<ItemStack> ingredients = new ArrayList<>();
        for (ItemStack ing : inv.getMatrix()) {
            if (ing != null && !ing.getType().isAir()) ingredients.add(ing.clone());
        }

        ArsRecipeCraftPreEvent pre = new ArsRecipeCraftPreEvent(
            p, key.toString(), preview, ingredients);
        Bukkit.getPluginManager().callEvent(pre);

        if (pre.isCancelled()) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!ArsAPI.isInitialized()) return;
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();
        if (!"arspaper".equals(key.getNamespace())) return;

        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player p)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null) return;

        int rollSeed = (int) (System.currentTimeMillis() ^ p.getUniqueId().hashCode());
        ArsItemCraftedEvent post = new ArsItemCraftedEvent(
            p, key.toString(), result.clone(), rollSeed);
        Bukkit.getPluginManager().callEvent(post);

        // setResultStack で個数倍化・品質付与等が行われた場合は反映
        ItemStack modified = post.getResultStack();
        if (modified != null && modified != result && !modified.isSimilar(result)) {
            event.setCurrentItem(modified);
        } else if (modified != null && modified.getAmount() != result.getAmount()) {
            event.setCurrentItem(modified);
        }
    }
}
