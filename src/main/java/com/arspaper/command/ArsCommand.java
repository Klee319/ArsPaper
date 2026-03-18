package com.arspaper.command;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * /ars コマンドルート。Brigadier API使用。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ArsCommand {

    private ArsCommand() {}

    public static void register(Commands commands, ArsPaper plugin) {
        commands.register(
            Commands.literal("ars")
                .then(Commands.literal("give")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .then(Commands.argument("itemId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            plugin.getItemRegistry().getAll().forEach(item ->
                                builder.suggest(item.getItemId())
                            );
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(
                                    Component.text("プレイヤー専用コマンドです！", NamedTextColor.RED)
                                );
                                return 0;
                            }
                            String itemId = StringArgumentType.getString(ctx, "itemId");
                            return executeGive(plugin, player, itemId);
                        })
                    )
                )
                .then(Commands.literal("mana")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        return executeManaInfo(plugin, player);
                    })
                )
                .then(Commands.literal("cleanup")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        return executeCleanup(player);
                    })
                )
                .then(Commands.literal("debug")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .then(Commands.literal("mana")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return executeDebugMana(plugin, player);
                        })
                    )
                )
                .then(Commands.literal("reload")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .executes(ctx -> {
                        return executeReload(plugin, ctx.getSource().getSender());
                    })
                )
                .then(Commands.literal("pvp")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("on");
                            builder.suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String state = StringArgumentType.getString(ctx, "state");
                            return executePvpToggle(plugin, ctx.getSource().getSender(), state);
                        })
                    )
                )
                .then(Commands.literal("glyph")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .then(Commands.literal("unlockall")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return executeGlyphUnlockAll(plugin, player);
                        })
                    )
                    .then(Commands.literal("lockall")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return executeGlyphLockAll(player);
                        })
                    )
                )
                .then(Commands.literal("spell")
                    .then(Commands.literal("list")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return executeSpellList(plugin, player);
                        })
                    )
                    .then(Commands.literal("set")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 10))
                            .then(Commands.argument("spellDef", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                                    int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                    String spellDef = StringArgumentType.getString(ctx, "spellDef");
                                    return executeSpellSet(plugin, player, slot, spellDef);
                                })
                            )
                        )
                    )
                    .then(Commands.literal("bind")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 10))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                                int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                return executeSpellBind(plugin, player, slot);
                            })
                        )
                    )
                    .then(Commands.literal("unbind")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return executeSpellUnbind(player);
                        })
                    )
                )
                .build(),
            "ArsPaper main command",
            List.of("arspaper")
        );
    }

    private static int executeGive(ArsPaper plugin, Player player, String itemId) {
        var optItem = plugin.getItemRegistry().get(itemId);
        if (optItem.isEmpty()) {
            player.sendMessage(Component.text("不明なアイテム: " + itemId, NamedTextColor.RED));
            return 0;
        }
        ItemStack stack = optItem.get().createItemStack();
        player.getInventory().addItem(stack);
        player.sendMessage(Component.text(itemId + " を付与しました", NamedTextColor.GREEN));
        return 1;
    }

    private static int executeManaInfo(ArsPaper plugin, Player player) {
        int current = plugin.getManaManager().getCurrentMana(player);
        int max = plugin.getManaManager().getMaxMana(player);
        player.sendMessage(Component.text("マナ: " + current + " / " + max, NamedTextColor.AQUA));
        return 1;
    }

    private static int executeCleanup(Player player) {
        int removed = 0;
        for (ArmorStand stand : player.getWorld().getEntitiesByClass(ArmorStand.class)) {
            if (stand.getPersistentDataContainer().has(BlockKeys.DISPLAY_MARKER)) {
                stand.remove();
                removed++;
            }
        }
        player.sendMessage(Component.text(
            "ArsPaper表示用ArmorStandを" + removed + "体除去しました", NamedTextColor.GREEN));
        return 1;
    }

    private static int executeDebugMana(ArsPaper plugin, Player player) {
        boolean enabled = plugin.getManaManager().toggleInfiniteMana(player);
        if (enabled) {
            player.sendMessage(Component.text("マナ無限モード: ON", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("マナ無限モード: OFF", NamedTextColor.YELLOW));
        }
        return 1;
    }

    private static int executeReload(ArsPaper plugin, CommandSender sender) {
        long start = System.currentTimeMillis();

        // config.yml リロード
        plugin.reloadConfig();

        // glyphs.yml リロード（グリフのティア・コスト・係数）
        plugin.reloadGlyphConfig();

        // rituals.yml リロード
        plugin.getRitualRecipeRegistry().loadRecipes();

        // recipes.yml リロード
        plugin.getRecipeManager().unloadRecipes();
        plugin.getRecipeManager().loadRecipes();

        long elapsed = System.currentTimeMillis() - start;
        sender.sendMessage(Component.text(
            "ArsPaper設定をリロードしました (" + elapsed + "ms)", NamedTextColor.GREEN));
        return 1;
    }

    private static int executePvpToggle(ArsPaper plugin, CommandSender sender, String state) {
        boolean enabled;
        if ("on".equalsIgnoreCase(state)) {
            enabled = true;
        } else if ("off".equalsIgnoreCase(state)) {
            enabled = false;
        } else {
            sender.sendMessage(Component.text("使い方: /ars pvp <on|off>", NamedTextColor.RED));
            return 0;
        }
        plugin.getConfig().set("pvp.enabled", enabled);
        plugin.saveConfig();
        sender.sendMessage(Component.text(
            "スペルPvP: " + (enabled ? "ON" : "OFF"),
            enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        return 1;
    }

    private static int executeGlyphUnlockAll(ArsPaper plugin, Player player) {
        Set<String> allGlyphs = new HashSet<>();
        for (SpellComponent comp : plugin.getSpellRegistry().getAll()) {
            allGlyphs.add(comp.getId().toString());
        }

        JsonArray arr = new JsonArray();
        allGlyphs.forEach(arr::add);
        player.getPersistentDataContainer().set(
            ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING, new Gson().toJson(arr)
        );

        player.sendMessage(Component.text(
            allGlyphs.size() + " 個の全グリフをアンロックしました", NamedTextColor.GREEN));
        return 1;
    }

    private static int executeGlyphLockAll(Player player) {
        player.getPersistentDataContainer().remove(ManaKeys.UNLOCKED_GLYPHS);
        player.sendMessage(Component.text(
            "全グリフをロックしました", NamedTextColor.YELLOW));
        return 1;
    }

    private static int executeSpellBind(ArsPaper plugin, Player player, int slot) {
        // オフハンドのアイテムにバインド
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType().isAir()) {
            player.sendMessage(Component.text("オフハンドにバインド先のアイテムを持ってください", NamedTextColor.RED));
            return 0;
        }

        // メインハンドのスペルブックからスペルを取得
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        String customId = mainHand.hasItemMeta()
            ? mainHand.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING)
            : null;
        if (customId == null || (!customId.startsWith("spell_book_") && !customId.startsWith("wand_"))) {
            player.sendMessage(Component.text("メインハンドにスペルブック/ワンドを持ってください", NamedTextColor.RED));
            return 0;
        }

        String slotsJson = mainHand.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) {
            player.sendMessage(Component.text("スペルが設定されていません", NamedTextColor.RED));
            return 0;
        }

        java.util.List<com.arspaper.spell.SpellRecipe> slots =
            com.arspaper.spell.SpellSerializer.deserializeSlots(slotsJson, plugin.getSpellRegistry());
        int idx = slot - 1;
        if (idx >= slots.size() || slots.get(idx) == null) {
            player.sendMessage(Component.text("スロット" + slot + "にスペルがありません", NamedTextColor.RED));
            return 0;
        }

        // スペルブックにUUIDがなければ付与
        com.arspaper.item.impl.SpellBook.getOrCreateUUID(mainHand);
        // mainHandはスペルブック、offhandがバインド先アイテム
        return com.arspaper.spell.SpellBindListener.bindSpell(player, offhand, mainHand, idx, slots.get(idx)) ? 1 : 0;
    }

    private static int executeSpellUnbind(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir()) {
            player.sendMessage(Component.text("バインド解除するアイテムを手に持ってください", NamedTextColor.RED));
            return 0;
        }
        return com.arspaper.spell.SpellBindListener.unbindSpell(player, mainHand) ? 1 : 0;
    }

    private static int executeSpellList(ArsPaper plugin, Player player) {
        player.sendMessage(Component.text("=== 利用可能なグリフ ===", NamedTextColor.GOLD));

        player.sendMessage(Component.text("形態(Form): ", NamedTextColor.GREEN)
            .append(Component.text(
                plugin.getSpellRegistry().getForms().stream()
                    .map(SpellForm::getDisplayName)
                    .collect(Collectors.joining(", ")),
                NamedTextColor.WHITE
            )));

        player.sendMessage(Component.text("効果(Effect): ", NamedTextColor.YELLOW)
            .append(Component.text(
                plugin.getSpellRegistry().getEffects().stream()
                    .map(SpellEffect::getDisplayName)
                    .collect(Collectors.joining(", ")),
                NamedTextColor.WHITE
            )));

        player.sendMessage(Component.text("増強(Augment): ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(
                plugin.getSpellRegistry().getAugments().stream()
                    .map(SpellAugment::getDisplayName)
                    .collect(Collectors.joining(", ")),
                NamedTextColor.WHITE
            )));

        return 1;
    }

    private static int executeSpellSet(ArsPaper plugin, Player player, int slot, String spellDef) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String customId = hand.hasItemMeta()
            ? hand.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING)
            : null;

        if (customId == null || !customId.startsWith("spell_book_")) {
            player.sendMessage(Component.text("スペルブックを手に持って実行してください！", NamedTextColor.RED));
            return 0;
        }

        String[] parts = spellDef.split("\\s+");
        if (parts.length < 1) {
            player.sendMessage(Component.text("使い方: /ars spell set <スロット> <名前>:<形態> <効果/増強...>", NamedTextColor.RED));
            return 0;
        }

        String nameAndForm = parts[0];
        String[] nf = nameAndForm.split(":", 2);
        String spellName = nf[0];
        if (nf.length < 2) {
            player.sendMessage(Component.text("形式: <スペル名>:<形態> <効果...>", NamedTextColor.RED));
            return 0;
        }

        SpellRegistry registry = plugin.getSpellRegistry();
        List<SpellComponent> components = new ArrayList<>();

        SpellComponent form = registry.get("arspaper:" + nf[1].toLowerCase());
        if (form == null || form.getType() != SpellComponent.ComponentType.FORM) {
            player.sendMessage(Component.text("不明な形態: " + nf[1], NamedTextColor.RED));
            return 0;
        }
        components.add(form);

        for (int i = 1; i < parts.length; i++) {
            SpellComponent comp = registry.get("arspaper:" + parts[i].toLowerCase());
            if (comp == null) {
                player.sendMessage(Component.text("不明なグリフ: " + parts[i], NamedTextColor.RED));
                return 0;
            }
            components.add(comp);
        }

        SpellRecipe recipe = new SpellRecipe(spellName, components);
        if (!recipe.isValid()) {
            player.sendMessage(Component.text("無効なスペルです！先頭はFormである必要があります", NamedTextColor.RED));
            return 0;
        }

        String existingSlotsJson = hand.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);

        List<SpellRecipe> slots;
        if (existingSlotsJson != null) {
            slots = new ArrayList<>(SpellSerializer.deserializeSlots(existingSlotsJson, registry));
        } else {
            slots = new ArrayList<>();
        }

        int slotIndex = slot - 1;
        while (slots.size() <= slotIndex) {
            slots.add(null);
        }
        slots.set(slotIndex, recipe);

        String newSlotsJson = SpellSerializer.serializeSlots(slots);
        hand.editMeta(meta ->
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOTS, PersistentDataType.STRING, newSlotsJson
            )
        );

        player.sendMessage(Component.text(
            "スロット" + slot + "に設定: " + spellName + " (マナコスト: " + recipe.getTotalManaCost() + ")",
            NamedTextColor.GREEN
        ));
        return 1;
    }
}
