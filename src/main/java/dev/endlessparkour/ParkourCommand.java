package dev.endlessparkour;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /parkour (alias /pk) and its sub commands. */
final class ParkourCommand extends AbstractPlayerCommand {

    private final EndlessParkourPlugin plugin;

    ParkourCommand(EndlessParkourPlugin plugin) {
        super("parkour", "Start a parkour run, or start over");
        this.plugin = plugin;
        addAliases("pk");
        requireNoPermission();
        addSubCommand(new Leave(plugin));
        addSubCommand(new Top(plugin));
        addSubCommand(new Stats(plugin));
        addSubCommand(new Hud(plugin));
        addSubCommand(new Plants(plugin));
        addSubCommand(new SelfTestCommand(plugin));
        addSubCommand(new Scan(plugin));
    }

    /** /parkour scan: counts blocks left over from old courses (admins). */
    static final class Scan extends CommandBase {
        private final EndlessParkourPlugin plugin;

        Scan(EndlessParkourPlugin plugin) {
            super("scan", "Count blocks left over from old courses");
            this.plugin = plugin;
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            WorldScan.run(plugin.runs(), ctx::sendMessage);
        }
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef player, World world) {
        plugin.runs().join(player, world, ref, store);
    }

    /** /parkour leave */
    static final class Leave extends AbstractPlayerCommand {
        private final EndlessParkourPlugin plugin;

        Leave(EndlessParkourPlugin plugin) {
            super("leave", "Leave the parkour");
            this.plugin = plugin;
            addAliases("quit");
            requireNoPermission();
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world) {
            plugin.runs().leave(player, world, ref, store);
        }
    }

    /** /parkour top */
    static final class Top extends CommandBase {
        private final EndlessParkourPlugin plugin;

        Top(EndlessParkourPlugin plugin) {
            super("top", "Show the best parkour players");
            this.plugin = plugin;
            requireNoPermission();
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            Leaderboard board = plugin.board();
            List<Leaderboard.Line> lines = board.top(plugin.config().leaderboardSize);
            if (lines.isEmpty()) {
                ctx.sendMessage(Msg.line().text("No scores yet. Be the first: ").value("/parkour").build());
                return;
            }
            ctx.sendMessage(Msg.line().text("Top " + lines.size() + " (platforms)").build());
            boolean listed = false;
            UUID self = ctx.isPlayer() ? ctx.sender().getUuid() : null;
            for (Leaderboard.Line l : lines) {
                boolean me = l.id().equals(self);
                listed |= me;
                ctx.sendMessage(Msg.plain().gray(" " + l.rank() + ". ")
                        .colored(l.name(), me ? Msg.GREEN : Msg.WHITE)
                        .colored(" » ", Msg.DARK).value(l.best()).build());
            }
            if (self != null && !listed) {
                int rank = board.rankOf(self);
                if (rank > 0) {
                    ctx.sendMessage(Msg.plain().gray(" " + rank + ". ").colored(ctx.sender().getUsername(), Msg.GREEN)
                            .colored(" » ", Msg.DARK).value(board.best(self)).build());
                }
            }
        }
    }

    /** /parkour stats [player] */
    static final class Stats extends CommandBase {
        private final EndlessParkourPlugin plugin;
        private final OptionalArg<String> playerArg;

        Stats(EndlessParkourPlugin plugin) {
            super("stats", "Show parkour stats of a player");
            this.plugin = plugin;
            this.playerArg = withOptionalArg("player", "Player name", ArgTypes.STRING);
            requireNoPermission();
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            Leaderboard board = plugin.board();
            UUID id;
            Leaderboard.Entry e;
            if (ctx.provided(playerArg)) {
                Map.Entry<UUID, Leaderboard.Entry> found = board.find(ctx.get(playerArg));
                if (found == null) {
                    ctx.sendMessage(Msg.line().bad("No parkour stats for ").value(ctx.get(playerArg)).build());
                    return;
                }
                id = found.getKey();
                e = found.getValue();
            } else if (ctx.isPlayer()) {
                id = ctx.sender().getUuid();
                e = board.get(id);
                if (e == null) {
                    ctx.sendMessage(Msg.line().text("No runs yet. Start one with ").value("/parkour").build());
                    return;
                }
            } else {
                ctx.sendMessage(Msg.error("Name a player: /parkour stats --player=<name>"));
                return;
            }
            int rank = board.rankOf(id);
            ctx.sendMessage(Msg.line().value(e.name).gray(" » ").text("best ").value(e.best)
                    .text(", rank ").value(rank > 0 ? "#" + rank : "-")
                    .text(", runs ").value(e.runs)
                    .text(", platforms ").value(e.platforms).build());
        }
    }

    /** /parkour plants grow|fly|land: how plants travel with your own flying islands. */
    static final class Plants extends AbstractPlayerCommand {
        private final EndlessParkourPlugin plugin;
        private final RequiredArg<String> styleArg;

        Plants(EndlessParkourPlugin plugin) {
            super("plants", "How plants come with your flying islands: grow, fly or land");
            this.plugin = plugin;
            requireNoPermission();
            this.styleArg = withRequiredArg("style", "grow, fly or land", ArgTypes.STRING);
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world) {
            plugin.runs().setPlantStyle(player, ctx.get(styleArg));
        }
    }

    /** /parkour hud */
    static final class Hud extends AbstractPlayerCommand {
        private final EndlessParkourPlugin plugin;

        Hud(EndlessParkourPlugin plugin) {
            super("hud", "Turn the parkour side panel on or off");
            this.plugin = plugin;
            requireNoPermission();
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world) {
            plugin.runs().toggleHud(player, world, ref);
        }
    }

    /**
     * /parkour selftest [count] [seed] [fly]: builds a course in a spare lane and checks every block;
     * with fly the islands fly in and away (admins).
     */
    static final class SelfTestCommand extends CommandBase {
        private final EndlessParkourPlugin plugin;
        private final DefaultArg<Integer> countArg;
        private final OptionalArg<Integer> seedArg;
        private final DefaultArg<Boolean> flyArg;

        SelfTestCommand(EndlessParkourPlugin plugin) {
            super("selftest", "Build a test course and check every block");
            this.plugin = plugin;
            this.countArg = withDefaultArg("count", "Platforms to build", ArgTypes.INTEGER, 80, "80");
            this.seedArg = withOptionalArg("seed", "Course seed", ArgTypes.INTEGER);
            this.flyArg = withDefaultArg("fly", "Let the islands fly in and away", ArgTypes.BOOLEAN, false, "false");
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            int count = Math.max(2, Math.min(2000, ctx.get(countArg)));
            long seed = ctx.provided(seedArg) ? ctx.get(seedArg) : 12345L;
            SelfTest.run(plugin.runs(), plugin.config(), count, seed, ctx.get(flyArg), ctx::sendMessage);
        }
    }
}
