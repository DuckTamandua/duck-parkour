package dev.endlessparkour.course;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks the course generator without a server: hundreds of random courses, every jump,
 * every block. Run with: java -cp <classes> dev.endlessparkour.course.GeneratorTest
 */
public final class GeneratorTest {

    private static final int SEEDS = 300;
    private static final int LENGTH = 400;
    private static final int SECTION_MIN = 22;
    private static final int SECTION_MAX = 45;
    private static final int BASE_Y = 120;
    private static final int MIN_Y = 80;
    private static final int MAX_Y = 170;
    private static final int DRIFT = 14;
    private static final Tier[] LEVELS = Tier.defaults();

    private static int failures;
    /** Indexes of safety-net platforms of the last course built by course(). */
    private static final Set<Integer> lastFallbacks = new HashSet<>();
    /** Safety platforms placed without a free spot, over all courses built. */
    private static int lastResorts;

    public static void main(String[] args) {
        jumpModel();
        areas();
        courses();
        randomAreas();
        determinism();
        difficultyByLevel();
        specialJumps();
        standing();
        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("ALL OK");
    }

    private static CourseGenerator gen(long seed, int laneZ) {
        return new CourseGenerator(seed, Themes.ALL, Themes.START, LEVELS, laneZ, BASE_Y, MIN_Y, MAX_Y,
                SECTION_MIN, SECTION_MAX, DRIFT);
    }

    private static List<Platform> course(long seed, int laneZ, int length) {
        CourseGenerator g = gen(seed, laneZ);
        List<Platform> list = new ArrayList<>();
        lastFallbacks.clear();
        Platform p = g.start(0);
        list.add(p);
        while (list.size() < length) {
            int before = g.fallbacks();
            p = g.next(p);
            if (g.fallbacks() > before) {
                lastFallbacks.add(p.index());
            }
            list.add(p);
        }
        lastResorts += g.lastResorts();
        return list;
    }

    private static Tier tier(Theme t) {
        return LEVELS[t.level() - 1].sanitized();
    }

    private static void jumpModel() {
        System.out.printf("Jump model: apex %.2f blocks, air time flat %.3f s%n", JumpModel.apex(), JumpModel.airTime(0));
        for (int dy = 1; dy >= -4; dy--) {
            System.out.printf("  dy %+d: max gap sprint %.2f, running %.2f%n", dy, JumpModel.maxGap(dy),
                    JumpModel.maxGapRunning(dy));
        }
        check(JumpModel.maxGap(2) < 0, "two blocks up must be out of reach");
        check(JumpModel.maxDashGap(0) > JumpModel.maxGap(0) + 2, "a dash clears clearly more than a jump");
        check(JumpModel.maxDashGap(0) - 0.25 >= 7, "a dash clears seven blocks");
        check(JumpModel.maxGapNoDash(0) < 7, "seven blocks are too far for a jump, even with a grab at the edge");
        check(Math.abs(JumpModel.bounceApex() - 6.25) < 1e-9, "a bounce lifts 6.25 blocks");
        check(JumpModel.maxBounceGap(5) - 0.25 >= 3, "a bounce onto an island six up still goes three blocks far");
        check(JumpModel.maxBounceGap(5.6) < 0, "a bounce does not reach higher than it lifts");
        check(JumpModel.MANTLE_HEIGHT < 5, "towers and bounce targets are out of reach of a jump and a pull-up");
        check(LEVELS.length == 10, "ten levels");
        for (int i = 0; i < LEVELS.length; i++) {
            Tier s = LEVELS[i].sanitized();
            // The +0.25 tolerance for diagonal gaps must still be within reach.
            check(s.maxGap + 0.25 <= JumpModel.maxGap(0), "level " + (i + 1) + " flat gap within reach");
            check(s.maxGapUp + 0.25 <= JumpModel.maxGap(1), "level " + (i + 1) + " up gap within reach");
            check(s.maxGapDrop + 0.25 <= JumpModel.maxGap(-2), "level " + (i + 1) + " drop gap within reach");
            Tier raw = LEVELS[i];
            check(raw.maxGap == s.maxGap && raw.maxGapUp == s.maxGapUp && raw.maxGapDrop == s.maxGapDrop
                    && raw.minGap == s.minGap && raw.maxDrop == s.maxDrop, "level " + (i + 1) + " defaults need no clamping");
            if (i > 0) {
                Tier p = LEVELS[i - 1];
                check(raw.minGap >= p.minGap && raw.maxGap >= p.maxGap && raw.maxGapUp >= p.maxGapUp
                        && raw.maxGapDrop >= p.maxGapDrop && raw.maxDrop >= p.maxDrop
                        && raw.pillarChance >= p.pillarChance && raw.maxSize <= p.maxSize,
                        "level " + (i + 1) + " is at least as hard as level " + i);
            }
        }
    }

    private static void areas() {
        Set<String> ids = new HashSet<>();
        Set<Integer> levels = new HashSet<>();
        for (Theme t : Themes.ALL) {
            check(ids.add(t.id()), "unique area id " + t.id());
            check(t.level() >= 1 && t.level() <= 10, "level of " + t.name());
            levels.add(t.level());
            check(!t.zone().isBlank(), "zone of " + t.name());
            check(!t.trees().isEmpty(), "decoration for " + t.name());
            check(!t.environment().isBlank() && !t.weather().isBlank() && !t.sound().isBlank(),
                    "environment, weather and sound for " + t.name());
            check(!t.tints().isEmpty(), "grass colours for " + t.name());
            for (String hex : t.tints().names()) {
                check(hex.matches("#[0-9A-Fa-f]{6}"), "colour " + hex + " of " + t.name());
            }
            check(!t.top().isEmpty() && !t.fill().isEmpty() && !t.bottom().isEmpty() && !t.marker().isEmpty(),
                    "block layers of " + t.name());
            check(t.color().matches("#[0-9A-Fa-f]{6}"), "title colour of " + t.name());
        }
        check(Themes.ALL.size() == 18, "18 areas");
        check(levels.size() == 10, "every level 1 to 10 has an area");
        check(Themes.START.level() == 1 && Themes.START.zone().equals("Emerald Wilds"), "runs start in the green, easy area");
        System.out.println("Areas: " + Themes.ALL.size() + ", levels used: " + levels);
    }

    private static void courses() {
        int jumps = 0;
        int fallbacks = 0;
        int gates = 0;
        int gatesWithLandmark = 0;
        int landmarks = 0;
        int easier = 0;
        Map<String, Integer> problems = new HashMap<>();
        Map<Integer, Integer> sectionLengths = new TreeMap<>();
        Map<Move, Integer> moves = new EnumMap<>(Move.class);
        for (int s = 0; s < SEEDS; s++) {
            int laneZ = (s % 7) * 128;
            List<Platform> list = course(1000L + s, laneZ, LENGTH);
            check(list.get(0).theme() == Themes.START, "run starts in " + Themes.START.name(), problems);
            Theme lastTheme = null;
            int lastSectionStart = 0;
            for (int i = 0; i < list.size(); i++) {
                Platform b = list.get(i);
                check(b.index() == i, "index order", problems);
                check(b.topY() >= MIN_Y && b.topY() <= MAX_Y, "height " + b.topY() + " in band", problems);
                check(Math.abs(b.spawnZ() - laneZ) <= DRIFT + 6, "lane drift " + (b.spawnZ() - laneZ), problems);
                check(b.hasCell((int) Math.floor(b.spawnX()), (int) Math.floor(b.spawnZ())), "spawn on a cell", problems);
                check(hasTint(b.theme(), b.tint()), "tint of " + b.theme().name(), problems);
                for (Placement pl : b.blocks()) {
                    // Decoration islands stay near the lane (lanes are far apart).
                    check(Math.abs(pl.z() - laneZ) <= DRIFT + 6 + 16, "block far from the lane", problems);
                }
                blocks(b, problems);
                if (b.boxes().size() > 1) {
                    landmarks++;
                }
                if (i == 0) {
                    lastTheme = b.theme();
                    continue;
                }
                Platform a = list.get(i - 1);
                double gap = a.gapTo(b);
                int dy = b.topY() - a.topY();
                Tier t = tier(b.theme());
                jumps++;
                boolean fallback = lastFallbacks.contains(i);
                if (fallback) {
                    fallbacks++;
                }
                Move mv = b.move();
                check(i >= 3 || mv == Move.JUMP, "no special jump on the first platforms", problems);
                check(mv == Move.JUMP || b.theme().features().contains(feature(mv)),
                        mv + " is one of the area's special jumps", problems);
                check(!b.sectionStart() || mv == Move.JUMP, "area starts are normal jumps", problems);
                switch (mv) {
                    case JUMP -> {
                        check(JumpModel.feasible(gap, dy), "jump in reach gap " + gap + " dy " + dy, problems);
                        check(dy <= 1, "at most one block up", problems);
                        check(dy >= -t.maxDrop || fallback, "drop " + dy + " within level", problems);
                        // After many tries the generator may take an easier jump (gap 2, small height change).
                        if (gap < Math.min(t.minGap, t.maxGapFor(dy)) - 1e-9 && !fallback) {
                            easier++;
                            check(gap >= 1.0 - 1e-9 && dy >= -1, "easier jump is still a normal jump", problems);
                        }
                        double max = t.maxGapFor(dy) + 0.25;
                        check(gap <= max + 1e-9, "gap " + gap + " <= " + max, problems);
                    }
                    case CLIMB -> {
                        check(gap < 1e-9, "the tower stands right next to the island", problems);
                        check(dy > JumpModel.MANTLE_HEIGHT && dy <= JumpModel.MAX_CLIMB, "climb height " + dy
                                + " (above a jump and a pull-up)", problems);
                        String bad = ladderProblem(a, b);
                        check(bad == null, "ladder: " + bad, problems);
                    }
                    case BOUNCE -> {
                        check(dy > JumpModel.MANTLE_HEIGHT && dy <= 6, "bounce height " + dy
                                + " (above a jump and a pull-up)", problems);
                        String bad = bounceProblem(list, i);
                        check(bad == null, "bounce: " + bad, problems);
                    }
                    case DASH -> {
                        check(dy == 0, "dash jumps are level", problems);
                        check(gap > JumpModel.maxGapNoDash(dy), "a dash gap is too long for a jump, even with a grab at "
                                + "the edge (" + gap + ")", problems);
                        check(gap <= JumpModel.maxDashGap(dy) - 0.25 + 1e-9, "dash gap " + gap + " within a dash",
                                problems);
                        check(b.theme().level() >= 4, "dash gaps from level 4 on", problems);
                        String bad = dashMarkProblem(a, b);
                        check(bad == null, "dash mark: " + bad, problems);
                    }
                    case DROP -> {
                        check(dy <= -5 && dy >= -9, "drop of 5 to 9 blocks (" + dy + ")", problems);
                        check(JumpModel.feasible(gap, dy) && gap <= 3.25, "drop gap " + gap, problems);
                    }
                    case LADDER_JUMP, LADDER_DASH -> {
                        check(dy > JumpModel.MANTLE_HEIGHT && dy <= JumpModel.MAX_CLIMB, "ladder tower height " + dy, problems);
                        String bad = ladderJumpProblem(a, b);
                        check(bad == null, mv + ": " + bad, problems);
                        if (mv == Move.LADDER_DASH) {
                            check(b.theme().level() >= 7, "ladder dashes from level 7 on", problems);
                        } else {
                            check(b.theme().level() >= 4, "ladder jumps from level 4 on", problems);
                        }
                    }
                }
                moves.merge(mv, 1, Integer::sum);
                // The yellow mark is only ever right under a mushroom (its top two layers).
                for (Placement pl : b.blocks()) {
                    if (pl.block().equals(Themes.BOUNCE_MARK)) {
                        check(Themes.BOUNCE_PAD.equals(blockAt(b, pl.x(), b.topY() + 1, pl.z())) && pl.y() >= b.topY() - 1,
                                "bounce mark only under a mushroom", problems);
                    }
                }
                // The glowing mark is only ever at dash jumps: on the take-off's far row and the target's near row.
                for (Placement pl : b.blocks()) {
                    if (pl.block().equals(Themes.DASH_MARK)) {
                        // The last platform of a test course may be a take-off whose target was not asked for yet.
                        boolean takeOff = (i + 1 == list.size() || list.get(i + 1).move() == Move.DASH
                                || list.get(i + 1).move() == Move.LADDER_DASH) && pl.x() == b.maxX();
                        boolean target = mv == Move.DASH && pl.x() == b.minX();
                        check(takeOff || target, "dash mark only at dash jumps (platform " + i + " of " + list.size()
                                + ", " + mv + ", next " + (i + 1 < list.size() ? list.get(i + 1).move() : "none") + ")", problems);
                        break;
                    }
                }
                check(Math.abs(b.jumpGap() - gap) < 1e-9 && b.jumpDy() == dy, "jump data of the platform", problems);
                if (b.sectionStart()) {
                    gates++;
                    if (b.boxes().size() > 1) {
                        gatesWithLandmark++;
                    }
                    int length = i - lastSectionStart;
                    check(length >= SECTION_MIN && length <= SECTION_MAX, "section length " + length, problems);
                    sectionLengths.merge(length, 1, Integer::sum);
                    check(b.theme() != lastTheme, "new area at " + i, problems);
                    lastTheme = b.theme();
                    lastSectionStart = i;
                } else {
                    check(b.theme() == a.theme(), "same area inside a section", problems);
                    check(b.tint() == a.tint(), "same grass colour inside a section", problems);
                    check(i - lastSectionStart < SECTION_MAX, "section not longer than the maximum", problems);
                }
                // Nothing of a nearby platform overlaps, blocks the jump or hangs over a landing spot.
                for (int k = Math.max(0, i - 9); k < i; k++) {
                    check(!sharesBlocks(list.get(k), b), "platforms " + k + " and " + i + " share a block", problems);
                }
                String blocker = corridorBlocker(list, i);
                check(blocker == null, "jump " + (i - 1) + " -> " + i + " is not blocked: " + blocker, problems);
            }
            for (int i = 0; i < list.size(); i++) {
                check(headRoom(list, i), "head room over platform " + i, problems);
            }
        }
        System.out.printf("Courses: %d seeds x %d platforms, %d jumps, %d safety-net platforms, %d easier jumps, "
                + "%d decoration islands (%d of %d area starts)%n",
                SEEDS, LENGTH, jumps, fallbacks, easier, landmarks, gatesWithLandmark, gates);
        System.out.println("Ways onto the platforms: " + moves);
        for (Move m : Move.values()) {
            check(moves.getOrDefault(m, 0) > jumps / 400, m + " comes up (" + moves.getOrDefault(m, 0) + ")");
        }
        check(moves.getOrDefault(Move.JUMP, 0) > jumps * 0.75, "most jumps are normal jumps");
        long lengthSum = 0;
        int lengthCount = 0;
        for (Map.Entry<Integer, Integer> e : sectionLengths.entrySet()) {
            lengthSum += (long) e.getKey() * e.getValue();
            lengthCount += e.getValue();
        }
        double meanLength = (double) lengthSum / lengthCount;
        System.out.printf("Area lengths seen: %d to %d platforms, mean %.1f%n", sectionLengths.keySet().iterator().next(),
                ((TreeMap<Integer, Integer>) sectionLengths).lastKey(), meanLength);
        check(Math.abs(meanLength - (SECTION_MIN + SECTION_MAX) / 2.0) < 1.0, "mean area length " + meanLength);
        check(fallbacks <= jumps / 1000, "safety net is rare (" + fallbacks + ")");
        check(lastResorts == 0, "the safety platform always found a free spot (" + lastResorts + " did not)");
        check(easier <= jumps / 50, "easier jumps are rare (" + easier + ")");
        check(sectionLengths.size() == SECTION_MAX - SECTION_MIN + 1, "every section length occurs");
        check(gatesWithLandmark >= gates * 0.8, "most area starts have a decoration island");
        if (!problems.isEmpty()) {
            System.out.println("Problems: " + problems);
        }
    }

    private static boolean hasTint(Theme th, int rgb) {
        for (String hex : th.tints().names()) {
            if (Integer.parseInt(hex.substring(1), 16) == rgb) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> leaves(Theme th) {
        Set<String> out = new HashSet<>();
        for (Theme.Tree t : th.trees()) {
            if (t.leaves() != null) {
                out.add(t.leaves());
            }
        }
        return out;
    }

    private static Set<String> trunks(Theme th) {
        Set<String> out = new HashSet<>();
        for (Theme.Tree t : th.trees()) {
            if (t.leaves() != null) {
                out.add(t.trunk());
            }
        }
        return out;
    }

    /** Blocks a player bumps into: solid ones, leaves and hanging rocks (not plants). */
    private static boolean obstacle(Platform owner, Placement pl) {
        return !pl.decor() || pl.flipped() || leaves(owner.theme()).contains(pl.block());
    }

    private static void blocks(Platform p, Map<String, Integer> problems) {
        Theme th = p.theme();
        Set<String> leafNames = leaves(th);
        Set<String> trunkNames = trunks(th);
        Set<String> solid = new HashSet<>();
        List<int[]> trunkBlocks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Placement pl : p.blocks()) {
            String key = key(pl.x(), pl.y(), pl.z());
            check(seen.add(key), "one block per position", problems);
            if (!pl.decor()) {
                solid.add(key);
                if (trunkNames.contains(pl.block())) {
                    trunkBlocks.add(new int[] {pl.x(), pl.y(), pl.z()});
                }
            }
        }
        for (Placement pl : p.blocks()) {
            String below = key(pl.x(), pl.y() - 1, pl.z());
            String above = key(pl.x(), pl.y() + 1, pl.z());
            boolean leaf = pl.decor() && leafNames.contains(pl.block());
            if (!pl.decor() && Themes.NEEDS_FLOOR.contains(pl.block())) {
                check(solid.contains(below), pl.block() + " has a floor", problems);
            }
            if (pl.attached() || pl.block().equals(Themes.BOUNCE_PAD)) {
                // Ladders and the bounce mushroom stand on the island before: checked with the jump.
                continue;
            }
            if (pl.decor() && !pl.flipped() && !leaf) {
                check(solid.contains(below), pl.block() + " stands on a block", problems);
                // Tall plants take two blocks: anything right above breaks them in the game.
                check(!seen.contains(above), pl.block() + " has room above (" + blockAt(p, pl.x(), pl.y() + 1, pl.z()) + ")",
                        problems);
                if (th.soilPlants().names().contains(pl.block()) && !th.anyPlants().names().contains(pl.block())) {
                    String ground = blockAt(p, pl.x(), pl.y() - 1, pl.z());
                    check(Themes.SOIL.contains(ground), pl.block() + " on soil, not " + ground, problems);
                }
                if (p.hasCell(pl.x(), pl.z())) {
                    check(pl.y() == p.topY() + 1, "plant on the surface", problems);
                    check(!th.scatter().names().contains(pl.block()) || th.soilPlants().names().contains(pl.block())
                            || th.anyPlants().names().contains(pl.block()), "no clutter on landing spots", problems);
                }
            }
            if (leaf) {
                boolean near = false;
                for (int[] t : trunkBlocks) {
                    if (Math.abs(t[0] - pl.x()) <= 2 && Math.abs(t[2] - pl.z()) <= 2 && Math.abs(t[1] - pl.y()) <= 3) {
                        near = true;
                        break;
                    }
                }
                check(near, "leaves close to a trunk", problems);
                check(!p.hasCell(pl.x(), pl.z()), "no leaves over the course", problems);
            }
            if (pl.flipped()) {
                check(solid.contains(above), "hanging rock under a block", problems);
                check(!seen.contains(below), "nothing under a hanging rock", problems);
            }
            if (!pl.decor() && p.hasCell(pl.x(), pl.z())) {
                check(pl.y() <= p.topY(), "nothing solid above a landing spot", problems);
                if (pl.y() == p.topY()) {
                    check(!pl.block().startsWith("Rock_Ice") && !pl.block().equals("Soil_Snow")
                            && !pl.block().equals("Soil_Mud"), "no slippery or soft surface (" + pl.block() + ")", problems);
                }
            }
        }
        // Every surface cell has its top block.
        for (int i = 0; i < p.cellCount(); i++) {
            check(solid.contains(key(p.cellX(i), p.topY(), p.cellZ(i))), "surface block on every cell", problems);
        }
        // No lone block floating in the air: every solid block touches another one.
        for (Placement pl : p.blocks()) {
            if (pl.decor()) {
                continue;
            }
            boolean touches = false;
            int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] d : dirs) {
                if (solid.contains(key(pl.x() + d[0], pl.y() + d[1], pl.z() + d[2]))) {
                    touches = true;
                    break;
                }
            }
            check(touches, "lone floating block " + pl.block(), problems);
        }
    }

    /**
     * Nothing a player bumps into in the space of a jump: the box between the two closest surface
     * cells, from above the lower surface to head height over the higher one. Else what is in the way.
     */
    private static String corridorBlocker(List<Platform> list, int i) {
        Platform a = list.get(i - 1);
        Platform b = list.get(i);
        int[] best = closestCells(a, b);
        int x0 = Math.min(best[0], best[2]);
        int x1 = Math.max(best[0], best[2]);
        int z0 = Math.min(best[1], best[3]);
        int z1 = Math.max(best[1], best[3]);
        int y0 = Math.min(a.topY(), b.topY()) + 1;
        int y1 = Math.max(a.topY(), b.topY()) + 3;
        for (int k = Math.max(0, i - 9); k <= Math.min(list.size() - 1, i + 9); k++) {
            Platform o = list.get(k);
            for (Placement pl : o.blocks()) {
                if (!obstacle(o, pl)) {
                    continue;
                }
                // The islands the jump starts from and lands on are not in the way below their surface.
                if ((o == a || o == b) && pl.y() <= o.topY()) {
                    continue;
                }
                if (pl.x() >= x0 && pl.x() <= x1 && pl.z() >= z0 && pl.z() <= z1 && pl.y() >= y0 && pl.y() <= y1) {
                    return String.format("%s of platform %d (%s, top %d) at %d %d %d; from %d %d top %d to %d %d top %d",
                            pl.block(), o.index(), o == a ? "start" : o == b ? "target" : "other", o.topY(),
                            pl.x(), pl.y(), pl.z(), best[0], best[1], a.topY(), best[2], best[3], b.topY());
                }
            }
        }
        return null;
    }

    /** Three blocks of room above every landing cell. */
    private static boolean headRoom(List<Platform> list, int i) {
        Platform p = list.get(i);
        for (int k = Math.max(0, i - 9); k <= Math.min(list.size() - 1, i + 9); k++) {
            Platform o = list.get(k);
            for (Placement pl : o.blocks()) {
                if (!obstacle(o, pl)) {
                    continue;
                }
                if (pl.y() >= p.topY() + 1 && pl.y() <= p.topY() + 3 && p.hasCell(pl.x(), pl.z())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int[] closestCells(Platform a, Platform b) {
        double best = Double.MAX_VALUE;
        int[] out = new int[4];
        for (int i = 0; i < a.cellCount(); i++) {
            for (int j = 0; j < b.cellCount(); j++) {
                double d = Math.hypot(a.cellX(i) - b.cellX(j), a.cellZ(i) - b.cellZ(j));
                if (d < best) {
                    best = d;
                    out = new int[] {a.cellX(i), a.cellZ(i), b.cellX(j), b.cellZ(j)};
                }
            }
        }
        return out;
    }

    private static boolean sharesBlocks(Platform a, Platform b) {
        Set<String> keys = new HashSet<>();
        for (Placement pl : a.blocks()) {
            keys.add(key(pl.x(), pl.y(), pl.z()));
        }
        for (Placement pl : b.blocks()) {
            if (keys.contains(key(pl.x(), pl.y(), pl.z()))) {
                return true;
            }
        }
        return false;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static String blockAt(Platform p, int x, int y, int z) {
        for (Placement pl : p.blocks()) {
            if (pl.x() == x && pl.y() == y && pl.z() == z) {
                return pl.block();
            }
        }
        return null;
    }

    private static Theme.Feature feature(Move m) {
        return switch (m) {
            case CLIMB, LADDER_JUMP, LADDER_DASH -> Theme.Feature.CLIMB;
            case BOUNCE -> Theme.Feature.BOUNCE;
            case DASH -> Theme.Feature.DASH;
            case DROP -> Theme.Feature.DROP;
            case JUMP -> null;
        };
    }

    /**
     * The ladder of a tower: on the far row of the island before, one piece per block from that island's
     * surface up to the tower's top block (not above the edge), each in front of a tower block, nothing else there.
     */
    private static String ladderProblem(Platform a, Platform b) {
        List<Placement> ladder = new ArrayList<>();
        for (Placement pl : b.blocks()) {
            if (pl.attached()) {
                ladder.add(pl);
            }
        }
        if (ladder.size() != b.topY() - a.topY()) {
            return ladder.size() + " pieces for a climb of " + (b.topY() - a.topY());
        }
        Set<String> solid = new HashSet<>();
        for (Placement pl : b.blocks()) {
            if (!pl.decor()) {
                solid.add(key(pl.x(), pl.y(), pl.z()));
            }
        }
        int z = ladder.get(0).z();
        for (Placement pl : ladder) {
            if (!pl.block().equals(b.theme().ladder()) || pl.facing() != Placement.EAST) {
                return "piece " + pl;
            }
            if (pl.x() != a.maxX() || pl.z() != z || !a.hasCell(pl.x(), pl.z())) {
                return "not on the far row of the island before: " + pl;
            }
            if (!solid.contains(key(pl.x() + 1, pl.y(), pl.z()))) {
                return "no tower block behind " + pl;
            }
            if (blockAt(a, pl.x(), pl.y(), pl.z()) != null) {
                return "the island before has a block there: " + pl;
            }
        }
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (Placement pl : ladder) {
            low = Math.min(low, pl.y());
            high = Math.max(high, pl.y());
        }
        if (low != a.topY() + 1 || high != b.topY()) {
            return "ladder from " + low + " to " + high + ", islands at " + a.topY() + " and " + b.topY();
        }
        // The player steps off the ladder onto the tower: no plant on the tower's near row.
        for (Placement pl : b.blocks()) {
            if (pl.decor() && !pl.attached() && pl.y() == b.topY() + 1 && pl.x() == b.minX() && b.hasCell(pl.x(), pl.z())) {
                return "plant where the ladder ends: " + pl;
            }
        }
        return null;
    }

    /**
     * A ladder hanging on a tower across a gap: straight ahead of the take-off island's far row, a tower block
     * behind every piece, up to the tower's top. A jump's ladder hangs from 2 below the surface and is within
     * reach of a jump; a dash's starts 2 above and is out of reach without a dash, and its take-off glows.
     */
    private static String ladderJumpProblem(Platform a, Platform b) {
        List<Placement> ladder = new ArrayList<>();
        Set<String> solid = new HashSet<>();
        for (Placement pl : b.blocks()) {
            if (pl.attached()) {
                ladder.add(pl);
            } else if (!pl.decor()) {
                solid.add(key(pl.x(), pl.y(), pl.z()));
            }
        }
        if (ladder.isEmpty()) {
            return "no ladder";
        }
        int x = ladder.get(0).x();
        int z = ladder.get(0).z();
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (Placement pl : ladder) {
            if (pl.x() != x || pl.z() != z || pl.facing() != Placement.EAST || !pl.block().equals(b.theme().ladder())) {
                return "piece " + pl;
            }
            if (!solid.contains(key(x + 1, pl.y(), z))) {
                return "no tower block behind " + pl;
            }
            low = Math.min(low, pl.y());
            high = Math.max(high, pl.y());
        }
        if (high != b.topY() || ladder.size() != high - low + 1 || x != b.minX() - 1) {
            return "ladder from " + low + " to " + high + " (" + ladder.size() + " pieces), tower top " + b.topY();
        }
        if (!a.hasCell(a.maxX(), z)) {
            return "not straight ahead of the take-off's far row";
        }
        int cellsPast = x - a.maxX();
        // The feet must be at least this high above where they took off for the body to touch the lowest piece.
        double feet = low - (a.topY() + 1) - JumpModel.PLAYER_HEIGHT;
        double need = JumpModel.travelToLadder(cellsPast);
        if (b.move() == Move.LADDER_JUMP) {
            if (cellsPast < 3 || cellsPast > 5 || low != a.topY() - 2) {
                return cellsPast + " cells past the edge, lowest piece " + (low - a.topY());
            }
            if (JumpModel.travelAbove(feet) * JumpModel.SAFETY < need) {
                return "out of reach of a jump";
            }
        } else {
            if (cellsPast != CourseGenerator.DASH_LADDER_CELLS || low != a.topY() + 3) {
                return cellsPast + " cells past the edge, lowest piece " + (low - a.topY());
            }
            if (JumpModel.travelAbove(feet) > need - 0.5) {
                return "a jump reaches it without a dash (" + JumpModel.travelAbove(feet) + " of " + need + ")";
            }
            double withDash = (JumpModel.travelAbove(feet) + JumpModel.DASH_GAIN) * JumpModel.SAFETY;
            if (withDash < need) {
                return "out of reach of a dash (" + withDash + " of " + need + ")";
            }
            for (int k = 0; k < a.cellCount(); k++) {
                if (a.cellX(k) == a.maxX() && !Themes.DASH_MARK.equals(blockAt(a, a.maxX(), a.topY(), a.cellZ(k)))) {
                    return "take-off far row does not glow";
                }
            }
        }
        return null;
    }

    /**
     * The mushroom of a bounce: on the far row of the island before (its own block, on the glowing bounce
     * mark), with room above for the whole bounce, and the target within reach of a bounce.
     */
    private static String bounceProblem(List<Platform> list, int i) {
        Platform a = list.get(i - 1);
        Platform b = list.get(i);
        Placement pad = null;
        for (Placement pl : b.blocks()) {
            if (pl.block().equals(Themes.BOUNCE_PAD)) {
                return "the target has a mushroom";
            }
        }
        for (Placement pl : a.blocks()) {
            if (pl.block().equals(Themes.BOUNCE_PAD)) {
                if (pad != null) {
                    return "two mushrooms";
                }
                pad = pl;
            }
        }
        if (pad == null) {
            return "no mushroom";
        }
        if (pad.x() != a.maxX() || !a.hasCell(pad.x(), pad.z()) || pad.y() != a.topY() + 1) {
            return "mushroom not on the far row of the island before: " + pad;
        }
        if (!Themes.BOUNCE_MARK.equals(blockAt(a, pad.x(), pad.y() - 1, pad.z()))) {
            return "the mushroom stands on " + blockAt(a, pad.x(), pad.y() - 1, pad.z()) + ", not on the glowing mark";
        }
        int[] bx = new int[b.cellCount()];
        int[] bz = new int[b.cellCount()];
        for (int k = 0; k < bx.length; k++) {
            bx[k] = b.cellX(k);
            bz[k] = b.cellZ(k);
        }
        double g = Platform.gap(new int[] {pad.x()}, new int[] {pad.z()}, bx, bz);
        double reach = JumpModel.maxBounceGap(b.topY() - a.topY() - 1);
        if (reach < 0 || g > reach - 0.25 + 1e-9) {
            return "target " + g + " away, a bounce reaches " + reach;
        }
        // Nothing to bump into above the mushroom, up to above the highest point of a real bounce.
        int top = pad.y() + 1 + (int) Math.ceil(JumpModel.bounceApex()) + 1;
        for (int k = Math.max(0, i - 9); k <= Math.min(list.size() - 1, i + 9); k++) {
            Platform o = list.get(k);
            for (Placement pl : o.blocks()) {
                if (pl != pad && obstacle(o, pl) && pl.x() == pad.x() && pl.z() == pad.z() && pl.y() > pad.y()
                        && pl.y() <= top) {
                    return pl.block() + " of platform " + o.index() + " above the mushroom";
                }
            }
        }
        return null;
    }

    /**
     * Both ends of a dash glow: the top two layers of the take-off island's far row and of the target's near
     * row are the dash mark (every surface cell there, and nothing else of the two islands).
     */
    private static String dashMarkProblem(Platform a, Platform b) {
        for (Platform p : List.of(a, b)) {
            int row = p == a ? p.maxX() : p.minX();
            for (int k = 0; k < p.cellCount(); k++) {
                if (p.cellX(k) == row && !Themes.DASH_MARK.equals(blockAt(p, row, p.topY(), p.cellZ(k)))) {
                    return (p == a ? "take-off" : "target") + " surface at " + row + " " + p.cellZ(k) + " is "
                            + blockAt(p, row, p.topY(), p.cellZ(k));
                }
            }
            for (Placement pl : p.blocks()) {
                if (pl.block().equals(Themes.DASH_MARK) && (pl.x() != row || pl.y() < p.topY() - 1)) {
                    return "mark outside the row at " + pl;
                }
                if (pl.decor() && pl.x() == row && pl.y() == p.topY() + 1 && p.hasCell(pl.x(), pl.z())) {
                    return "plant on the glowing row: " + pl.block();
                }
            }
        }
        return null;
    }

    /** Every area has its own special jumps, and they look as they should. */
    private static void specialJumps() {
        Map<String, Map<Move, Integer>> byArea = new TreeMap<>();
        int beams = 0;
        for (int s = 0; s < 120; s++) {
            List<Platform> list = course(20_000L + s, 0, LENGTH);
            for (Platform p : list) {
                byArea.computeIfAbsent(p.theme().name(), k -> new EnumMap<>(Move.class)).merge(p.move(), 1, Integer::sum);
                if (p.cellCount() >= 3 && p.maxZ() == p.minZ() && p.maxX() - p.minX() + 1 == p.cellCount()) {
                    beams++;
                    for (int k = 0; k < p.cellCount(); k++) {
                        check(p.theme().beam().names().contains(blockAt(p, p.cellX(k), p.topY(), p.cellZ(k)))
                                        || p.theme().top().names().contains(blockAt(p, p.cellX(k), p.topY(), p.cellZ(k))),
                                "beam made of the area's beam block");
                    }
                }
            }
        }
        System.out.println("Special jumps by area: " + byArea);
        check(beams > 0, "beams come up (" + beams + ")");
        for (Theme t : Themes.ALL) {
            Map<Move, Integer> m = byArea.getOrDefault(t.name(), Map.of());
            for (Theme.Feature f : t.features()) {
                if (f == Theme.Feature.BEAM) {
                    continue;
                }
                Move mv = switch (f) {
                    case CLIMB -> Move.CLIMB;
                    case BOUNCE -> Move.BOUNCE;
                    case DASH -> Move.DASH;
                    case DROP -> Move.DROP;
                    case BEAM -> Move.JUMP;
                };
                check(m.getOrDefault(mv, 0) >= 5, t.name() + " has its " + mv + " jumps (" + m.getOrDefault(mv, 0) + ")");
            }
            check(t.ladder() != null, t.name() + " has a ladder");
        }
        // Different areas feel different: few areas share the same special jumps, each has its own mix.
        Map<Set<Theme.Feature>, Integer> kinds = new HashMap<>();
        for (Theme t : Themes.ALL) {
            kinds.merge(t.features(), 1, Integer::sum);
        }
        for (Map.Entry<Set<Theme.Feature>, Integer> e : kinds.entrySet()) {
            check(e.getValue() <= 3, "at most three areas with the special jumps " + e.getKey() + " (" + e.getValue() + ")");
        }
        check(kinds.size() >= 8, "areas differ in their special jumps (" + kinds.size() + " kinds)");
    }

    /** After the green start, every area comes up at random, easy ones again too. */
    private static void randomAreas() {
        Map<String, Integer> second = new TreeMap<>();
        Map<String, Integer> seen = new TreeMap<>();
        int startAgain = 0;
        int sections = 0;
        for (int s = 0; s < 200; s++) {
            List<Platform> list = course(7000L + s, 0, 300);
            Theme secondArea = null;
            for (Platform p : list) {
                seen.merge(p.theme().name(), 1, Integer::sum);
                if (!p.sectionStart()) {
                    continue;
                }
                sections++;
                if (secondArea == null) {
                    secondArea = p.theme();
                    second.merge(secondArea.name(), 1, Integer::sum);
                }
                if (p.theme() == Themes.START) {
                    startAgain++;
                }
            }
        }
        System.out.println("Second area over 200 runs: " + second);
        System.out.printf("Start area again later: %d of %d area changes%n", startAgain, sections);
        check(seen.size() == Themes.ALL.size(), "every area shows up (" + seen.size() + ")");
        check(second.size() >= Themes.ALL.size() - 2, "the second area is random (" + second.size() + " kinds)");
        check(!second.containsKey(Themes.START.name()), "no area follows itself");
        check(startAgain > sections / 40, "the green start area comes back later (" + startAgain + "x)");
    }

    private static void determinism() {
        List<Platform> a = course(42, 0, 120);
        List<Platform> b = course(42, 0, 120);
        List<Platform> c = course(43, 0, 120);
        boolean same = true;
        boolean differs = false;
        for (int i = 0; i < a.size(); i++) {
            same &= a.get(i).blocks().equals(b.get(i).blocks()) && a.get(i).theme() == b.get(i).theme();
            differs |= !a.get(i).blocks().equals(c.get(i).blocks());
        }
        check(same, "same seed, same course");
        check(differs, "new seed, new course");
        Set<String> starts = new HashSet<>();
        for (int s = 0; s < 50; s++) {
            List<Platform> l = course(5000 + s, 0, 21);
            StringBuilder sig = new StringBuilder();
            for (Platform p : l) {
                sig.append(p.minX()).append(',').append(p.minZ()).append(',').append(p.topY()).append(';');
            }
            starts.add(sig.toString());
        }
        System.out.println("Distinct first 20 jumps over 50 runs: " + starts.size());
        check(starts.size() == 50, "every run starts differently");
    }

    /** The difficulty comes from the area: gaps, pillars and drops grow with the level. */
    private static void difficultyByLevel() {
        double[] gapSum = new double[11];
        double[] pillars = new double[11];
        double[] small = new double[11];
        double[] drops = new double[11];
        double[] side = new double[11];
        double[] deco = new double[11];
        int[] count = new int[11];
        // Over all platforms, special jumps too: jumps that ask a lot (a landing of one block, a gap of four
        // or more, a dash, a bounce or drop onto two blocks or fewer).
        double[] hard = new double[11];
        int[] all = new int[11];
        for (int s = 0; s < SEEDS; s++) {
            List<Platform> list = course(9000L + s, 0, LENGTH);
            for (int i = 1; i < list.size(); i++) {
                Platform b = list.get(i);
                if (b.sectionStart()) {
                    continue;
                }
                int level = b.theme().level();
                Move mv = b.move();
                double gp = list.get(i - 1).gapTo(b);
                all[level]++;
                if (mv == Move.DASH || mv == Move.LADDER_DASH
                        || (mv != Move.CLIMB && mv != Move.LADDER_JUMP && b.cellCount() == 1) || (mv == Move.JUMP && gp >= 3.9)
                        || ((mv == Move.BOUNCE || mv == Move.DROP) && b.cellCount() <= 2)) {
                    hard[level]++;
                }
                if (mv != Move.JUMP) {
                    continue;
                }
                gapSum[level] += list.get(i - 1).gapTo(b);
                pillars[level] += b.cellCount() == 1 ? 1 : 0;
                small[level] += b.cellCount() <= 2 ? 1 : 0;
                drops[level] += Math.max(0, list.get(i - 1).topY() - b.topY());
                int[] c = closestCells(list.get(i - 1), b);
                side[level] += c[1] != c[3] ? 1 : 0;
                deco[level] += b.boxes().size() > 1 ? 1 : 0;
                count[level]++;
            }
        }
        System.out.println("By level: mean gap / pillars / platforms of 1-2 blocks / mean drop / diagonal or sideways"
                + " / with a decoration island");
        double[] mean = new double[11];
        for (int level = 1; level <= 10; level++) {
            if (count[level] == 0) {
                check(false, "level " + level + " never used");
                continue;
            }
            mean[level] = gapSum[level] / count[level];
            System.out.printf("  L%-2d %.2f  %3.0f%%  %3.0f%%  %.2f  %3.0f%%  %3.0f%%  (%d jumps)%n", level, mean[level],
                    100 * pillars[level] / count[level], 100 * small[level] / count[level],
                    drops[level] / count[level], 100 * side[level] / count[level], 100 * deco[level] / count[level],
                    count[level]);
        }
        for (int level = 2; level <= 10; level++) {
            check(mean[level] >= mean[level - 1] - 0.08, "mean gap does not drop at level " + level);
            check(pillars[level] / count[level] >= pillars[level - 1] / count[level - 1] - 0.02,
                    "pillar share does not drop at level " + level);
        }
        check(mean[10] > mean[1] + 1.5, "the hardest area is clearly harder than the start");
        // With the special jumps, every level from 3 on is harder than the one before, up to about the old top.
        StringBuilder hardLine = new StringBuilder("Jumps that ask a lot, all ways:");
        for (int level = 1; level <= 10; level++) {
            hardLine.append(String.format(" L%d %.0f%%", level, 100 * hard[level] / Math.max(1, all[level])));
        }
        System.out.println(hardLine);
        for (int level = 3; level <= 10; level++) {
            double h = hard[level] / Math.max(1, all[level]);
            double before = hard[level - 1] / Math.max(1, all[level - 1]);
            check(h > before + 0.01, String.format("level %d harder than level %d (%.0f%% vs %.0f%%)", level, level - 1,
                    100 * h, 100 * before));
        }
        check(hard[10] / Math.max(1, all[10]) >= 0.66, "the top level stays hard with its special jumps");
        // Decoration islands come about as often in every area, hard or easy.
        double decoMin = 1;
        double decoMax = 0;
        for (int level = 1; level <= 10; level++) {
            double share = deco[level] / count[level];
            decoMin = Math.min(decoMin, share);
            decoMax = Math.max(decoMax, share);
        }
        check(decoMin >= 0.75 * decoMax, String.format("decoration islands as often in every level (%.0f%% to %.0f%%)",
                100 * decoMin, 100 * decoMax));
        check(pillars[1] == 0, "no one-block pillars in the first area");
    }

    private static void standing() {
        List<Platform> l = course(7, 0, 30);
        for (Platform p : l) {
            check(p.isStandingOn(p.spawnX(), p.topY() + 1.0, p.spawnZ()), "standing on the spawn point");
            check(!p.isStandingOn(p.spawnX(), p.topY() + 3.0, p.spawnZ()), "not standing high above");
            check(!p.isStandingOn(p.spawnX(), p.topY() - 0.5, p.spawnZ()), "not standing below");
            check(!p.isStandingOn(p.maxX() + 3.0, p.topY() + 1.0, p.spawnZ()), "not standing next to it");
        }
        // Edge: a player with half the body over the edge still counts.
        Platform p = l.get(0);
        check(p.isStandingOn(p.maxX() + 1.2, p.topY() + 1.0, p.spawnZ()), "standing on the edge");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static void check(boolean ok, String what, Map<String, Integer> problems) {
        if (!ok) {
            failures++;
            String key = what.replaceAll("[-0-9.]+", "#");
            int n = problems.merge(key, 1, Integer::sum);
            if (n <= 3) {
                System.out.println("FAIL: " + what);
            }
        }
    }
}
