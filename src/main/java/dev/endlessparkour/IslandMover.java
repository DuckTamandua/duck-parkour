package dev.endlessparkour;

import dev.endlessparkour.course.Placement;
import dev.endlessparkour.course.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Lets islands glide in and away smoothly. A flying island is a set of block objects
 * ({@link FlyingBlocks}) moved every world tick: arriving ones start tiny and grow while they slow
 * down, then turn into real blocks; leaving ones turn into objects, speed up and shrink until they
 * are gone. When an island lands, its objects stay a moment over the new blocks, so the game has
 * time to draw the blocks and nothing flickers; when one takes off, the objects appear inside the
 * blocks first. Objects are made and removed a batch per tick, so big islands never stall a tick.
 * New islands come from ahead (+X) when that way is free, else from below, above or the side, so
 * islands never fly through each other. How plants, leaves and hanging rocks travel is up to
 * {@link PlantStyle} (the game draws moving objects plainer than blocks, plants most of all).
 * <p>
 * {@link #tick} runs inside the world tick; everything that adds or removes objects or blocks runs
 * as a world task ({@code later}). All methods run on the world thread.
 */
final class IslandMover {

    /** Ways in, best first: from ahead, from below, from above, from the sides. */
    static final int[][] WAYS_IN = {{1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}};
    static final int[] WAY_OUT = {-1, 0, 0};
    /** Size of an object when it appears or just before it is gone. */
    static final float MIN_SCALE = 0.05f;
    /** Size of a flying island: exactly a block, so its blocks join without seams. */
    static final float FULL_SCALE = 1.0f;
    /** Size of objects hidden inside their blocks (no flicker between two faces in one place). */
    static final float INSIDE_SCALE = 0.98f;
    /** How long objects and blocks are both there when an island lands or takes off. */
    static final long OVERLAP_MS = 250;
    /** Within that time, how long the objects keep full size next to the new blocks (until the game drew them). */
    static final long COVER_MS = 120;
    /** Objects made or removed per flight and tick. */
    static final int BATCH = 64;
    /** An object left behind by accident vanishes by itself this long after its flight should be over. */
    static final long SPARE_LIFETIME_MS = 5000;
    /**
     * Arriving objects are made this long before their flight, far below at {@link #HIDDEN_Y} and tiny,
     * and only come up when the flight starts: whatever the game shows of a brand-new object in its
     * first moments happens out of sight.
     */
    static final long HIDE_MS = 200;
    /** Height where arriving objects are made (the bottom of the world, far below every course). */
    static final double HIDDEN_Y = 1.5;

    /** How plants, leaves and hanging rocks travel with a flying island. */
    enum PlantStyle {
        /** They fly along, tiny at first, and grow out of the island during the second half of the flight. */
        GROW,
        /** They fly along at full size, like the solid blocks. */
        FLY,
        /** They do not fly: they appear the moment the island lands and vanish when it takes off. */
        LAND;

        /** The style for a name ("grow", "fly", "land"), or null. */
        static PlantStyle parse(String name) {
            if (name == null) {
                return null;
            }
            for (PlantStyle s : values()) {
                if (s.name().equalsIgnoreCase(name.trim())) {
                    return s;
                }
            }
            return null;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How an island flies in: its way and when it starts. */
    record Plan(int[] way, long start) {
    }

    /** Later starts tried when no way is clear at first (ms after the wished start). */
    private static final long[] DELAYS = {0, 150, 300, 450, 600, 800, 1000, 1300, 1600, 2000};

    private enum State {
        /** Not started; a leaving island still stands as blocks. */
        WAITING,
        /** Objects are being made, a batch per tick (arriving ones far below, out of sight). */
        SPAWNING,
        /** An arriving island's objects are made and wait out of sight for the flight to start. */
        READY,
        /** A leaving island: objects stand inside the blocks until the blocks go. */
        HOLDING,
        FLYING,
        /** An arriving island has landed: its objects stay a moment over the new blocks. */
        LINGERING,
        /** Objects are being removed, a batch per tick. */
        REMOVING,
        DONE
    }

    private static final class Flight {
        final UUID owner;
        final Platform platform;
        final List<Placement> blocks;
        final boolean in;
        final int[] dir;
        final int distance;
        final long start;
        final long duration;
        final PlantStyle style;
        State state = State.WAITING;
        /** True while a world task for this flight is queued. */
        boolean busy;
        /** Start of the movement (an arriving island: start; a leaving one: when its blocks went). */
        long moveStart;
        /** Until when objects and blocks are both there (HOLDING, LINGERING). */
        long until;
        /** When the blocks came (LINGERING). */
        long landed;
        /** True once the objects switched size within HOLDING or LINGERING. */
        boolean resized;
        /** Solid blocks come first in blocks; the rest is decoration (plants, leaves, hanging rocks). */
        final int solids;
        /** How many blocks fly as objects: all, or only the solid ones. */
        final int objectCount;
        /** One object per flying block (same order as blocks); the first {@code made} were made. */
        final Object[] objects;
        int made;
        /** Objects that exist right now. */
        int alive;

        Flight(UUID owner, Platform platform, boolean in, int[] dir, int distance, long start, long duration,
               PlantStyle style) {
            this.owner = owner;
            this.platform = platform;
            this.blocks = ordered(platform.blocks());
            this.in = in;
            this.dir = dir.clone();
            this.distance = distance;
            this.start = start;
            this.duration = Math.max(1, duration);
            this.style = style;
            this.moveStart = start;
            int n = 0;
            while (n < blocks.size() && !blocks.get(n).decor()) {
                n++;
            }
            this.solids = n;
            this.objectCount = style == PlantStyle.LAND ? n : blocks.size();
            this.objects = new Object[objectCount];
        }
    }

    private final BlockAccess world;
    private final FlyingBlocks objects;
    private final Executor later;
    private final Consumer<List<Placement>> unloaded;
    private final LongSupplier clock;
    private final Consumer<String> notes;
    private final List<Flight> flights = new ArrayList<>();
    /** Flights whose island is not standing as blocks at its place (on its way in, or leaving). */
    private final Map<Platform, Flight> byPlatform = new IdentityHashMap<>();
    private long ticks;
    private long tickNanos;
    private long maxTickNanos;
    private long spawned;
    private long removed;
    private long landedBlocks;
    private long incomplete;
    private long notMade;
    private int live;
    private final long[] ways = new long[WAYS_IN.length + 1];

    /**
     * @param world    the blocks
     * @param objects  the flying block objects
     * @param later    runs a task on the world thread outside the tick (adding and removing things)
     * @param unloaded gets blocks that could not land because their chunk was not loaded
     * @param clock    milliseconds
     * @param notes    gets a line for the log when a flight could not make its objects
     */
    IslandMover(BlockAccess world, FlyingBlocks objects, Executor later, Consumer<List<Placement>> unloaded,
                LongSupplier clock, Consumer<String> notes) {
        this.world = world;
        this.objects = objects;
        this.later = later;
        this.unloaded = unloaded;
        this.clock = clock;
        this.notes = notes;
    }

    // ------------------------------------------------------------------ planning

    /**
     * How an island that should fly from startAt for duration ms comes in: the first way of
     * {@link #WAYS_IN} (from ahead if it can) and the earliest of a few start times at which it
     * never meets another island, neither a standing one nor one flying or landing meanwhile.
     * From ahead at startAt when nothing is clear.
     */
    Plan chooseWay(Platform p, Iterable<Platform> others, int distance, long startAt, long duration) {
        Set<Long> standing = new HashSet<>();
        for (Platform o : others) {
            if (o == p || byPlatform.containsKey(o)) {
                continue;
            }
            for (Placement b : o.blocks()) {
                standing.add(RunManager.pack(b.x(), b.y(), b.z()));
            }
        }
        boolean[] free = new boolean[WAYS_IN.length];
        for (int w = 0; w < WAYS_IN.length; w++) {
            free[w] = passesFree(p, WAYS_IN[w], distance, standing);
        }
        for (long delay : DELAYS) {
            Flight probe = new Flight(null, p, true, WAYS_IN[0], distance, startAt + delay, duration, PlantStyle.FLY);
            for (int w = 0; w < WAYS_IN.length; w++) {
                if (free[w] && meetsNone(probe, WAYS_IN[w])) {
                    ways[w]++;
                    return new Plan(WAYS_IN[w].clone(), startAt + delay);
                }
            }
        }
        ways[WAYS_IN.length]++;
        return new Plan(WAYS_IN[0].clone(), startAt);
    }

    /** True when no block of the island crosses a standing island anywhere on the way. */
    private static boolean passesFree(Platform p, int[] d, int distance, Set<Long> standing) {
        for (Placement b : p.blocks()) {
            for (int s = 1; s <= distance; s++) {
                if (standing.contains(RunManager.pack(b.x() + d[0] * s, b.y() + d[1] * s, b.z() + d[2] * s))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * True when the island, flying along d as probe says, never shares a block space with one of
     * the flying islands at the same time (also after they landed, and before the leaving ones left).
     * Each island counts where it is and where it was a moment before, as objects lie between blocks.
     */
    private boolean meetsNone(Flight probe, int[] d) {
        long from = probe.start;
        long to = probe.start + probe.duration;
        for (Flight f : flights) {
            Set<Long> pairs = new HashSet<>();
            for (long t = from; t <= to + 10; t += 10) {
                long when = Math.min(t, to);
                int mine = target(probe, when);
                int mineBefore = target(probe, Math.max(from, when - 60));
                int theirs = where(f, when);
                int theirsBefore = where(f, when - 60);
                for (int a : new int[] {mine, mineBefore}) {
                    for (int b : new int[] {theirs, theirsBefore}) {
                        if (b >= 0) {
                            pairs.add((long) a << 32 | b);
                        }
                    }
                }
            }
            Map<Integer, Set<Long>> there = new HashMap<>();
            for (long pair : pairs) {
                int mine = (int) (pair >> 32);
                int theirs = (int) pair;
                Set<Long> at = there.computeIfAbsent(theirs, o -> {
                    Set<Long> out = new HashSet<>();
                    for (Placement b : f.blocks) {
                        out.add(RunManager.pack(b.x() + f.dir[0] * o, b.y() + f.dir[1] * o, b.z() + f.dir[2] * o));
                    }
                    return out;
                });
                for (Placement b : probe.blocks) {
                    if (at.contains(RunManager.pack(b.x() + d[0] * mine, b.y() + d[1] * mine, b.z() + d[2] * mine))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Blocks away from the final place at a time (rounded), or -1 when the island is not in the world then. */
    private static int where(Flight f, long t) {
        if (f.in) {
            if (t < f.start) {
                return -1;
            }
            return t >= f.start + f.duration ? 0 : target(f, t);
        }
        if (t < f.moveStart) {
            return 0;
        }
        return t >= f.moveStart + f.duration ? -1 : target(f, t);
    }

    private static int target(Flight f, long now) {
        return (int) Math.round(offset(f, now));
    }

    /** Blocks away from the final place: from distance down to 0 (in, slowing down), from 0 up to distance (out, speeding up). */
    private static double offset(Flight f, long now) {
        double q = progress(f, now);
        return f.in ? f.distance * (1 - q) * (1 - q) : f.distance * q * q;
    }

    /** Size of solid blocks: arriving islands grow during the first 60 % of the flight, leaving ones shrink during the last 60 %. */
    private static float scale(Flight f, long now) {
        double q = progress(f, now);
        double s = f.in ? MIN_SCALE + (FULL_SCALE - MIN_SCALE) * Math.min(1.0, q / 0.6)
                : FULL_SCALE - (FULL_SCALE - MIN_SCALE) * Math.max(0.0, (q - 0.4) / 0.6);
        return (float) s;
    }

    /** Size of plants, leaves and hanging rocks: like the island, or (GROW) growing late and shrinking early. */
    private static float plantScale(Flight f, long now) {
        if (f.style != PlantStyle.GROW) {
            return scale(f, now);
        }
        double q = progress(f, now);
        double s = f.in ? MIN_SCALE + (FULL_SCALE - MIN_SCALE) * smooth((q - 0.5) / 0.45)
                : FULL_SCALE - (FULL_SCALE - MIN_SCALE) * smooth(q / 0.45);
        return (float) s;
    }

    /** 0 to 1 with soft ends. */
    private static double smooth(double x) {
        double t = Math.max(0, Math.min(1, x));
        return t * t * (3 - 2 * t);
    }

    private static double progress(Flight f, long now) {
        return Math.max(0, Math.min(1, (now - f.moveStart) / (double) f.duration));
    }

    // ------------------------------------------------------------------ flights

    /** Lets an island fly in along way: it appears distance blocks away at startAt and lands after duration ms. */
    void flyIn(UUID owner, Platform p, int[] way, int distance, long startAt, long duration, PlantStyle style) {
        if (byPlatform.containsKey(p)) {
            return;
        }
        Flight f = new Flight(owner, p, true, way, distance, startAt, duration, style);
        flights.add(f);
        byPlatform.put(p, f);
    }

    /** Lets a standing island fly away to the back (-X) from startAt; it is gone about duration ms later. */
    void flyOut(UUID owner, Platform p, int distance, long startAt, long duration, PlantStyle style) {
        Flight still = byPlatform.get(p);
        if (still != null) {
            // Still on its way in (a very fast player): just take it away.
            drop(still);
            return;
        }
        // Objects of its landing that may still stand over it go now.
        for (Flight f : new ArrayList<>(flights)) {
            if (f.platform == p) {
                drop(f);
            }
        }
        Flight f = new Flight(owner, p, false, WAY_OUT, distance, startAt, duration, style);
        flights.add(f);
        byPlatform.put(p, f);
    }

    /** True while the island is not standing as blocks at its place (flying in, or leaving). */
    boolean isFlying(Platform p) {
        return byPlatform.containsKey(p);
    }

    /** True while any flight of this owner is going on (objects included). */
    boolean busy(UUID owner) {
        for (Flight f : flights) {
            if (f.owner.equals(owner)) {
                return true;
            }
        }
        return false;
    }

    int active() {
        return flights.size();
    }

    /** Objects in the world right now. */
    int objects() {
        return live;
    }

    /** Takes away every flight of the owner at once (fall, leave): objects go, leaving islands still standing go too. */
    void cancel(UUID owner) {
        for (Flight f : new ArrayList<>(flights)) {
            if (f.owner.equals(owner)) {
                drop(f);
            }
        }
    }

    /** Ends a flight at once: its objects go, and a leaving island that still stands is cleared. */
    private void drop(Flight f) {
        removeObjects(f, Integer.MAX_VALUE, 0);
        if (!f.in && standing(f)) {
            clearBlocks(f);
        }
        finish(f);
    }

    /** A leaving island stands as blocks until it flies. */
    private static boolean standing(Flight f) {
        return f.state == State.WAITING || f.state == State.SPAWNING || f.state == State.HOLDING;
    }

    private void finish(Flight f) {
        f.state = State.DONE;
        flights.remove(f);
        if (byPlatform.get(f.platform) == f) {
            byPlatform.remove(f.platform);
        }
    }

    /** Adds the positions of blocks that belong to flights and stand (leaving islands before take-off). */
    void positions(Set<Long> out) {
        for (Flight f : flights) {
            if (!f.in && standing(f)) {
                for (Placement b : f.blocks) {
                    out.add(RunManager.pack(b.x(), b.y(), b.z()));
                }
            }
        }
    }

    /** Every world tick: moves the objects and asks for the next step of each flight (as world tasks). */
    void tick(long now) {
        if (flights.isEmpty()) {
            return;
        }
        long t0 = System.nanoTime();
        for (Flight f : new ArrayList<>(flights)) {
            if (f.busy) {
                continue;
            }
            switch (f.state) {
                case WAITING -> {
                    if (now >= f.start - (f.in ? HIDE_MS : 0)) {
                        f.state = State.SPAWNING;
                        queue(f, () -> spawnBatch(f));
                    }
                }
                case SPAWNING -> queue(f, () -> spawnBatch(f));
                case READY -> {
                    if (now >= f.start) {
                        // Up from the hiding place to the start of the flight, tiny.
                        f.state = State.FLYING;
                        moveObjects(f, now);
                    }
                }
                case HOLDING -> {
                    if (!f.resized && now >= f.until - COVER_MS) {
                        // Full size a moment before the blocks go, so nothing changes when they do.
                        resize(f, FULL_SCALE);
                    }
                    if (now >= f.until) {
                        queue(f, () -> takeOff(f));
                    }
                }
                case FLYING -> {
                    if (now >= f.moveStart + f.duration) {
                        moveObjects(f, f.moveStart + f.duration);
                        queue(f, () -> arrive(f));
                    } else {
                        moveObjects(f, now);
                    }
                }
                case LINGERING -> {
                    if (!f.resized && now >= f.landed + COVER_MS) {
                        // The blocks are drawn by now: the solid objects slip inside them, out of sight, and
                        // the plant objects go (plants are not solid, they would show twice).
                        resize(f, INSIDE_SCALE);
                        queue(f, () -> removeObjects(f, Integer.MAX_VALUE, f.solids));
                    } else if (now >= f.until) {
                        f.state = State.REMOVING;
                        queue(f, () -> removeBatch(f));
                    }
                }
                case REMOVING -> queue(f, () -> removeBatch(f));
                default -> {
                }
            }
        }
        long took = System.nanoTime() - t0;
        ticks++;
        tickNanos += took;
        maxTickNanos = Math.max(maxTickNanos, took);
    }

    private void queue(Flight f, Runnable task) {
        f.busy = true;
        later.execute(() -> {
            f.busy = false;
            if (f.state != State.DONE) {
                task.run();
            }
        });
    }

    /** World task: the next batch of objects appears (arriving: tiny, far below; leaving: inside the blocks). */
    private void spawnBatch(Flight f) {
        if (f.state != State.SPAWNING) {
            return;
        }
        long lifetime = HIDE_MS + f.duration + OVERLAP_MS + SPARE_LIFETIME_MS;
        int end = Math.min(f.objectCount, f.made + BATCH);
        for (int i = f.made; i < end; i++) {
            Placement b = f.blocks.get(i);
            Object o = f.in
                    ? objects.spawn(b, b.x() + 0.5, HIDDEN_Y, b.z() + 0.5, MIN_SCALE, lifetime)
                    : objects.spawn(b, b.x() + 0.5, b.y() + 0.5, b.z() + 0.5, INSIDE_SCALE, lifetime);
            spawned++;
            if (o == null) {
                if (i == 0) {
                    // The game could not make it (that part of the world is not active right now): no flight
                    // this time, the island simply stands at its place when it would have landed.
                    withoutObjects(f);
                    return;
                }
                continue;
            }
            f.objects[i] = o;
            f.alive++;
            live++;
        }
        f.made = end;
        if (f.made == f.objectCount) {
            if (f.in) {
                f.state = State.READY;
            } else {
                f.state = State.HOLDING;
                f.until = clock.getAsLong() + OVERLAP_MS;
                f.resized = false;
            }
        }
    }

    /** A flight whose objects could not be made: an arriving island just lands on time, a leaving one goes now. */
    private void withoutObjects(Flight f) {
        notMade++;
        Placement first = f.blocks.get(0);
        notes.accept(String.format("Flying objects could not be made at %d %d %d (part of the world not active); "
                + "island %d %s without flight", first.x(), first.y(), first.z(), f.platform.index(),
                f.in ? "lands" : "goes"));
        f.made = f.objectCount;
        if (f.in) {
            f.state = State.FLYING;
        } else {
            clearBlocks(f);
            finish(f);
        }
    }

    /** World task: a leaving island's blocks go (its objects stand in their place already) and it starts to move. */
    private void takeOff(Flight f) {
        if (f.state != State.HOLDING) {
            return;
        }
        clearBlocks(f);
        f.moveStart = clock.getAsLong();
        f.state = State.FLYING;
    }

    /**
     * World task: an arriving island becomes real blocks, all of it in one go (plants too), while its
     * objects still stand over it; a leaving one is done flying.
     */
    private void arrive(Flight f) {
        if (f.state != State.FLYING) {
            return;
        }
        if (f.in) {
            List<Placement> notLoaded = new ArrayList<>();
            for (Placement b : f.blocks) {
                land(b, notLoaded);
            }
            if (!notLoaded.isEmpty()) {
                unloaded.accept(notLoaded);
            }
            byPlatform.remove(f.platform);
            f.state = State.LINGERING;
            f.landed = clock.getAsLong();
            f.until = f.landed + OVERLAP_MS;
            f.resized = false;
        } else {
            f.state = State.REMOVING;
            removeBatch(f);
        }
    }

    /** Places one block of a landing island where nothing else is. */
    private void land(Placement b, List<Placement> notLoaded) {
        int here = world.blockAt(b.x(), b.y(), b.z());
        if (here < 0) {
            notLoaded.add(b);
        } else if (here != 0) {
            // Something else is there already: leave it alone.
            incomplete++;
        } else if (world.place(b, b.x(), b.y(), b.z())) {
            landedBlocks++;
        } else {
            notLoaded.add(b);
        }
    }

    /** World task: the next batch of objects goes; the flight ends with the last one. */
    private void removeBatch(Flight f) {
        if (f.state != State.REMOVING) {
            return;
        }
        removeObjects(f, BATCH, 0);
        if (f.alive == 0) {
            finish(f);
        }
    }

    /** Removes up to max objects, those of blocks from index {@code from} on. */
    private void removeObjects(Flight f, int max, int from) {
        int n = 0;
        for (int i = from; i < f.made && n < max; i++) {
            if (f.objects[i] != null) {
                objects.remove(f.objects[i]);
                f.objects[i] = null;
                f.alive--;
                live--;
                removed++;
                n++;
            }
        }
    }

    /** All objects of a standing island (at their final place) get this size. */
    private void resize(Flight f, float scale) {
        for (int i = 0; i < f.made; i++) {
            if (f.objects[i] == null) {
                continue;
            }
            Placement b = f.blocks.get(i);
            objects.move(f.objects[i], b.x() + 0.5, b.y() + 0.5, b.z() + 0.5, scale);
        }
        f.resized = true;
    }

    private void moveObjects(Flight f, long now) {
        double off = offset(f, now);
        float scale = scale(f, now);
        float plants = plantScale(f, now);
        for (int i = 0; i < f.made; i++) {
            if (f.objects[i] == null) {
                continue;
            }
            Placement b = f.blocks.get(i);
            objects.move(f.objects[i], b.x() + 0.5 + f.dir[0] * off, b.y() + 0.5 + f.dir[1] * off,
                    b.z() + 0.5 + f.dir[2] * off, i < f.solids ? scale : plants);
        }
    }

    /** Clears the blocks of a standing island: decoration first, then the solid blocks. */
    private void clearBlocks(Flight f) {
        for (int pass = 0; pass < 2; pass++) {
            boolean decorPass = pass == 0;
            for (Placement b : f.blocks) {
                if (b.decor() == decorPass) {
                    world.clear(b.x(), b.y(), b.z());
                }
            }
        }
    }

    /** Solid blocks first, then decoration (plants need their ground). */
    private static List<Placement> ordered(List<Placement> blocks) {
        List<Placement> out = new ArrayList<>(blocks.size());
        for (Placement p : blocks) {
            if (!p.decor()) {
                out.add(p);
            }
        }
        for (Placement p : blocks) {
            if (p.decor()) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ numbers for tests and the self test

    void resetStats() {
        ticks = 0;
        tickNanos = 0;
        maxTickNanos = 0;
        spawned = 0;
        removed = 0;
        landedBlocks = 0;
        incomplete = 0;
        java.util.Arrays.fill(ways, 0);
    }

    String stats() {
        double avg = ticks == 0 ? 0 : tickNanos / 1e6 / ticks;
        return String.format("%d ticks, %.2f ms average, %.2f ms longest, %d objects made, %d removed, %d blocks landed, "
                + "%d blocks not landed (taken), %d objects left, %d flights without objects; ways in: %s", ticks, avg,
                maxTickNanos / 1e6, spawned, removed, landedBlocks, incomplete, live, notMade, waysText());
    }

    /** How often each way in was taken: ahead, below, above, side +Z, side -Z, none clear. */
    String waysText() {
        return String.format("ahead %d, below %d, above %d, side %d/%d, none clear %d", ways[0], ways[1], ways[2],
                ways[3], ways[4], ways[5]);
    }

    /** How often each way in was taken (see {@link #waysText()}). */
    long[] wayCounts() {
        return ways.clone();
    }

    long incomplete() {
        return incomplete;
    }

    long notMade() {
        return notMade;
    }
}
