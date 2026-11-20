package dev.endlessparkour;

import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import dev.endlessparkour.course.CourseGenerator;
import dev.endlessparkour.course.Placement;
import dev.endlessparkour.course.Platform;
import dev.endlessparkour.course.Themes;
import dev.endlessparkour.course.Tier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Checks the gliding islands in a pretend world: whole runs with islands flying in and out as
 * objects, falls in the middle of a flight, foreign blocks, chunks that load late and very fast
 * players. World tasks run after each tick, as in the game. Run with:
 * java -cp <classes> dev.endlessparkour.IslandMoverTest
 */
public final class IslandMoverTest {

    private static final int DISTANCE = 12;
    private static final long MILLIS = 1000;
    private static final long TICK = 33;
    private static final UUID OWNER = new UUID(1, 1);
    private static final int[] AHEAD = IslandMover.WAYS_IN[0];
    private static final IslandMover.PlantStyle GROW = IslandMover.PlantStyle.GROW;

    private static int failures;

    /** Blocks: a map of positions to block names. */
    static final class FakeWorld implements BlockAccess {
        final Map<Long, String> blocks = new HashMap<>();
        final Set<Long> unloaded = new HashSet<>();

        @Override
        public int blockAt(int x, int y, int z) {
            long k = RunManager.pack(x, y, z);
            if (unloaded.contains(k)) {
                return -1;
            }
            return blocks.containsKey(k) ? 2 : 0;
        }

        @Override
        public boolean place(Placement p, int x, int y, int z) {
            long k = RunManager.pack(x, y, z);
            if (unloaded.contains(k)) {
                return false;
            }
            String old = blocks.put(k, p.block());
            if (old != null) {
                throw new IllegalStateException("a block was overwritten at " + x + " " + y + " " + z + ": " + old);
            }
            return true;
        }

        @Override
        public boolean clear(int x, int y, int z) {
            long k = RunManager.pack(x, y, z);
            if (unloaded.contains(k)) {
                return false;
            }
            String old = blocks.remove(k);
            if ("FOREIGN".equals(old)) {
                throw new IllegalStateException("a foreign block was cleared at " + x + " " + y + " " + z);
            }
            return true;
        }

        void put(Platform p) {
            for (Placement b : p.blocks()) {
                blocks.put(RunManager.pack(b.x(), b.y(), b.z()), b.block());
            }
        }

        void remove(Platform p) {
            for (Placement b : p.blocks()) {
                blocks.remove(RunManager.pack(b.x(), b.y(), b.z()));
            }
        }
    }

    /** Flying objects: id to position and size (and the block they stand for). */
    static final class FakeObjects implements FlyingBlocks {
        final Map<Integer, double[]> live = new HashMap<>();
        final Map<Integer, Placement> block = new HashMap<>();
        int next;
        int madeThisTick;
        int removedThisTick;
        /** The game cannot make objects (that part of the world is not active). */
        boolean fail;
        /** False when plants must not become objects (style LAND). */
        boolean plantsFly = true;

        // A pretend player's game, as Hytale's tracker fills it: objects up to 512 blocks away whose box
        // (boxSize, scaled with the object, but only from the object's second tick on) is at least
        // lodRatio x distance² thick. Off while viewer is null.
        double[] viewer;
        double boxSize = 1;
        double lodRatio = EntityTrackerSystems.LODCull.ENTITY_LOD_RATIO_DEFAULT;
        final Set<Integer> sent = new HashSet<>();
        final Map<Integer, Integer> ticksSeen = new HashMap<>();

        /** The tracker at the end of the tick: what the player's game has now. */
        void track() {
            if (viewer == null) {
                return;
            }
            sent.clear();
            for (Map.Entry<Integer, double[]> e : live.entrySet()) {
                int age = ticksSeen.merge(e.getKey(), 1, Integer::sum);
                double[] o = e.getValue();
                double scale = age == 1 ? 1.0 : o[3];
                double dx = o[0] - viewer[0];
                double dy = o[1] - viewer[1];
                double dz = o[2] - viewer[2];
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 <= 512.0 * 512.0 && boxSize * scale >= lodRatio * d2) {
                    sent.add(e.getKey());
                }
            }
        }

        @Override
        public Predicate<Object> sentTo(UUID player) {
            if (viewer == null) {
                return null;
            }
            return h -> sent.contains((Integer) h);
        }

        @Override
        public Object spawn(Placement b, double x, double y, double z, float scale, long lifetimeMs) {
            if (b.decor() && !plantsFly) {
                throw new IllegalStateException("plants should not fly here: " + b.block());
            }
            if (fail) {
                return null;
            }
            int id = next++;
            live.put(id, new double[] {x, y, z, scale});
            block.put(id, b);
            madeThisTick++;
            if (lifetimeMs < 1000) {
                throw new IllegalStateException("objects must live at least as long as a flight");
            }
            return id;
        }

        @Override
        public void move(Object handle, double x, double y, double z, float scale) {
            double[] o = live.get((Integer) handle);
            if (o == null) {
                throw new IllegalStateException("moved an object that is gone");
            }
            o[0] = x;
            o[1] = y;
            o[2] = z;
            o[3] = scale;
        }

        @Override
        public void remove(Object handle) {
            if (live.remove((Integer) handle) == null) {
                throw new IllegalStateException("removed an object twice");
            }
            ticksSeen.remove((Integer) handle);
            sent.remove((Integer) handle);
            removedThisTick++;
        }
    }

    /** A pretend server: world, objects, world tasks and a clock. */
    static final class Sim {
        final FakeWorld world = new FakeWorld();
        final FakeObjects objects = new FakeObjects();
        final Deque<Runnable> tasks = new ArrayDeque<>();
        final List<Placement> notLoaded = new ArrayList<>();
        long now;
        final List<String> notes = new ArrayList<>();
        final IslandMover mover = new IslandMover(world, objects, tasks::add, notLoaded::addAll, () -> now, notes::add);

        int maxMadePerTick;
        int maxRemovedPerTick;

        /** One world tick: the mover moves, then the queued world tasks run. */
        void tick() {
            now += TICK;
            objects.madeThisTick = 0;
            objects.removedThisTick = 0;
            mover.tick(now);
            objects.track();
            while (!tasks.isEmpty()) {
                tasks.poll().run();
            }
            maxMadePerTick = Math.max(maxMadePerTick, objects.madeThisTick);
            maxRemovedPerTick = Math.max(maxRemovedPerTick, objects.removedThisTick);
        }
    }

    public static void main(String[] args) {
        runs();
        overlapAtLanding();
        overlapAtTakeOff();
        madeOutOfSight();
        cannotMakeObjects();
        plantStyles();
        fallMidFlight();
        cancelBeforeTask();
        foreignBlocks();
        lateChunks();
        fastPlayer();
        plannedAhead();
        stalledServer();
        sendBox();
        staysInGame();
        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("Island flights OK");
    }

    /** Runs like real ones: 2 islands at once, the rest flies in, then one in and one out per step. */
    private static void runs() {
        long checks = 0;
        long overlapTicks = 0;
        long objectTicks = 0;
        long flights = 0;
        long maxObjects = 0;
        long[] ways = new long[IslandMover.WAYS_IN.length + 1];
        for (int s = 0; s < 40; s++) {
            Sim sim = new Sim();
            IslandMover m = sim.mover;
            CourseGenerator g = new CourseGenerator(3000L + s, Themes.ALL, Themes.START, Tier.defaults(), 0, 120, 80, 170,
                    22, 45, 14);
            Deque<Platform> run = new ArrayDeque<>();
            run.add(g.start(0));
            while (run.peekLast().index() < 7) {
                run.add(g.next(run.peekLast()));
            }
            List<Platform> all = new ArrayList<>(run);
            sim.world.put(all.get(0));
            sim.world.put(all.get(1));
            long start = sim.now + 300;
            for (int i = 2; i < all.size(); i++) {
                Platform f = all.get(i);
                IslandMover.Plan plan = m.chooseWay(f, run, DISTANCE, start, MILLIS);
                m.flyIn(OWNER, f, plan.way(), DISTANCE, plan.start(), MILLIS, GROW);
                start = plan.start() + 150;
                flights++;
            }
            long nextReach = 600;
            int score = 0;
            for (int step = 0; step < 6000; step++) {
                sim.tick();
                if (score >= 120 && m.active() == 0) {
                    break;
                }
                if (sim.now >= nextReach && score < 120) {
                    score++;
                    nextReach = sim.now + 300 + (step * 7919L % 600);
                    long st = sim.now;
                    while (run.peekLast().index() < score + 7) {
                        Platform n = g.next(run.peekLast());
                        run.add(n);
                        IslandMover.Plan plan = m.chooseWay(n, run, DISTANCE, st, MILLIS);
                        m.flyIn(OWNER, n, plan.way(), DISTANCE, plan.start(), MILLIS, GROW);
                        st = plan.start() + 150;
                        flights++;
                    }
                    long so = sim.now;
                    while (run.size() > 1 && run.peekFirst().index() < score - 3) {
                        m.flyOut(OWNER, run.pollFirst(), DISTANCE, so, MILLIS, GROW);
                        so += 150;
                        flights++;
                    }
                }
                checks += checkSettled(sim, run, "seed " + (3000 + s) + " at " + sim.now + " ms");
                check(sim.objects.live.size() == m.objects(), "object count matches");
                maxObjects = Math.max(maxObjects, sim.objects.live.size());
                if (!sim.objects.live.isEmpty()) {
                    objectTicks++;
                    if (overlaps(sim)) {
                        overlapTicks++;
                    }
                }
            }
            check(m.active() == 0, "all flights over");
            check(sim.objects.live.isEmpty(), "no objects left");
            check(sim.world.blocks.equals(expected(run)), "only the run's islands are left (seed " + (3000 + s) + ")");
            check(m.incomplete() == 0 && sim.notLoaded.isEmpty(), "every island landed whole");
            long[] counts = m.wayCounts();
            for (int i = 0; i < ways.length; i++) {
                ways[i] += counts[i];
            }
        }
        System.out.printf("Runs: 40 courses x 120 platforms, %d flights, %d island checks, at most %d objects at once%n",
                flights, checks, maxObjects);
        System.out.printf("Ways in: ahead %d, from below %d, from above %d, from the sides %d/%d, none clear %d%n",
                ways[0], ways[1], ways[2], ways[3], ways[4], ways[5]);
        System.out.printf("Ticks with objects of two islands (or an object and a block) in one place: %d of %d%n",
                overlapTicks, objectTicks);
        check(ways[5] == 0, "a clear way for every island");
        check(overlapTicks <= objectTicks / 100, "islands hardly ever fly through each other");
    }

    /**
     * True when two objects, or an object and a block, are in the same block space; an object inside
     * its own block (just landed, or about to take off) does not count.
     */
    private static boolean overlaps(Sim sim) {
        Set<Long> cells = new HashSet<>();
        for (Map.Entry<Integer, double[]> e : sim.objects.live.entrySet()) {
            double[] o = e.getValue();
            if (o[3] < 0.5 || o[1] < 5) {
                continue; // still tiny, or still waiting far below
            }
            int x = (int) Math.floor(o[0]);
            int y = (int) Math.floor(o[1]);
            int z = (int) Math.floor(o[2]);
            Placement b = sim.objects.block.get(e.getKey());
            boolean home = b.x() == x && b.y() == y && b.z() == z;
            long k = RunManager.pack(x, y, z);
            if (!cells.add(k) || (!home && sim.world.blocks.containsKey(k))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Landing: the objects arrive at full size (no seams), the blocks come while the objects still
     * cover them, then the objects slip inside the blocks and go.
     */
    private static void overlapAtLanding() {
        Sim sim = new Sim();
        Platform p = biggest(course(21, 40));
        sim.mover.flyIn(OWNER, p, AHEAD, DISTANCE, 2 * IslandMover.HIDE_MS, MILLIS, GROW);
        boolean together = false;
        boolean blocksBeforeObjectsGo = true;
        boolean fullWhenBlocksCame = false;
        boolean shrankOnlyOverBlocks = true;
        boolean wasFull = false;
        boolean slippedInside = false;
        boolean hadBlocks = false;
        int solids = solids(p);
        for (int i = 0; i < 80; i++) {
            int objectsBefore = sim.objects.live.size();
            sim.tick();
            boolean blocks = solidsThere(sim, p) == solids;
            double smallest = 9;
            double largest = 0;
            for (double[] o : sim.objects.live.values()) {
                smallest = Math.min(smallest, o[3]);
                largest = Math.max(largest, o[3]);
            }
            if (blocks && !hadBlocks) {
                fullWhenBlocksCame = !sim.objects.live.isEmpty() && smallest >= IslandMover.FULL_SCALE - 1e-6;
            }
            hadBlocks |= blocks;
            if (!sim.objects.live.isEmpty() && smallest >= IslandMover.FULL_SCALE - 1e-6) {
                wasFull = true;
            }
            if (wasFull && !sim.objects.live.isEmpty() && smallest < IslandMover.FULL_SCALE - 1e-6) {
                if (!blocks) {
                    shrankOnlyOverBlocks = false;
                } else if (largest <= IslandMover.INSIDE_SCALE + 1e-6) {
                    slippedInside = true;
                }
            }
            if (blocks && sim.objects.live.size() >= solids) {
                together = true;
            }
            if (objectsBefore > sim.objects.live.size() && !blocks) {
                blocksBeforeObjectsGo = false;
            }
        }
        check(together, "landed blocks and objects are there together for a moment (no flicker)");
        check(blocksBeforeObjectsGo, "objects only go once the blocks are there");
        check(fullWhenBlocksCame, "objects cover the new blocks at full size when they come");
        check(shrankOnlyOverBlocks && slippedInside, "objects slip inside only once the blocks are there");
        check(sim.objects.live.isEmpty() && sim.world.blocks.equals(expected(List.of(p))), "landed whole, objects gone");
        check(p.blocks().size() > IslandMover.BATCH && sim.maxMadePerTick <= IslandMover.BATCH
                && sim.maxRemovedPerTick <= IslandMover.BATCH, "a big island (" + p.blocks().size()
                + " blocks) makes and removes at most " + IslandMover.BATCH + " objects a tick (made "
                + sim.maxMadePerTick + ", removed " + sim.maxRemovedPerTick + ")");
    }

    /** Take-off: the objects stand inside the blocks before the blocks go. */
    private static void overlapAtTakeOff() {
        Sim sim = new Sim();
        Platform p = biggest(course(22, 40));
        sim.world.put(p);
        sim.mover.flyOut(OWNER, p, DISTANCE, 0, MILLIS, GROW);
        boolean together = false;
        boolean objectsBeforeBlocksGo = true;
        boolean fullWhenBlocksWent = false;
        boolean hiddenWhileMade = true;
        boolean plantsFirst = true;
        int solids = solids(p);
        for (int i = 0; i < 80; i++) {
            boolean blocksBefore = solidsThere(sim, p) > 0;
            sim.tick();
            int solidNow = solidsThere(sim, p);
            if (solidNow > 0 && sim.objects.live.size() == p.blocks().size()) {
                together = true;
            }
            if (solidNow > 0 && sim.objects.live.size() < p.blocks().size()) {
                for (double[] o : sim.objects.live.values()) {
                    hiddenWhileMade &= o[3] <= IslandMover.INSIDE_SCALE + 1e-6;
                }
            }
            if (solidNow < solids && sim.world.blocks.size() > solidNow) {
                plantsFirst = false; // a solid block went while plants were still there
            }
            if (blocksBefore && solidNow == 0) {
                if (sim.objects.live.size() < p.blocks().size()) {
                    objectsBeforeBlocksGo = false;
                }
                fullWhenBlocksWent = true;
                for (double[] o : sim.objects.live.values()) {
                    fullWhenBlocksWent &= o[3] >= IslandMover.FULL_SCALE - 1e-6;
                }
            }
        }
        check(together, "objects and blocks are there together before take-off (no flicker)");
        check(objectsBeforeBlocksGo, "blocks only go once all objects are there");
        check(hiddenWhileMade, "objects appear hidden inside the blocks");
        check(fullWhenBlocksWent, "objects are at full size when the blocks go");
        check(plantsFirst, "plants and leaves never stay behind when the solid blocks go");
        check(sim.objects.live.isEmpty() && sim.world.blocks.isEmpty() && sim.mover.active() == 0, "gone after flying away");
    }

    /** New objects wait far below and only show up tiny, where their flight starts. */
    private static void madeOutOfSight() {
        Sim sim = new Sim();
        Platform p = biggest(course(23, 40));
        long start = 500;
        sim.mover.flyIn(OWNER, p, AHEAD, DISTANCE, start, MILLIS, GROW);
        Set<Integer> seenUp = new HashSet<>();
        boolean hiddenBefore = true;
        boolean tinyWhenUp = true;
        boolean atStartWhenUp = true;
        int madeBefore = 0;
        for (int i = 0; i < 80; i++) {
            sim.tick();
            for (Map.Entry<Integer, double[]> e : sim.objects.live.entrySet()) {
                double[] o = e.getValue();
                boolean up = o[1] >= 5;
                if (sim.now < start) {
                    hiddenBefore &= !up && o[3] <= IslandMover.MIN_SCALE + 1e-6;
                } else if (up && seenUp.add(e.getKey())) {
                    tinyWhenUp &= o[3] <= 0.1;
                    Placement b = sim.objects.block.get(e.getKey());
                    atStartWhenUp &= o[0] - (b.x() + 0.5) >= DISTANCE - 1.5;
                }
            }
            if (sim.now < start) {
                madeBefore = sim.objects.live.size();
            }
        }
        check(madeBefore == p.blocks().size(), "all objects made before the flight starts (" + madeBefore + " of "
                + p.blocks().size() + ")");
        check(hiddenBefore, "until the flight starts the objects wait far below and tiny");
        check(tinyWhenUp && atStartWhenUp, "objects show up tiny, where their flight starts");
        check(sim.world.blocks.equals(expected(List.of(p))) && sim.objects.live.isEmpty(), "landed whole");
    }

    /** The game cannot make objects: the island still stands on time, a leaving one goes at once, and the log says so. */
    private static void cannotMakeObjects() {
        Sim sim = new Sim();
        sim.objects.fail = true;
        List<Platform> list = course(24, 5);
        Platform in = list.get(3);
        sim.mover.flyIn(OWNER, in, AHEAD, DISTANCE, 300, MILLIS, GROW);
        long landedAt = -1;
        for (int i = 0; i < 80; i++) {
            sim.tick();
            if (landedAt < 0 && sim.world.blocks.size() == in.blocks().size()) {
                landedAt = sim.now;
            }
        }
        check(landedAt >= 300 + MILLIS && landedAt <= 300 + MILLIS + 2 * TICK,
                "an island without objects stands when it would have landed (" + landedAt + " ms)");
        check(sim.objects.live.isEmpty() && sim.mover.active() == 0 && sim.mover.objects() == 0, "no objects, no flight left");
        Platform out = list.get(2);
        sim.world.put(out);
        sim.mover.flyOut(OWNER, out, DISTANCE, sim.now, MILLIS, GROW);
        for (int i = 0; i < 5; i++) {
            sim.tick();
        }
        check(sim.world.blocks.equals(expected(List.of(in))), "a leaving island without objects goes at once");
        check(sim.notes.size() == 2 && sim.mover.notMade() == 2, "both told in the log (" + sim.notes + ")");
    }

    /** The three plant styles: how plants travel, and that they always land together with the island. */
    private static void plantStyles() {
        Platform p = mostDecor(course(25, 60));
        int decor = p.blocks().size() - solids(p);
        check(decor > 20, "test island has plants or leaves (" + decor + ")");
        for (IslandMover.PlantStyle style : IslandMover.PlantStyle.values()) {
            Sim sim = new Sim();
            sim.objects.plantsFly = style != IslandMover.PlantStyle.LAND;
            sim.mover.flyIn(OWNER, p, AHEAD, DISTANCE, 300, MILLIS, style);
            double plantAtHalf = -1;
            double solidAtHalf = -1;
            double plantNearEnd = -1;
            boolean together = true;
            boolean landedOnce = false;
            for (int i = 0; i < 80; i++) {
                sim.tick();
                double plant = 0;
                double solid = 0;
                for (Map.Entry<Integer, double[]> e : sim.objects.live.entrySet()) {
                    if (e.getValue()[1] < 5) {
                        continue;
                    }
                    if (sim.objects.block.get(e.getKey()).decor()) {
                        plant = Math.max(plant, e.getValue()[3]);
                    } else {
                        solid = Math.max(solid, e.getValue()[3]);
                    }
                }
                long t = sim.now - 300;
                if (t >= MILLIS * 4 / 10 && t < MILLIS * 4 / 10 + TICK) {
                    plantAtHalf = plant;
                    solidAtHalf = solid;
                }
                if (t >= MILLIS * 9 / 10 && t < MILLIS * 9 / 10 + TICK) {
                    plantNearEnd = plant;
                }
                int solidsNow = solidsThere(sim, p);
                int plantsNow = sim.world.blocks.size() - solidsNow;
                if (solidsNow > 0 || plantsNow > 0) {
                    landedOnce = true;
                    together &= solidsNow == solids(p) && plantsNow == decor;
                }
            }
            String n = style.id();
            check(landedOnce && together, n + ": plants land in the same moment as the island");
            check(sim.world.blocks.equals(expected(List.of(p))) && sim.objects.live.isEmpty(), n + ": landed whole");
            switch (style) {
                case GROW -> check(plantAtHalf < 0.2 && solidAtHalf > 0.6 && plantNearEnd > 0.7,
                        n + ": plants tiny at first, grown near the end (" + plantAtHalf + " / " + plantNearEnd + ")");
                case FLY -> check(Math.abs(plantAtHalf - solidAtHalf) < 1e-6 && plantAtHalf > 0.6,
                        n + ": plants fly at the island's size");
                case LAND -> check(plantAtHalf == 0 && plantNearEnd == 0, n + ": no plant objects");
            }
        }
    }

    private static int solids(Platform p) {
        int n = 0;
        for (Placement b : p.blocks()) {
            if (!b.decor()) {
                n++;
            }
        }
        return n;
    }

    private static int solidsThere(Sim sim, Platform p) {
        int n = 0;
        for (Placement b : p.blocks()) {
            if (!b.decor() && b.block().equals(sim.world.blocks.get(RunManager.pack(b.x(), b.y(), b.z())))) {
                n++;
            }
        }
        return n;
    }

    private static Platform mostDecor(List<Platform> list) {
        Platform best = list.get(1);
        for (Platform p : list) {
            if (p.index() > 0 && p.blocks().size() - solids(p) > best.blocks().size() - solids(best)) {
                best = p;
            }
        }
        return best;
    }

    private static Platform biggest(List<Platform> list) {
        Platform best = list.get(1);
        for (Platform p : list) {
            if (p.index() > 0 && p.blocks().size() > best.blocks().size()) {
                best = p;
            }
        }
        return best;
    }

    /** Every settled island (not flying) is whole, and the world holds nothing else but leaving islands before take-off. */
    private static int checkSettled(Sim sim, Deque<Platform> run, String when) {
        int checks = 0;
        Set<Long> standing = new HashSet<>();
        sim.mover.positions(standing);
        for (Platform p : run) {
            if (sim.mover.isFlying(p)) {
                continue;
            }
            for (Placement b : p.blocks()) {
                long k = RunManager.pack(b.x(), b.y(), b.z());
                standing.add(k);
                String got = sim.world.blocks.get(k);
                if (!b.block().equals(got)) {
                    check(false, "island " + p.index() + " whole: " + b.block() + " is " + got + " (" + when + ")");
                    return checks;
                }
            }
            checks++;
        }
        for (Long k : sim.world.blocks.keySet()) {
            if (!standing.contains(k)) {
                check(false, "stray block " + sim.world.blocks.get(k) + " (" + when + ")");
                return checks;
            }
        }
        return checks;
    }

    private static Map<Long, String> expected(Iterable<Platform> run) {
        Map<Long, String> out = new HashMap<>();
        for (Platform p : run) {
            for (Placement b : p.blocks()) {
                out.put(RunManager.pack(b.x(), b.y(), b.z()), b.block());
            }
        }
        return out;
    }

    /** A fall while islands fly in and out: cancel() takes every object away and clears leaving islands. */
    private static void fallMidFlight() {
        Sim sim = new Sim();
        List<Platform> list = course(77, 12);
        sim.world.put(list.get(0));
        sim.world.put(list.get(1));
        for (int i = 2; i < 10; i++) {
            sim.mover.flyIn(OWNER, list.get(i), AHEAD, DISTANCE, IslandMover.HIDE_MS + 150L * (i - 2), MILLIS, GROW);
        }
        sim.mover.flyOut(OWNER, list.get(0), DISTANCE, 300, MILLIS, GROW);
        for (int i = 0; i < 6; i++) {
            sim.tick();
        }
        check(sim.mover.busy(OWNER) && !sim.objects.live.isEmpty(), "islands are on their way");
        check(sim.world.blocks.size() == list.get(0).blocks().size() + list.get(1).blocks().size(),
                "the leaving island still stands before take-off");
        sim.mover.cancel(OWNER);
        // The run is stopped: its standing islands are cleared at their place.
        sim.world.remove(list.get(1));
        check(sim.world.blocks.isEmpty(), "no blocks left after a fall mid-flight (" + sim.world.blocks.size() + ")");
        check(sim.objects.live.isEmpty() && sim.mover.active() == 0 && sim.mover.objects() == 0,
                "no objects after a fall");
        for (int i = 0; i < 100; i++) {
            sim.tick();
        }
        check(sim.world.blocks.isEmpty() && sim.objects.live.isEmpty(), "and nothing comes back later");
    }

    /** A fall right after a flight asked for its start or landing: the queued task does nothing. */
    private static void cancelBeforeTask() {
        Sim sim = new Sim();
        List<Platform> list = course(78, 5);
        sim.mover.flyIn(OWNER, list.get(2), AHEAD, DISTANCE, 0, MILLIS, GROW);
        sim.now += TICK;
        sim.mover.tick(sim.now); // queues the take-off
        check(sim.tasks.size() == 1, "take-off queued");
        sim.mover.cancel(OWNER);
        while (!sim.tasks.isEmpty()) {
            sim.tasks.poll().run();
        }
        check(sim.objects.live.isEmpty() && sim.world.blocks.isEmpty(), "a cancelled take-off makes nothing");

        long t0 = sim.now + IslandMover.HIDE_MS;
        sim.mover.flyIn(OWNER, list.get(3), AHEAD, DISTANCE, t0, MILLIS, GROW);
        for (int i = 0; i < 10; i++) {
            sim.tick(); // take-off, a batch of objects per tick
        }
        check(sim.objects.live.size() == list.get(3).blocks().size(), "objects made at take-off");
        sim.now = t0 + MILLIS + 10;
        sim.mover.tick(sim.now); // queues the landing
        check(sim.tasks.size() == 1, "landing queued");
        sim.mover.cancel(OWNER);
        while (!sim.tasks.isEmpty()) {
            sim.tasks.poll().run();
        }
        check(sim.objects.live.isEmpty() && sim.world.blocks.isEmpty(), "a cancelled landing places nothing");
    }

    /** A foreign block at the landing place is left alone; the rest of the island lands. */
    private static void foreignBlocks() {
        Sim sim = new Sim();
        Platform p = course(5, 3).get(2);
        Placement first = p.blocks().get(0);
        long k = RunManager.pack(first.x(), first.y(), first.z());
        sim.world.blocks.put(k, "FOREIGN");
        IslandMover.Plan plan = sim.mover.chooseWay(p, List.of(), DISTANCE, 0, MILLIS);
        check(plan.way()[0] == 1 && plan.start() == IslandMover.HIDE_MS,
                "nothing in the way: the island comes from ahead as soon as it can");
        sim.mover.flyIn(OWNER, p, AHEAD, DISTANCE, plan.start(), MILLIS, GROW);
        for (int i = 0; i < 80; i++) {
            sim.tick();
        }
        Map<Long, String> want = expected(List.of(p));
        want.put(k, "FOREIGN");
        check(sim.world.blocks.equals(want), "foreign block untouched, the rest landed");
        check(sim.mover.incomplete() == 1 && sim.objects.live.isEmpty(), "one block left out, no objects left");
    }

    /** Chunks not loaded at landing: those blocks are handed on to be placed once loaded. */
    private static void lateChunks() {
        Sim sim = new Sim();
        Platform p = course(9, 3).get(2);
        for (Placement b : p.blocks()) {
            sim.world.unloaded.add(RunManager.pack(b.x(), b.y(), b.z()));
        }
        sim.mover.flyIn(OWNER, p, AHEAD, DISTANCE, IslandMover.HIDE_MS, MILLIS, GROW);
        for (int i = 0; i < 80; i++) {
            sim.tick();
        }
        check(sim.notLoaded.size() == p.blocks().size() && sim.objects.live.isEmpty() && !sim.mover.isFlying(p),
                "blocks of unloaded chunks handed on (" + sim.notLoaded.size() + " of " + p.blocks().size() + ")");
    }

    /** A player faster than the flight: an island that is still flying in is simply taken away. */
    private static void fastPlayer() {
        Sim sim = new Sim();
        Platform p = course(11, 3).get(2);
        sim.mover.flyIn(OWNER, p, AHEAD, DISTANCE, IslandMover.HIDE_MS, MILLIS, GROW);
        for (int i = 0; i < 10; i++) {
            sim.tick();
        }
        sim.mover.flyOut(OWNER, p, DISTANCE, sim.now, MILLIS, GROW);
        for (int i = 0; i < 80; i++) {
            sim.tick();
        }
        check(sim.world.blocks.isEmpty() && sim.objects.live.isEmpty() && sim.mover.active() == 0,
                "nothing left of an island taken away mid-flight");
    }

    /** A flight asked for "now" is planned HIDE_MS ahead, so all objects are made well before it starts. */
    private static void plannedAhead() {
        Sim sim = new Sim();
        sim.now = 5000;
        Platform p = biggest(course(31, 40));
        IslandMover.Plan plan = sim.mover.chooseWay(p, List.of(), DISTANCE, sim.now, MILLIS);
        check(plan.start() >= sim.now + IslandMover.HIDE_MS, "a flight asked for now starts " + IslandMover.HIDE_MS
                + " ms later at the earliest (" + (plan.start() - sim.now) + " ms)");
        sim.mover.flyIn(OWNER, p, plan.way(), DISTANCE, plan.start(), MILLIS, GROW);
        long lastMade = -1;
        for (int i = 0; i < 80; i++) {
            int before = sim.objects.next;
            sim.tick();
            if (sim.objects.next > before) {
                lastMade = sim.now;
            }
        }
        check(sim.objects.next == p.blocks().size(), "every block of a big island flew (" + sim.objects.next + " of "
                + p.blocks().size() + ")");
        check(lastMade >= 0 && lastMade <= plan.start() - IslandMover.SETTLE_MS, "all objects made at least "
                + IslandMover.SETTLE_MS + " ms before the flight (the last " + (plan.start() - lastMade) + " ms before)");
        check(sim.world.blocks.equals(expected(List.of(p))) && sim.objects.live.isEmpty(), "landed whole");
    }

    /**
     * The server stalls until the flight has started: objects that could not be made in time never pop
     * up during the flight; the island still lands whole and on time.
     */
    private static void stalledServer() {
        Sim sim = new Sim();
        Platform p = biggest(course(32, 40));
        long start = IslandMover.HIDE_MS;
        sim.mover.flyIn(OWNER, p, AHEAD, DISTANCE, start, MILLIS, GROW);
        while (sim.now < start + 2 * TICK) {
            sim.now += TICK;
            sim.mover.tick(sim.now); // the world tasks wait
        }
        boolean poppedUp = false;
        long landedAt = -1;
        for (int i = 0; i < 80; i++) {
            Set<Integer> before = new HashSet<>(sim.objects.live.keySet());
            sim.tick();
            for (Map.Entry<Integer, double[]> e : sim.objects.live.entrySet()) {
                if (!before.contains(e.getKey()) && sim.now >= start) {
                    poppedUp = true;
                }
            }
            if (landedAt < 0 && sim.world.blocks.size() == p.blocks().size()) {
                landedAt = sim.now;
            }
        }
        check(!poppedUp, "no object appears once the flight has started");
        check(sim.mover.watchTotals()[4] == p.blocks().size(), "objects made too late do not fly ("
                + sim.mover.watchTotals()[4] + " of " + p.blocks().size() + ")");
        check(landedAt >= start + MILLIS && landedAt <= start + MILLIS + 2 * TICK,
                "the island still lands whole and on time (" + landedAt + " ms)");
        check(sim.world.blocks.equals(expected(List.of(p))) && sim.objects.live.isEmpty() && sim.mover.active() == 0,
                "nothing left over");
    }

    /** The box of flying objects keeps them in the game at any size from MIN_SCALE up, up to SEND_RANGE away. */
    private static void sendBox() {
        double ratio = EntityTrackerSystems.LODCull.ENTITY_LOD_RATIO_DEFAULT;
        double box = EntityBlocks.sendBoxSize(ratio, EntityBlocks.SEND_RANGE, IslandMover.MIN_SCALE);
        boolean all = true;
        for (double s = IslandMover.MIN_SCALE; s <= 1.0001; s += 0.01) {
            for (double d = 0; d <= EntityBlocks.SEND_RANGE; d += 1) {
                all &= box * s >= ratio * d * d;
            }
        }
        check(all, "with a box of " + Math.round(box) + " objects stay in the game up to " + EntityBlocks.SEND_RANGE
                + " blocks away at any size");
        check(IslandMover.MIN_SCALE < ratio * 40 * 40, "with a block's own box a tiny object 40 blocks away would drop out");
        check(box >= 1 && box <= 1000, "the box stays a sane size (" + box + ")");
    }

    /**
     * The players' games, modelled on Hytale's tracker: with the box of flying objects every object reaches
     * the game while it still waits out of sight and stays there until it has landed. With a block's own
     * box the game loses the objects at the start of the flight and gets them again in the middle of it.
     */
    private static void staysInGame() {
        double box = EntityBlocks.sendBoxSize(EntityTrackerSystems.LODCull.ENTITY_LOD_RATIO_DEFAULT,
                EntityBlocks.SEND_RANGE, IslandMover.MIN_SCALE);
        long[] old = watchedRun(1.0);
        long[] now = watchedRun(box);
        check(old[0] > 0 && old[3] > 0 && old[2] > 0, "the watch sees a block box lose objects at the start and get them "
                + "again mid-flight (odd flights, lost, shown again, late, not in time, early: " + Arrays.toString(old) + ")");
        check(now[0] == 0 && now[1] == 0 && now[2] == 0 && now[3] == 0 && now[4] == 0 && now[5] > 0,
                "with the flying box every object is in the game before its flight and stays (" + Arrays.toString(now) + ")");
        System.out.printf("Players' games (modelled): block box %s, flying box %s (odd flights, lost, shown again, "
                + "late, not in time, early)%n", Arrays.toString(old), Arrays.toString(now));
    }

    /** A player stands on island 3 while the next eight islands fly in; returns the watch totals. */
    private static long[] watchedRun(double boxSize) {
        Sim sim = new Sim();
        sim.objects.boxSize = boxSize;
        List<Platform> list = course(41, 14);
        Placement top = list.get(3).blocks().get(0);
        for (Placement b : list.get(3).blocks()) {
            if (!b.decor() && b.y() > top.y()) {
                top = b;
            }
        }
        sim.objects.viewer = new double[] {top.x() + 0.5, top.y() + 1.0, top.z() + 0.5};
        List<Platform> run = new ArrayList<>(list.subList(0, 4));
        for (Platform p : run) {
            sim.world.put(p);
        }
        long start = sim.now;
        for (int i = 4; i < 12; i++) {
            Platform p = list.get(i);
            run.add(p);
            IslandMover.Plan plan = sim.mover.chooseWay(p, run, DISTANCE, start, MILLIS);
            sim.mover.flyIn(OWNER, p, plan.way(), DISTANCE, plan.start(), MILLIS, GROW);
            start = plan.start() + 150;
        }
        for (int i = 0; i < 300 && sim.mover.active() > 0; i++) {
            sim.tick();
        }
        check(sim.mover.active() == 0 && sim.world.blocks.equals(expected(run)), "watched flights landed whole");
        return sim.mover.watchTotals();
    }

    private static List<Platform> course(long seed, int length) {
        CourseGenerator g = new CourseGenerator(seed, Themes.ALL, Themes.START, Tier.defaults(), 0, 120, 80, 170, 22, 45, 14);
        List<Platform> list = new ArrayList<>();
        Platform p = g.start(0);
        list.add(p);
        while (list.size() < length) {
            p = g.next(p);
            list.add(p);
        }
        return list;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            if (failures <= 20) {
                System.out.println("FAIL: " + what);
            }
        }
    }
}
