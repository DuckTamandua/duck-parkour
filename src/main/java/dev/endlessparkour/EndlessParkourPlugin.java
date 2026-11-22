package dev.endlessparkour;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SpawnUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.endlessparkour.course.Theme;
import dev.endlessparkour.course.Themes;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * «Duck» Parkour (plugin id EndlessParkour): an endless, newly generated jump course in the sky
 * with a shared top list.
 */
public final class EndlessParkourPlugin extends JavaPlugin {

    private ParkourConfig config;
    private Leaderboard board;
    private RunManager runs;
    private ScheduledFuture<?> ticker;
    private final AtomicBoolean saveQueued = new AtomicBoolean();

    public EndlessParkourPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        config = ParkourConfig.load(getDataDirectory(), getLogger());
        board = new Leaderboard(getDataDirectory().resolve("leaderboard.json"), getLogger());
        board.load();
        getCommandRegistry().registerCommand(new ParkourCommand(this));
        Protection.register(this, () -> runs == null ? null : runs.world());
        getEntityStoreRegistry().registerSystem(new FlightTicker(() -> runs == null ? null : runs.world(),
                () -> runs == null ? null : runs.mover(), getLogger()));
        getEventRegistry().register(PlayerDisconnectEvent.class, e -> {
            if (runs != null) {
                runs.onDisconnect(e.getPlayerRef().getUuid());
            }
        });
        // Parkour-only server: players join straight into the parkour world, on their own start island ...
        getEventRegistry().<PlayerConnectEvent>registerAsync(PlayerConnectEvent.class, future -> future.thenCompose(e -> {
            World w = runs == null ? null : runs.world();
            if (!config.parkourOnly || w == null) {
                return CompletableFuture.completedFuture(e);
            }
            e.setWorld(w);
            PlayerRef pr = e.getPlayerRef();
            return runs.prepareJoin(pr.getUuid(), pr.getUsername()).handle((start, err) -> {
                if (err != null) {
                    getLogger().at(Level.WARNING).withCause(err).log("Could not prepare the start of %s", pr.getUsername());
                } else if (start != null) {
                    Holder<EntityStore> holder = e.getHolder();
                    if (holder.getComponent(TransformComponent.getComponentType()) != null) {
                        SpawnUtil.applyTransform(holder, start);
                    } else {
                        SpawnUtil.applyFirstSpawnTransform(holder, start);
                    }
                }
                return e;
            });
        }));
        // ... and anyone who still ends up in another world is brought over.
        getEventRegistry().<String, PlayerReadyEvent>registerGlobal(PlayerReadyEvent.class, e -> {
            if (config.parkourOnly && runs != null) {
                runs.onReady(e.getPlayerRef(), e.getPlayer());
            }
        });
    }

    @Override
    protected void start() {
        List<Theme> themes = checkedThemes();
        runs = new RunManager(config, board, getLogger(), themes, this::saveSoon);
        Universe.get().getUniverseReady().thenRun(() ->
                ParkourWorld.ensure(config, getLogger()).whenComplete((world, err) -> {
                    if (err != null || world == null) {
                        getLogger().at(Level.SEVERE).withCause(err).log("Could not create the parkour world");
                        return;
                    }
                    runs.attach(world);
                    getLogger().at(Level.INFO).log("Parkour ready in world %s (%d areas, %d players on the top list)",
                            world.getName(), themes.size(), board.size());
                }));
        ticker = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                runs.scheduleTick();
            } catch (Throwable t) {
                getLogger().at(Level.SEVERE).withCause(t).log("Parkour scheduler failed");
            }
        }, 1000, config.tickMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void shutdown() {
        if (ticker != null) {
            ticker.cancel(false);
        }
        if (runs != null) {
            runs.shutdown();
        }
        if (board != null) {
            board.save();
        }
    }

    /** Drops blocks this server version does not know, and says which. */
    private List<Theme> checkedThemes() {
        TreeSet<String> missing = new TreeSet<>();
        List<Theme> out = new ArrayList<>();
        // Special jumps the server leaves out: all of them when switched off, dash gaps without the sword.
        java.util.EnumSet<Theme.Feature> off = java.util.EnumSet.noneOf(Theme.Feature.class);
        if (!config.specialJumps) {
            off = java.util.EnumSet.allOf(Theme.Feature.class);
        } else if (!config.dashSword || !DashSword.available()) {
            off.add(Theme.Feature.DASH);
            if (config.dashSword) {
                getLogger().at(Level.WARNING).log("The dash sword item %s is missing: no sword and no dash gaps",
                        DashSword.ITEM);
            }
        }
        for (Theme t : Themes.ALL) {
            for (String n : t.blockNames()) {
                if (!BlockPlacer.exists(n)) {
                    missing.add(n);
                }
            }
            out.add(t.filter(BlockPlacer::exists).without(off));
        }
        if (!missing.isEmpty()) {
            getLogger().at(Level.WARNING).log("Unknown blocks left out of the biomes: %s", missing);
        }
        return out;
    }

    /** Saves the top list off the world thread, at most one save at a time. */
    void saveSoon() {
        if (saveQueued.compareAndSet(false, true)) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                saveQueued.set(false);
                board.save();
            }, 2, TimeUnit.SECONDS);
        }
    }

    ParkourConfig config() {
        return config;
    }

    Leaderboard board() {
        return board;
    }

    RunManager runs() {
        return runs;
    }
}
