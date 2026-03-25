package com.arspaper.command;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.gui.BackpackGui;
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
                            for (String eid : new String[]{"mana_regen", "mana_boost", "share"}) {
                                for (int lv = 1; lv <= 3; lv++) {
                                    builder.suggest("enchant_book:" + eid + ":" + lv);
                                }
                            }
                            return builder.buildFuture();
                        })
                        // /ars give <itemId> (自分に1個)
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(
                                    Component.text("プレイヤー専用コマンドです！", NamedTextColor.RED));
                                return 0;
                            }
                            String itemId = StringArgumentType.getString(ctx, "itemId");
                            if (itemId.startsWith("enchant_book:")) return executeGiveEnchantBook(player, itemId);
                            return executeGive(plugin, player, itemId, 1);
                        })
                        // /ars give <itemId> <count>
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player player)) {
                                    ctx.getSource().getSender().sendMessage(
                                        Component.text("プレイヤー専用コマンドです！", NamedTextColor.RED));
                                    return 0;
                                }
                                String itemId = StringArgumentType.getString(ctx, "itemId");
                                int count = IntegerArgumentType.getInteger(ctx, "count");
                                if (itemId.startsWith("enchant_book:")) return executeGiveEnchantBook(player, itemId);
                                return executeGive(plugin, player, itemId, count);
                            })
                            // /ars give <itemId> <count> <player>
                            .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                                        builder.suggest(p.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String itemId = StringArgumentType.getString(ctx, "itemId");
                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    Player target = org.bukkit.Bukkit.getPlayer(targetName);
                                    if (target == null) {
                                        ctx.getSource().getSender().sendMessage(
                                            Component.text("プレイヤーが見つかりません: " + targetName, NamedTextColor.RED));
                                        return 0;
                                    }
                                    if (itemId.startsWith("enchant_book:")) return executeGiveEnchantBook(target, itemId);
                                    return executeGive(plugin, target, itemId, count);
                                })
                            )
                        )
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
                .then(Commands.literal("fixmana")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .executes(ctx -> {
                        // 自分のマナを修正
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        return executeFixMana(plugin, ctx.getSource().getSender(), player);
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            Player target = org.bukkit.Bukkit.getPlayer(name);
                            if (target == null) {
                                ctx.getSource().getSender().sendMessage(
                                    Component.text("プレイヤーが見つかりません: " + name, NamedTextColor.RED));
                                return 0;
                            }
                            return executeFixMana(plugin, ctx.getSource().getSender(), target);
                        })
                    )
                )
                .then(Commands.literal("reload")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .executes(ctx -> {
                        return executeReload(plugin, ctx.getSource().getSender(), false);
                    })
                    .then(Commands.literal("reset")
                        .executes(ctx -> {
                            return executeReload(plugin, ctx.getSource().getSender(), true);
                        })
                    )
                )
                .then(Commands.literal("backpack")
                    .executes(ctx -> {
                        return executeBackpack(ctx.getSource().getSender());
                    })
                )
                .then(Commands.literal("recipes")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        new com.arspaper.gui.RecipeBrowserGui(player).open();
                        return 1;
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
                .then(Commands.literal("ranking")
                    .then(Commands.literal("glyphs")
                        .executes(ctx -> executeRankingGlyphs(plugin, ctx.getSource().getSender()))
                    )
                    .then(Commands.literal("mana")
                        .executes(ctx -> executeRankingMana(plugin, ctx.getSource().getSender()))
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

    private static int executeGiveEnchantBook(Player player, String spec) {
        // enchant_book:<enchantId>:<level>
        String[] parts = spec.split(":");
        if (parts.length < 3) {
            player.sendMessage(Component.text(
                "形式: enchant_book:<enchantId>:<level> (例: enchant_book:mana_regen:1)", NamedTextColor.RED));
            return 0;
        }
        String enchantId = parts[1];
        int level;
        try {
            level = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("レベルは数値で指定してください", NamedTextColor.RED));
            return 0;
        }

        var enchant = com.arspaper.enchant.ArsEnchantments.getFromId(enchantId);
        if (enchant == null) {
            player.sendMessage(Component.text(
                "不明なエンチャント: " + enchantId + " (mana_regen/mana_boost/share)", NamedTextColor.RED));
            return 0;
        }

        level = Math.max(1, Math.min(level, com.arspaper.enchant.ArsEnchantments.MAX_LEVEL));
        String displayName = com.arspaper.enchant.ArsEnchantments.getDisplayName(enchantId);
        String roman = com.arspaper.enchant.ArsEnchantments.toRoman(level);

        ItemStack book = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        final int finalLevel = level;
        book.editMeta(meta -> {
            meta.displayName(Component.text(displayName + " " + roman, net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(
                ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING, "enchant_book"
            );
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchant, finalLevel, true);
            }
            meta.lore(java.util.List.of(
                Component.text(displayName + " " + roman, net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("金床でメイジアーマーに適用", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            ));
        });

        player.getInventory().addItem(book);
        player.sendMessage(Component.text(displayName + " " + roman + " のエンチャント本を付与しました", NamedTextColor.GREEN));
        return 1;
    }

    private static int executeGive(ArsPaper plugin, Player player, String itemId, int count) {
        var optItem = plugin.getItemRegistry().get(itemId);
        if (optItem.isEmpty()) {
            player.sendMessage(Component.text("不明なアイテム: " + itemId, NamedTextColor.RED));
            return 0;
        }
        for (int i = 0; i < count; i++) {
            ItemStack stack = optItem.get().createItemStack();
            player.getInventory().addItem(stack);
        }
        String msg = count > 1
            ? itemId + " x" + count + " を " + player.getName() + " に付与しました"
            : itemId + " を " + player.getName() + " に付与しました";
        player.sendMessage(Component.text(msg, NamedTextColor.GREEN));
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

    private static int executeReload(ArsPaper plugin, CommandSender sender, boolean resetDefaults) {
        long start = System.currentTimeMillis();

        // reset: 設定ファイルをデフォルトに上書き復元
        if (resetDefaults) {
            String[] ymlFiles = {"config.yml", "glyphs.yml", "items.yml", "materials.yml", "armors.yml", "threads.yml"};
            for (String yml : ymlFiles) {
                plugin.saveResource(yml, true); // true = 上書き
            }
            sender.sendMessage(Component.text("全設定ファイルをデフォルトにリセットしました", NamedTextColor.YELLOW));
        }

        // config.yml リロード
        plugin.reloadConfig();

        // glyphs.yml リロード（グリフのティア・コスト・係数）
        plugin.reloadGlyphConfig();

        // materials.yml / armors.yml を先にリロード（レシピ解決に必要）
        plugin.reloadMaterialConfig();
        plugin.reloadArmorConfig();

        // 統合レシピリロード（items.yml, materials.yml, threads.yml）
        com.arspaper.recipe.UnifiedRecipeLoader loader = new com.arspaper.recipe.UnifiedRecipeLoader(plugin);
        loader.loadAll();
        plugin.getRecipeManager().unloadRecipes();
        plugin.getRecipeManager().registerWorkbenchRecipes(loader.getWorkbenchRecipes());
        plugin.getRitualRecipeRegistry().registerRecipes(loader.getRitualRecipes());

        // 防具レシピ再登録（armorConfigリロード済み）
        plugin.getRecipeManager().registerArmorRecipes(plugin.getArmorConfigManager());

        // スレッド設定リロード
        plugin.getThreadConfig().reload(plugin.getConfig());

        // マナ設定リロード（regenIntervalの変更はサーバ再起動が必要）
        com.arspaper.mana.ManaConfig newManaConfig = com.arspaper.mana.ManaConfig.fromConfig(plugin.getConfig());
        plugin.getManaManager().reloadConfig(newManaConfig);

        // ルートチェスト設定リロード
        plugin.reloadLootConfig();

        long elapsed = System.currentTimeMillis() - start;
        sender.sendMessage(Component.text(
            "ArsPaper設定をリロードしました (" + elapsed + "ms)", NamedTextColor.GREEN));
        return 1;
    }

    private static int executeBackpack(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ使用可能", NamedTextColor.RED));
            return 0;
        }

        // 装備中の防具からバックパックスレッドを検索
        for (org.bukkit.inventory.ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && BackpackGui.countBackpackThreads(armor) > 0) {
                BackpackGui.open(player, armor);
                return 1;
            }
        }
        player.sendMessage(Component.text("バックパックスレッドが装備されていません", NamedTextColor.RED));
        return 0;
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

    /**
     * グリフ解放数からマナボーナスを再計算し、不正な値を修正する。
     */
    private static int executeFixMana(ArsPaper plugin, CommandSender sender, Player target) {
        // 現在の解放済みグリフ数を取得
        String json = target.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        int glyphCount = 0;
        if (json != null) {
            try {
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                glyphCount = arr.size();
            } catch (Exception ignored) {}
        }

        int perGlyphBonus = plugin.getConfig().getInt("mana.per-glyph-unlock-bonus", 5);
        int correctBonus = glyphCount * perGlyphBonus;
        int currentBonus = target.getPersistentDataContainer()
            .getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);

        target.getPersistentDataContainer().set(
            ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, correctBonus);

        // 現在マナが新上限を超えている場合はクランプ
        int newMax = plugin.getManaManager().getMaxMana(target);
        int currentMana = plugin.getManaManager().getCurrentMana(target);
        if (currentMana > newMax) {
            plugin.getManaManager().setCurrentMana(target, newMax);
        }

        int diff = currentBonus - correctBonus;
        sender.sendMessage(Component.text(
            target.getName() + " のマナボーナスを修正: " + currentBonus + " → " + correctBonus
                + " (グリフ" + glyphCount + "個 × " + perGlyphBonus + ")"
                + (diff > 0 ? " §c(-" + diff + " 修正)" : " §a(正常)"),
            NamedTextColor.GREEN));
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
        // オフハンドまたはホットバー9番スロットからバインド対象を検索
        ItemStack offhand = findBindTarget(player);
        if (offhand == null) {
            player.sendMessage(Component.text("オフハンドまたはホットバー9番スロットにバインド先のアイテムを持ってください", NamedTextColor.RED));
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

    /**
     * バインド対象アイテムを検索。オフハンド → ホットバースロット8(固定)。
     */
    private static ItemStack findBindTarget(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!offhand.getType().isAir()) return offhand;
        ItemStack slot8 = player.getInventory().getItem(8);
        if (slot8 != null && !slot8.getType().isAir()) {
            if (!slot8.hasItemMeta()) return slot8;
            String customId = slot8.getItemMeta().getPersistentDataContainer()
                .get(com.arspaper.item.ItemKeys.CUSTOM_ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING);
            if (customId == null || (!customId.contains("spell_book") && !customId.contains("wand"))) {
                return slot8;
            }
        }
        return null;
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

    private static int executeRankingGlyphs(ArsPaper plugin, CommandSender sender) {
        int totalGlyphs = plugin.getSpellRegistry().getAll().size();
        record Entry(String name, int count, boolean online) {}
        java.util.Map<String, Entry> entryMap = new java.util.LinkedHashMap<>();

        // キャッシュからオフライン含む全プレイヤーを読み込み
        for (var cached : plugin.getManaManager().getRankingCache().getAll().entrySet()) {
            var data = cached.getValue();
            entryMap.put(cached.getKey(), new Entry(data.name(), data.glyphCount(), false));
        }

        // オンラインプレイヤーの最新データで上書き
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            String json = p.getPersistentDataContainer()
                .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
            int count = 0;
            if (json != null) {
                try {
                    JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                    count = arr.size();
                } catch (Exception ignored) {}
            }
            entryMap.put(p.getUniqueId().toString(), new Entry(p.getName(), count, true));
        }

        List<Entry> entries = new ArrayList<>(entryMap.values());
        entries.sort((a, b) -> Integer.compare(b.count(), a.count()));

        sender.sendMessage(Component.text("=== グリフ解放数ランキング ===", NamedTextColor.GOLD));
        int rank = 0;
        for (Entry entry : entries) {
            rank++;
            if (rank > 10) break;
            String medal = switch (rank) {
                case 1 -> "§6①";
                case 2 -> "§7②";
                case 3 -> "§c③";
                default -> "§8" + rank;
            };
            double pct = totalGlyphs > 0 ? (entry.count() * 100.0 / totalGlyphs) : 0;
            String onlineMarker = entry.online() ? "" : " §8[OFF]";
            sender.sendMessage(Component.text(
                medal + " " + entry.name() + onlineMarker + " §f" + entry.count() + "/" + totalGlyphs
                    + " §7(" + String.format("%.0f", pct) + "%)"
            ));
        }
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("データがありません", NamedTextColor.GRAY));
        }
        return 1;
    }

    private static int executeRankingMana(ArsPaper plugin, CommandSender sender) {
        record Entry(String name, long consumed, boolean online) {}
        java.util.Map<String, Entry> entryMap = new java.util.LinkedHashMap<>();

        // キャッシュからオフライン含む全プレイヤーを読み込み
        for (var cached : plugin.getManaManager().getRankingCache().getAll().entrySet()) {
            var data = cached.getValue();
            entryMap.put(cached.getKey(), new Entry(data.name(), data.manaConsumed(), false));
        }

        // オンラインプレイヤーの最新データで上書き
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            long consumed = plugin.getManaManager().getTotalManaConsumed(p);
            entryMap.put(p.getUniqueId().toString(), new Entry(p.getName(), consumed, true));
        }

        List<Entry> entries = new ArrayList<>(entryMap.values());
        entries.sort((a, b) -> Long.compare(b.consumed(), a.consumed()));

        sender.sendMessage(Component.text("=== マナ消費量ランキング ===", NamedTextColor.AQUA));
        int rank = 0;
        for (Entry entry : entries) {
            rank++;
            if (rank > 10) break;
            String medal = switch (rank) {
                case 1 -> "§6①";
                case 2 -> "§7②";
                case 3 -> "§c③";
                default -> "§8" + rank;
            };
            String formatted = formatNumber(entry.consumed());
            String onlineMarker = entry.online() ? "" : " §8[OFF]";
            sender.sendMessage(Component.text(
                medal + " " + entry.name() + onlineMarker + " §b" + formatted + " マナ"
            ));
        }
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("データがありません", NamedTextColor.GRAY));
        }
        return 1;
    }

    private static String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
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
