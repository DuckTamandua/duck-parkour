package dev.endlessparkour;

import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.StatChangeDirection;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.SpawnUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import dev.endlessparkour.course.CourseGenerator;
import dev.endlessparkour.course.Placement;
import dev.endlessparkour.course.Platform;
import dev.endlessparkour.course.Theme;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Starts, follows and ends runs. Everything that touches a run happens on the parkour world thread. */
final class RunManager {

    private static final long SPAWN_RETRY_MS = 4000;
    private static final long WORLD_CHANGE_MS = 20000;
    private static final int SPAWN_MAX_TRIES = 4;
    private static final long LEAVE_GRACE_MS = 10000;

    /** Where a player came from, to send them back on /parkour leave. */
    record ReturnPoint(String world, double x, double y, double z, float pitch, float yaw, GameMode gameMode) {
    }

    private final ParkourConfig cfg;
    private final Leaderboard board;
    private final HytaleLogger log;
    private final List<Theme> themes;
    private final Runnable saveBoard;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    private final Map<UUID, ReturnPoint> returns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> leaving = new ConcurrentHashMap<>();
    private final Set<UUID> hudOff = ConcurrentHashMap.newKeySet();
    private final Set<UUID> welcomed = ConcurrentHashMap.newKeySet();
    private final BitSet lanes = new BitSet();
    private final AtomicBoolean tickQueued = new AtomicBoolean();
    private final Map<String, Integer> knownSounds = new ConcurrentHashMap<>();
    private final Map<String, Integer> weatherIds = new ConcurrentHashMap<>();
    /** How plants fly with each player's islands (set with /parkour plants). */
    private final Map<UUID, IslandMover.PlantStyle> plantStyles = new ConcurrentHashMap<>();
    private final HudComponent[] hiddenHud;
    private volatile World world;
    private volatile BlockPlacer placer;
    private volatile IslandMover mover;
    private volatile EntityBlocks objects;

    RunManager(ParkourConfig cfg, Leaderboard board, HytaleLogger log, List<Theme> themes, Runnable saveBoard) {
        this.cfg = cfg;
        this.board = board;
        this.log = log;
        this.themes = List.copyOf(themes);
        this.saveBoard = saveBoard;
        for (String s : new String[] {cfg.soundPoint, cfg.soundRecord, cfg.soundFail}) {
            checkSound(s);
        }
        for (Theme t : themes) {
            checkSound(t.sound());
        }
        EnumSet<HudComponent> hide = EnumSet.noneOf(HudComponent.class);
        for (String name : cfg.hideHud) {
            try {
                hide.add(HudComponent.valueOf(name));
            } catch (IllegalArgumentException e) {
                log.at(Level.WARNING).log("Unknown HUD part %s in HideHud", name);
            }
        }
        // Titles must stay visible: they show the biome and the score.
        hide.remove(HudComponent.EventTitle);
        this.hiddenHud = hide.toArray(new HudComponent[0]);
    }

    void attach(World w) {
        BlockPlacer bp = new BlockPlacer(w, log);
        this.placer = bp;
        this.objects = new EntityBlocks(w);
        this.mover = new IslandMover(bp, objects, w::execute, bp::place, System::currentTimeMillis,
                note -> log.at(Level.INFO).log("%s", note));
        this.world = w;
    }

    World world() {
        return world;
    }

    BlockPlacer placer() {
        return placer;
    }

    IslandMover mover() {
        return mover;
    }

    EntityBlocks objects() {
        return objects;
    }

    /** The run of a player, or null (world thread). */
    Run runOf(UUID id) {
        return runs.get(id);
    }

    /** Some run whose player is in the course, or null (world thread). */
    Run anyRun() {
        for (Run r : runs.values()) {
            if (!r.awaitingSpawn) {
                return r;
            }
        }
        return null;
    }

    List<Theme> themes() {
        return themes;
    }

    int activeRuns() {
        return runs.size();
    }

    /** Positions of all blocks of the running courses, flying islands where they are now (world thread). */
    Set<Long> livePositions() {
        Set<Long> out = new java.util.HashSet<>();
        for (Run run : runs.values()) {
            for (Platform pl : run.platforms()) {
                if (mover != null && mover.isFlying(pl)) {
                    continue;
                }
                for (Placement p : pl.blocks()) {
                    out.add(pack(p.x(), p.y(), p.z()));
                }
            }
        }
        if (mover != null) {
            mover.positions(out);
        }
        return out;
    }

    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    // ------------------------------------------------------------------ ticking

    /** Called by the scheduler; runs one check on the world thread unless one is still pending. */
    void scheduleTick() {
        World w = world;
        if (w == null || !w.isAlive()) {
            return;
        }
        if (!tickQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            w.execute(() -> {
                try {
                    tick();
                } catch (Throwable t) {
                    log.at(Level.SEVERE).withCause(t).log("Parkour tick failed");
                } finally {
                    tickQueued.set(false);
                }
            });
        } catch (RuntimeException e) {
            tickQueued.set(false);
        }
    }

    private void tick() {
        World w = world;
        if (w == null) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID worldId = w.getWorldConfig().getUuid();

        // Players in the parkour world without a run (after /tp or a reconnect) get one.
        for (PlayerRef pr : w.getPlayerRefs()) {
            UUID id = pr.getUuid();
            if (runs.containsKey(id)) {
                continue;
            }
            Long since = leaving.get(id);
            if (since != null && now - since < LEAVE_GRACE_MS) {
                continue;
            }
            leaving.remove(id);
            startRun(id, pr.getUsername(), null);
        }

        int boardVersion = board.version();
        for (Run run : new ArrayList<>(runs.values())) {
            PlayerRef pr = Universe.get().getPlayer(run.playerId);
            if (pr == null) {
                // A player who is still connecting is not in the universe yet.
                if (!(run.joining && now - run.teleportAt < WORLD_CHANGE_MS)) {
                    stop(run, now, true);
                }
                continue;
            }
            boolean here = worldId.equals(pr.getWorldUuid());
            Ref<EntityStore> ref = here ? pr.getReference() : null;
            if (here && (ref == null || !ref.isValid())) {
                continue;
            }
            if (run.awaitingSpawn) {
                if (here) {
                    Vector3d pos = position(ref);
                    Platform s = run.first();
                    if (pos != null && s.isNear(pos.x, pos.z, 1.5) && pos.y > s.topY() && pos.y < s.topY() + 3) {
                        run.awaitingSpawn = false;
                        run.teleportTries = 0;
                        run.joining = false;
                        arrived(run, pr, ref);
                        continue;
                    }
                }
                // A move between worlds shows a loading screen, so give it more time.
                long wait = here ? SPAWN_RETRY_MS : WORLD_CHANGE_MS;
                if (now - run.teleportAt > wait) {
                    if (run.teleportTries >= SPAWN_MAX_TRIES) {
                        pr.sendMessage(Msg.error("Could not bring you to the start. Try /parkour again."));
                        stop(run, now, false);
                    } else {
                        teleportToStart(run);
                    }
                }
                continue;
            }
            if (!here) {
                // Left through a portal, /tp or similar.
                stop(run, now, true);
                continue;
            }
            Vector3d pos = position(ref);
            if (pos == null) {
                continue;
            }
            if (cfg.unlimitedStamina) {
                fillStamina(ref);
            }
            progress(run, pr, pos, now);
            if (fell(run, pos)) {
                fail(run, pr, now);
                continue;
            }
            if (run.hud != null && run.hudBoardVersion != boardVersion) {
                run.hudBoardVersion = boardVersion;
                refreshHud(run);
            }
        }
    }

    private static Vector3d position(Ref<EntityStore> ref) {
        TransformComponent t = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
        return t == null ? null : t.getPosition();
    }

    // ------------------------------------------------------------------ joining and leaving

    /** /parkour: starts or restarts a run. Runs on the thread of the player's current world. */
    void join(PlayerRef pr, World from, Ref<EntityStore> ref, Store<EntityStore> store) {
        World w = world;
        if (w == null) {
            pr.sendMessage(Msg.error("The parkour is still loading. Try again in a moment."));
            return;
        }
        UUID id = pr.getUuid();
        leaving.remove(id);
        if (from != w) {
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            Player player = store.getComponent(ref, Player.getComponentType());
            GameMode mode = player != null ? player.getGameMode() : null;
            if (t != null) {
                Vector3d p = t.getPosition();
                Rotation3f r = t.getRotation();
                returns.put(id, new ReturnPoint(from.getName(), p.x, p.y, p.z, r.pitch(), r.yaw(), mode));
            }
            if (cfg.forceAdventure && mode != null && mode != GameMode.Adventure) {
                Player.setGameMode(ref, GameMode.Adventure, store);
            }
        }
        String name = pr.getUsername();
        w.execute(() -> {
            Run old = runs.remove(id);
            startRun(id, name, old);
        });
    }

    /** /parkour leave. Runs on the thread of the player's current world. */
    void leave(PlayerRef pr, World current, Ref<EntityStore> ref, Store<EntityStore> store) {
        World w = world;
        UUID id = pr.getUuid();
        long now = System.currentTimeMillis();
        boolean inParkour = w != null && current == w;
        Run run = runs.get(id);
        if (cfg.parkourOnly) {
            pr.sendMessage(Msg.line().text("This server is all parkour. ").value("/parkour")
                    .text(" starts over.").build());
            return;
        }
        if (run == null && !inParkour) {
            pr.sendMessage(Msg.error("You are not in the parkour."));
            return;
        }
        leaving.put(id, now);
        if (inParkour) {
            if (run != null) {
                stop(run, now, true);
            }
        } else if (w != null) {
            w.execute(() -> {
                Run r = runs.get(id);
                if (r != null) {
                    stop(r, System.currentTimeMillis(), false);
                }
            });
        }
        ReturnPoint rp = returns.remove(id);
        if (rp != null && rp.gameMode() != null && cfg.forceAdventure) {
            Player.setGameMode(ref, rp.gameMode(), store);
        }
        if (!inParkour) {
            pr.sendMessage(Msg.info("You left the parkour."));
            return;
        }
        World target = rp != null ? Universe.get().getWorld(rp.world()) : null;
        if (target == null || target == w) {
            target = Universe.get().getDefaultWorld();
        }
        if (target == null || target == w) {
            pr.sendMessage(Msg.error("There is no other world to go back to."));
            return;
        }
        if (rp != null && target.getName().equals(rp.world())) {
            Transform dest = new Transform(rp.x(), rp.y(), rp.z(), rp.pitch(), rp.yaw(), 0f);
            store.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(target, dest));
        } else {
            World to = target;
            SpawnUtil.resolveSpawnPoint(to, id).thenAccept(tr -> w.execute(() -> {
                Ref<EntityStore> r = pr.getReference();
                if (r != null && r.isValid() && tr != null) {
                    r.getStore().putComponent(r, Teleport.getComponentType(), Teleport.createForPlayer(to, tr));
                }
            }));
        }
        pr.sendMessage(Msg.info("You left the parkour."));
    }

    /** A player is ready in some world; on a parkour-only server, bring them into the parkour. */
    void onReady(Ref<EntityStore> ref, Player player) {
        World w = world;
        if (w == null || ref == null || !ref.isValid() || player == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World current = store.getExternalData().getWorld();
        if (current == null || current == w) {
            return;
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        current.execute(() -> {
            if (ref.isValid() && !runs.containsKey(pr.getUuid())) {
                join(pr, current, ref, store);
            }
        });
    }

    /** Called when a player disconnects (any thread). */
    void onDisconnect(UUID id) {
        welcomed.remove(id);
        World w = world;
        if (w == null) {
            return;
        }
        w.execute(() -> {
            Run r = runs.get(id);
            if (r != null) {
                r.hud = null;
                stop(r, System.currentTimeMillis(), true);
            }
            leaving.remove(id);
        });
    }

    /** /parkour hud. Runs on the thread of the player's current world. */
    void toggleHud(PlayerRef pr, World current, Ref<EntityStore> ref) {
        UUID id = pr.getUuid();
        boolean off = hudOff.add(id);
        if (!off) {
            hudOff.remove(id);
        }
        Run run = runs.get(id);
        if (run != null && current == world) {
            if (off) {
                removeHudHere(run, pr, ref);
            } else {
                showHud(run, pr, ref);
            }
        }
        pr.sendMessage(Msg.line().text("Side panel ").value(off ? "off" : "on").build());
    }

    /** Ends all runs (plugin shutdown). */
    void shutdown() {
        World w = world;
        long now = System.currentTimeMillis();
        for (Run run : new ArrayList<>(runs.values())) {
            if (!run.awaitingSpawn && run.score > 0) {
                board.record(run.playerId, run.playerName, run.score, now);
            }
        }
        runs.clear();
        world = null;
        if (w != null) {
            log.at(Level.INFO).log("Parkour stopped");
        }
    }

    // ------------------------------------------------------------------ the run itself

    private int laneZ(int lane) {
        return lane * cfg.laneSpacing;
    }

    /**
     * Parkour-only server: builds the start island of a connecting player before they enter the
     * world, so they appear right on it (their old position would be an unloaded chunk somewhere
     * in the void, which made the client wait about 30 seconds). Completes with the start point,
     * or null when the world is not ready. Any thread.
     */
    CompletableFuture<Transform> prepareJoin(UUID id, String name) {
        World w = world;
        if (w == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Transform> result = new CompletableFuture<>();
        w.execute(() -> {
            try {
                Run old = runs.remove(id);
                if (old != null) {
                    lanes.clear(old.lane);
                    mover.cancel(id);
                    placer.clear(old.allBlocks());
                }
                Run run = startRun(id, name, null, false);
                run.joining = true;
                run.built.whenComplete((v, err) -> result.complete(startPoint(run)));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.completeOnTimeout(null, 10, TimeUnit.SECONDS);
    }

    private static Transform startPoint(Run run) {
        Platform s = run.first();
        return new Transform(s.spawnX(), s.topY() + 1.0, s.spawnZ(), 0f, ParkourWorld.YAW_EAST, 0f);
    }

    private Run startRun(UUID id, String name, Run old) {
        return startRun(id, name, old, true);
    }

    private Run startRun(UUID id, String name, Run old, boolean teleport) {
        long now = System.currentTimeMillis();
        int lane;
        if (old != null) {
            lane = old.lane;
            mover.cancel(old.playerId);
            placer.clear(old.allBlocks());
            if (!old.awaitingSpawn && old.score > 0) {
                finish(old, now, false);
            }
        } else {
            lane = lanes.nextClearBit(0);
            lanes.set(lane);
        }
        long seed = ThreadLocalRandom.current().nextLong();
        CourseGenerator gen = new CourseGenerator(seed, themes, startTheme(), cfg.levels, laneZ(lane), cfg.startY,
                cfg.minY, cfg.maxY, cfg.sectionLengthMin, cfg.sectionLengthMax, cfg.laneDrift);
        Run run = new Run(id, name, lane, gen, now);
        if (old != null) {
            run.hud = old.hud;
        }
        Platform start = gen.start(0);
        run.add(start);
        run.theme = start.theme();
        while (run.last().index() < cfg.platformsAhead) {
            run.add(gen.next(run.last()));
        }
        runs.put(id, run);
        run.teleportAt = now;
        // The start island and the first jump are there at once; the others fly in one after another.
        List<Platform> all = run.platformList();
        int instant = cfg.flyIslands ? Math.min(2, all.size()) : all.size();
        run.built = placer.placePlatforms(all.subList(0, instant), cfg.areaColors, cfg.areaMusic).whenComplete((v, err) -> {
            if (err != null) {
                log.at(Level.WARNING).withCause(err).log("Could not build the course of %s", name);
            }
            if (teleport && runs.get(id) == run) {
                teleportToStart(run);
            }
        });
        if (instant < all.size()) {
            // They fly in once the player stands on the start island: before that, the game has not woken
            // up that part of the world yet and could not make the flying objects.
            run.pendingFlights = new ArrayList<>(all.subList(instant, all.size()));
        }
        return run;
    }

    private void teleportToStart(Run run) {
        run.awaitingSpawn = true;
        run.teleportAt = System.currentTimeMillis();
        run.teleportTries++;
        PlayerRef pr = Universe.get().getPlayer(run.playerId);
        World w = world;
        if (pr == null || w == null) {
            return;
        }
        Platform s = run.first();
        Transform dest = new Transform(s.spawnX(), s.topY() + 1.0, s.spawnZ(), 0f, ParkourWorld.YAW_EAST, 0f);
        UUID worldId = pr.getWorldUuid();
        World current = worldId == null ? null : Universe.get().getWorld(worldId);
        if (current == null) {
            return;
        }
        Runnable task = () -> {
            Ref<EntityStore> ref = pr.getReference();
            if (ref != null && ref.isValid()) {
                ref.getStore().putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(w, dest));
            }
        };
        if (current == w) {
            task.run();
        } else {
            current.execute(task);
        }
    }

    private void arrived(Run run, PlayerRef pr, Ref<EntityStore> ref) {
        if (run.pendingFlights != null) {
            // The game wakes up the world around a player a few tenths of a second after they arrived (objects
            // made 100 ms after arriving could not be made): the first objects come 400 ms after it.
            flyIn(run, run.pendingFlights, System.currentTimeMillis() + 400 + IslandMover.HIDE_MS, 150);
            run.pendingFlights = null;
        }
        showHud(run, pr, ref);
        hideGameHud(pr, ref);
        areaWeather(ref, run.theme);
        if (cfg.unlimitedStamina) {
            fillStamina(ref);
        }
        Theme th = run.theme;
        // After a fall the score title stays on screen instead of the biome title.
        if (!run.afterFall) {
            title(pr, Message.raw(th.name()).color(th.color()), zoneLine(th), true);
            play(pr, th.sound(), (float) cfg.soundBiomeVolume);
        }
        if (welcomed.add(run.playerId)) {
            pr.sendMessage(Msg.line().text("Jump from island to island. Fall and you start over. Best: ")
                    .value(board.best(run.playerId)).build());
            // On a parkour-only server the welcome is one line; the command hint is left out.
            if (!cfg.parkourOnly) {
                pr.sendMessage(Msg.line().value("/parkour leave").text(" to quit, ").value("/parkour top")
                        .text(" for the best players.").build());
            }
        }
    }

    private void progress(Run run, PlayerRef pr, Vector3d pos, long now) {
        int reached = run.score;
        for (Platform p : run.platforms()) {
            int i = p.index();
            if (i > reached && i <= run.score + 8 && p.isStandingOn(pos.x, pos.y, pos.z)) {
                reached = i;
            }
        }
        if (reached > run.score) {
            reach(run, pr, reached);
        }
    }

    private void reach(Run run, PlayerRef pr, int index) {
        run.score = index;
        Platform p = run.get(index);
        boolean newBiome = p != null && p.theme() != run.theme;
        if (newBiome) {
            run.theme = p.theme();
            Ref<EntityStore> ref = pr.getReference();
            if (ref != null && ref.isValid()) {
                areaWeather(ref, run.theme);
            }
            title(pr, Message.raw(run.theme.name()).color(run.theme.color()), zoneLine(run.theme), true);
            play(pr, run.theme.sound(), (float) cfg.soundBiomeVolume);
        } else {
            play(pr, cfg.soundPoint, (float) cfg.soundPointVolume);
        }
        List<Platform> add = new ArrayList<>();
        while (run.last().index() < index + cfg.platformsAhead) {
            Platform n = run.generator.next(run.last());
            run.add(n);
            add.add(n);
        }
        if (!add.isEmpty()) {
            if (cfg.flyIslands) {
                flyIn(run, add, System.currentTimeMillis(), 150);
            } else {
                placer.placePlatforms(add, cfg.areaColors, cfg.areaMusic);
            }
        }
        List<Platform> gone = new ArrayList<>();
        while (run.size() > 1 && run.first().index() < index - cfg.platformsBehind) {
            gone.add(run.removeFirst());
        }
        if (!gone.isEmpty()) {
            if (cfg.flyIslands) {
                flyOut(run, gone);
            } else {
                List<Placement> remove = new ArrayList<>();
                for (Platform g : gone) {
                    remove.addAll(g.blocks());
                }
                placer.clear(remove);
            }
        }
        if (run.hud != null) {
            run.hud.setScore(run.score, Math.max(board.best(run.playerId), run.score), board.rankOf(run.playerId));
            if (newBiome) {
                run.hud.setBiome(run.theme.name(), run.theme.color());
            }
            pushHud(run);
        }
    }

    /**
     * Islands fly in one after another (stagger ms apart) from startAt: from ahead when that way is
     * free, else from below, above or the side, so they never pass through other islands.
     */
    private void flyIn(Run run, List<Platform> platforms, long startAt, long stagger) {
        long start = startAt;
        for (Platform p : List.copyOf(platforms)) {
            IslandMover.Plan plan = mover.chooseWay(p, run.platforms(), cfg.flyDistance, start, cfg.flyMillis);
            // A player's game takes the grass colour of an object when it gets the object: colour the whole way
            // first, and let the mover make the objects only once that is done (it often has to load sections).
            mover.flyIn(run.playerId, p, plan.way(), cfg.flyDistance, plan.start(), cfg.flyMillis, plantStyle(run.playerId),
                    cfg.areaColors);
            placer.prepareFlight(List.of(p), plan.way(), cfg.flyDistance, cfg.areaColors, cfg.areaMusic)
                    .whenComplete((v, err) -> {
                        if (err != null) {
                            log.at(Level.WARNING).withCause(err).log("Could not get an island of %s ready", run.playerName);
                        }
                        mover.coloursReady(p);
                    });
            start = plan.start() + stagger;
        }
    }

    /** How plants fly with this player's islands. */
    IslandMover.PlantStyle plantStyle(UUID id) {
        IslandMover.PlantStyle s = plantStyles.get(id);
        if (s != null) {
            return s;
        }
        IslandMover.PlantStyle d = IslandMover.PlantStyle.parse(cfg.plantStyle);
        return d == null ? IslandMover.PlantStyle.GROW : d;
    }

    /** /parkour plants: picks how plants fly with this player's islands (any thread). */
    void setPlantStyle(PlayerRef pr, String name) {
        IslandMover.PlantStyle s = IslandMover.PlantStyle.parse(name);
        if (s == null) {
            pr.sendMessage(Msg.error("Pick grow, fly or land."));
            return;
        }
        plantStyles.put(pr.getUuid(), s);
        String how = switch (s) {
            case GROW -> "they fly along tiny and grow on the way in";
            case FLY -> "they fly along at full size";
            case LAND -> "they appear when an island lands";
        };
        pr.sendMessage(Msg.line().text("Plants: ").value(s.id()).text(" (" + how + "), from the next island on.").build());
    }

    /** Old islands fly away to the back and disappear. */
    private void flyOut(Run run, List<Platform> platforms) {
        List<Platform> list = List.copyOf(platforms);
        // Leaving islands keep their grass colour on the way out too.
        placer.prepareFlight(list, IslandMover.WAY_OUT, cfg.flyDistance, cfg.areaColors, false);
        long start = System.currentTimeMillis();
        for (Platform p : list) {
            mover.flyOut(run.playerId, p, cfg.flyDistance, start, cfg.flyMillis, plantStyle(run.playerId));
            start += 150;
        }
    }

    /** True when the player is clearly below the platforms around them. */
    boolean fell(Run run, Vector3d pos) {
        Platform cur = run.get(run.score);
        if (cur == null) {
            return false;
        }
        int low = cur.topY();
        for (Platform p : run.platforms()) {
            if (p.index() >= run.score - 2 && p.index() <= run.score + 1) {
                low = Math.min(low, p.topY());
            }
        }
        return pos.y < low + 1 - cfg.fallDistance;
    }

    private void fail(Run run, PlayerRef pr, long now) {
        runs.remove(run.playerId);
        Leaderboard.Result r = finish(run, now, true);
        boolean best = r.newBest() && r.score() > 0;
        play(pr, best ? cfg.soundRecord : cfg.soundFail);
        Message title = Message.raw("Score " + r.score()).color(best ? Msg.GREEN : Msg.WHITE);
        Message sub = best ? Message.raw("New best!").color(Msg.GREEN)
                : Message.raw("Best " + Math.max(r.previousBest(), r.score())).color(Msg.GRAY);
        title(pr, title, sub, false);
        // finish() already stored the score, so the old run is passed without it.
        run.score = 0;
        run.awaitingSpawn = true;
        Run next = startRun(run.playerId, run.playerName, run);
        next.afterFall = true;
    }

    /** Stores the score of a run and tells the player how it went. */
    private Leaderboard.Result finish(Run run, long now, boolean fell) {
        Leaderboard.Result r = board.record(run.playerId, run.playerName, run.score, now);
        saveBoard.run();
        PlayerRef pr = Universe.get().getPlayer(run.playerId);
        if (pr != null) {
            Msg m = Msg.line().text(fell ? "You fell! Score: " : "Run over. Score: ").value(r.score());
            if (r.newBest() && r.score() > 0) {
                m.good(" New best!");
            } else {
                m.gray(" (best " + Math.max(r.previousBest(), r.score()) + ")");
            }
            pr.sendMessage(m.build());
        }
        if (r.newRecord() && cfg.announceRecords && r.score() > 0) {
            Universe.get().sendMessage(Msg.line().value(run.playerName).text(" set a new " + Msg.NAME + " record: ")
                    .value(r.score()).text(" platforms!").build());
        }
        return r;
    }

    /** Ends a run without a new one (leave, disconnect, other world). World thread. */
    private void stop(Run run, long now, boolean record) {
        if (runs.get(run.playerId) == run) {
            runs.remove(run.playerId);
        }
        if (record && !run.awaitingSpawn && run.score > 0) {
            finish(run, now, false);
        }
        mover.cancel(run.playerId);
        placer.clear(run.allBlocks());
        lanes.clear(run.lane);
        removeHud(run);
        restorePlayer(run);
    }

    // ------------------------------------------------------------------ side panel

    private void showHud(Run run, PlayerRef pr, Ref<EntityStore> ref) {
        if (!cfg.showHud || hudOff.contains(run.playerId)) {
            return;
        }
        try {
            if (run.hud == null) {
                Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                ParkourHud hud = new ParkourHud(pr, cfg.hudTopSize);
                fillHud(run, hud);
                run.hudBoardVersion = board.version();
                player.getHudManager().addCustomHud(pr, hud);
                run.hud = hud;
            } else {
                refreshHud(run);
            }
        } catch (RuntimeException e) {
            log.at(Level.WARNING).withCause(e).log("Could not show the side panel");
        }
    }

    private void refreshHud(Run run) {
        if (run.hud == null) {
            return;
        }
        fillHud(run, run.hud);
        pushHud(run);
    }

    private void pushHud(Run run) {
        try {
            run.hud.push();
        } catch (RuntimeException e) {
            log.at(Level.WARNING).withCause(e).log("Could not update the side panel");
        }
    }

    private void fillHud(Run run, ParkourHud hud) {
        hud.setBiome(run.theme.name(), run.theme.color());
        hud.setScore(run.score, Math.max(board.best(run.playerId), run.score), board.rankOf(run.playerId));
        hud.setTop(topLines(cfg.hudTopSize));
    }

    List<String> topLines(int n) {
        List<String> out = new ArrayList<>();
        for (Leaderboard.Line l : board.top(n)) {
            out.add(l.rank() + ". " + l.name() + "  " + l.best());
        }
        return out;
    }

    private void removeHudHere(Run run, PlayerRef pr, Ref<EntityStore> ref) {
        try {
            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getHudManager().removeCustomHud(pr, ParkourHud.KEY);
            }
        } catch (RuntimeException e) {
            log.at(Level.WARNING).withCause(e).log("Could not remove the side panel");
        }
        run.hud = null;
    }

    private void removeHud(Run run) {
        if (run.hud == null) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(run.playerId);
        if (pr == null) {
            run.hud = null;
            return;
        }
        UUID worldId = pr.getWorldUuid();
        World current = worldId == null ? null : Universe.get().getWorld(worldId);
        if (current == null) {
            run.hud = null;
            return;
        }
        Runnable task = () -> {
            Ref<EntityStore> ref = pr.getReference();
            if (ref != null && ref.isValid()) {
                removeHudHere(run, pr, ref);
            }
        };
        if (current == world) {
            task.run();
        } else {
            current.execute(task);
        }
    }

    // ------------------------------------------------------------------ stamina and game screen

    /** Keeps stamina full: lowering is blocked and the value is topped up. */
    private void fillStamina(Ref<EntityStore> ref) {
        try {
            EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
            if (stats == null) {
                return;
            }
            int stamina = DefaultEntityStatTypes.getStamina();
            if (!stats.isStatChangeSuppressed(stamina, StatChangeDirection.LOWER)) {
                stats.setStatChangeSuppressed(stamina, StatChangeDirection.LOWER, true);
            }
            EntityStatValue value = stats.get(stamina);
            if (value != null && value.get() < value.getMax()) {
                stats.maximizeStatValue(stamina);
            }
        } catch (RuntimeException e) {
            log.at(Level.FINE).withCause(e).log("Could not fill stamina");
        }
    }

    private void hideGameHud(PlayerRef pr, Ref<EntityStore> ref) {
        if (hiddenHud.length == 0) {
            return;
        }
        try {
            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getHudManager().hideHudComponents(pr, hiddenHud);
            }
        } catch (RuntimeException e) {
            log.at(Level.WARNING).withCause(e).log("Could not hide parts of the game screen");
        }
    }

    /** Gives back stamina use and the normal game screen (player still online, run over). */
    private void restorePlayer(Run run) {
        if (cfg.parkourOnly) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(run.playerId);
        if (pr == null) {
            return;
        }
        UUID worldId = pr.getWorldUuid();
        World current = worldId == null ? null : Universe.get().getWorld(worldId);
        if (current == null) {
            return;
        }
        Runnable task = () -> {
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            try {
                EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                if (stats != null) {
                    stats.setStatChangeSuppressed(DefaultEntityStatTypes.getStamina(), StatChangeDirection.LOWER, false);
                }
                WeatherTracker tracker = ref.getStore().getComponent(ref, WeatherTracker.getComponentType());
                if (tracker != null) {
                    tracker.clearOverrideWeatherIndex();
                }
                Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                if (player != null && hiddenHud.length > 0) {
                    player.getHudManager().showHudComponents(pr, hiddenHud);
                }
            } catch (RuntimeException e) {
                log.at(Level.FINE).withCause(e).log("Could not restore the player");
            }
        };
        if (current == world) {
            task.run();
        } else {
            current.execute(task);
        }
    }

    // ------------------------------------------------------------------ areas

    Theme startTheme() {
        for (Theme t : themes) {
            if (t.id().equalsIgnoreCase(cfg.startArea)) {
                return t;
            }
        }
        return themes.get(0);
    }

    private static Message zoneLine(Theme th) {
        return th.zone() == null || th.zone().isBlank() ? Message.empty() : Message.raw(th.zone()).color(Msg.GRAY);
    }

    /** Shows the area's vanilla weather to this player (sky, light, particles). */
    private void areaWeather(Ref<EntityStore> ref, Theme th) {
        if (!cfg.areaWeather) {
            return;
        }
        try {
            WeatherTracker tracker = ref.getStore().getComponent(ref, WeatherTracker.getComponentType());
            if (tracker == null) {
                return;
            }
            int index = weatherIndex(th.weather());
            if (index > 0) {
                tracker.setOverrideWeatherIndex(index);
            } else {
                tracker.clearOverrideWeatherIndex();
            }
        } catch (RuntimeException e) {
            log.at(Level.FINE).withCause(e).log("Could not set the weather");
        }
    }

    private int weatherIndex(String id) {
        if (id == null || id.isBlank()) {
            return 0;
        }
        return weatherIds.computeIfAbsent(id, w -> {
            int index = Weather.getAssetMap().getIndex(w);
            if (index == AssetMapWithIndexes.NOT_FOUND) {
                log.at(Level.WARNING).log("Unknown weather %s", w);
                return 0;
            }
            return index;
        });
    }

    // ------------------------------------------------------------------ titles and sounds

    private void title(PlayerRef pr, Message title, Message sub, boolean major) {
        try {
            EventTitleUtil.showEventTitleToPlayer(pr, title, sub, major ? EventTitleStyle.Major : EventTitleStyle.Default);
        } catch (RuntimeException e) {
            log.at(Level.FINE).withCause(e).log("Could not show a title");
        }
    }

    private void checkSound(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        int index = SoundEvent.getAssetMap().getIndex(id);
        if (index == AssetMapWithIndexes.NOT_FOUND) {
            log.at(Level.WARNING).log("Unknown sound event %s (it will not play)", id);
        } else {
            knownSounds.put(id, index);
        }
    }

    private void play(PlayerRef pr, String sound) {
        play(pr, sound, 1.0f);
    }

    /** Plays a sound to one player; volume is a factor (1 = as the game plays it). */
    private void play(PlayerRef pr, String sound, float volume) {
        Integer index = sound == null ? null : knownSounds.get(sound);
        if (index == null || volume <= 0f) {
            return;
        }
        try {
            SoundUtil.playSoundEvent2dToPlayer(pr, index, SoundCategory.UI, volume, 1.0f);
        } catch (RuntimeException e) {
            log.at(Level.FINE).withCause(e).log("Could not play %s", sound);
        }
    }
}
