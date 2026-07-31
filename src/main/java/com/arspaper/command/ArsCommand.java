package com.arspaper.command;

import com.arspaper.ArsPaper;
import com.arspaper.command.handlers.AdminCommands;
import com.arspaper.command.handlers.GiveCommands;
import com.arspaper.command.handlers.GlyphCommands;
import com.arspaper.command.handlers.HelpCommands;
import com.arspaper.command.handlers.RankingCommands;
import com.arspaper.command.handlers.SpellCommands;
import com.arspaper.command.handlers.StatusCommands;
import com.arspaper.command.handlers.ThreadCommands;
import com.arspaper.command.handlers.WorldCommands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /ars コマンドルート。Brigadier API使用。
 *
 * <p>各サブコマンドの実装は {@code com.arspaper.command.handlers} 配下のハンドラクラスに分割されている。
 * このクラスは Brigadier の配線（引数・権限・サジェスト）のみを担う薄いファサードである。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ArsCommand {

    private ArsCommand() {}

    public static void register(Commands commands, ArsPaper plugin) {
        commands.register(
            Commands.literal("ars")
                // 引数なしの /ars はコマンド一覧。Brigadier の補完だけが発見経路だと
                // 「存在を知っている人しか辿れない」機能ができる(2026-07-31 F3 指摘1)。
                .executes(ctx -> HelpCommands.executeHelp(ctx.getSource().getSender()))
                .then(Commands.literal("help")
                    .executes(ctx -> HelpCommands.executeHelp(ctx.getSource().getSender()))
                )
                .then(Commands.literal("give")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .then(Commands.argument("itemId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            plugin.getItemRegistry().getAll().forEach(item -> {
                                String id = item.getItemId();
                                if (ArsGiveAllowlist.isAllowed(id)) {
                                    builder.suggest(id);
                                }
                            });
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
                            if (!ArsGiveAllowlist.isAllowed(itemId)) {
                                ctx.getSource().getSender().sendMessage(Component.text(
                                    "このアイテムは /ars give では取得できません。"
                                        + "魔導書・素材等は /tf give <id> を使ってください。",
                                    NamedTextColor.RED));
                                return 0;
                            }
                            return GiveCommands.executeGive(plugin, player, itemId, 1);
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
                                if (!ArsGiveAllowlist.isAllowed(itemId)) {
                                    ctx.getSource().getSender().sendMessage(Component.text(
                                        "このアイテムは /ars give では取得できません。"
                                            + "魔導書・素材等は /tf give <id> を使ってください。",
                                        NamedTextColor.RED));
                                    return 0;
                                }
                                return GiveCommands.executeGive(plugin, player, itemId, count);
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
                                    if (!ArsGiveAllowlist.isAllowed(itemId)) {
                                        ctx.getSource().getSender().sendMessage(Component.text(
                                            "このアイテムは /ars give では取得できません。"
                                                + "魔導書・素材等は /tf give <id> を使ってください。",
                                            NamedTextColor.RED));
                                        return 0;
                                    }
                                    return GiveCommands.executeGive(plugin, target, itemId, count);
                                })
                            )
                        )
                    )
                )
                .then(Commands.literal("mana")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        return StatusCommands.executeManaInfo(plugin, player);
                    })
                    .then(Commands.literal("notify")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return StatusCommands.executeManaNotifyToggle(player);
                        })
                    )
                )
                .then(Commands.literal("cleanup")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        return AdminCommands.executeCleanup(player);
                    })
                )
                .then(Commands.literal("debug")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        return StatusCommands.executeDebug(plugin, player);
                    })
                    .then(Commands.literal("on")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return StatusCommands.executeDebug(plugin, player, true);
                        }))
                    .then(Commands.literal("off")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return StatusCommands.executeDebug(plugin, player, false);
                        }))
                )
                .then(Commands.literal("fixmana")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .executes(ctx -> {
                        // 自分のマナを修正
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        return StatusCommands.executeFixMana(plugin, ctx.getSource().getSender(), player);
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
                            return StatusCommands.executeFixMana(plugin, ctx.getSource().getSender(), target);
                        })
                    )
                )
                .then(Commands.literal("reload")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .executes(ctx -> {
                        return AdminCommands.executeReload(plugin, ctx.getSource().getSender(), false);
                    })
                    .then(Commands.literal("reset")
                        .executes(ctx -> {
                            return AdminCommands.executeReload(plugin, ctx.getSource().getSender(), true);
                        })
                    )
                )
                .then(Commands.literal("backpack")
                    .executes(ctx -> {
                        return StatusCommands.executeBackpack(ctx.getSource().getSender());
                    })
                )
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        return StatusCommands.executeStatus(plugin, player);
                    })
                )
                // 手持ち装備(武器・触媒・ツール)のスレッド装着GUI。着用防具はスニーク+右クリックの
                // 既存トリガー(ThreadGuiOpenListener)のままで、こちらはその衝突回避用の入口。
                .then(Commands.literal("thread")
                    .executes(ctx -> ThreadCommands.executeThread(plugin, ctx.getSource().getSender()))
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
                            return AdminCommands.executePvpToggle(plugin, ctx.getSource().getSender(), state);
                        })
                    )
                )
                .then(Commands.literal("glyph")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .then(Commands.literal("unlockall")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return GlyphCommands.executeGlyphUnlockAll(plugin, player);
                        })
                    )
                    .then(Commands.literal("lockall")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return GlyphCommands.executeGlyphLockAll(player);
                        })
                    )
                )
                .then(Commands.literal("ranking")
                    .then(Commands.literal("glyphs")
                        .executes(ctx -> RankingCommands.executeRankingGlyphs(plugin, ctx.getSource().getSender()))
                    )
                    .then(Commands.literal("mana")
                        .executes(ctx -> RankingCommands.executeRankingMana(plugin, ctx.getSource().getSender()))
                    )
                )
                .then(Commands.literal("world")
                    .requires(src -> src.getSender().hasPermission("arspaper.admin"))
                    .then(Commands.literal("ban")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return WorldCommands.executeWorldBan(plugin, player);
                        })
                    )
                    .then(Commands.literal("maxmana")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-1000, 10000))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                                int value = IntegerArgumentType.getInteger(ctx, "value");
                                return WorldCommands.executeWorldManaSetting(plugin, player, "maxmana", value);
                            })
                        )
                    )
                    .then(Commands.literal("maxrgmana")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-1000, 10000))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                                int value = IntegerArgumentType.getInteger(ctx, "value");
                                return WorldCommands.executeWorldManaSetting(plugin, player, "maxrgmana", value);
                            })
                        )
                    )
                    .then(Commands.literal("fixmana")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-1, 100000))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                                int value = IntegerArgumentType.getInteger(ctx, "value");
                                return WorldCommands.executeWorldManaSetting(plugin, player, "fixmana", value);
                            })
                        )
                    )
                    .then(Commands.literal("fixrgmana")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-1, 10000))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                                int value = IntegerArgumentType.getInteger(ctx, "value");
                                return WorldCommands.executeWorldManaSetting(plugin, player, "fixrgmana", value);
                            })
                        )
                    )
                    .then(Commands.literal("info")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return WorldCommands.executeWorldInfo(plugin, player);
                        })
                    )
                )
                .then(Commands.literal("spell")
                    .then(Commands.literal("list")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return SpellCommands.executeSpellList(plugin, player);
                        })
                    )
                    .then(Commands.literal("set")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 10))
                            .then(Commands.argument("spellDef", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                                    int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                    String spellDef = StringArgumentType.getString(ctx, "spellDef");
                                    return SpellCommands.executeSpellSet(plugin, player, slot, spellDef);
                                })
                            )
                        )
                    )
                    .then(Commands.literal("bind")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 10))
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                                int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                return SpellCommands.executeSpellBind(plugin, player, slot);
                            })
                        )
                    )
                    .then(Commands.literal("unbind")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                            return SpellCommands.executeSpellUnbind(player);
                        })
                    )
                )
                .build(),
            "ArsPaper main command",
            List.of("arspaper")
        );
    }
}
