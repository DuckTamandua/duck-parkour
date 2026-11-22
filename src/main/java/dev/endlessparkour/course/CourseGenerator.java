package dev.endlessparkour.course;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Builds an endless course of floating islands for one run. Pure logic without game calls,
 * so it can be tested on its own. The course runs towards +X and wanders within its lane.
 * Every run begins in the start area; after that the areas follow in random order, each
 * with the difficulty of its level.
 */
public final class CourseGenerator {

    private static final int RECENT = 10;
    /** Chance that a platform (pillars too) gets a decoration island (area starts always try one). */
    private static final double LANDMARK_CHANCE = 0.22;

    private enum Kind { START, GATE, NORMAL, PILLAR, TOWER, BEAM }

    /**
     * What sets a special island apart: how the player gets onto it, its depth (0: the usual), a row of
     * cells (by X) that gets no plants (Integer.MIN_VALUE: none), blocks it puts on the island before
     * (the ladder up a tower) or on itself (the mushroom of a bounce), and the cells whose top two layers
     * glow with the mark block: the row markX (Integer.MIN_VALUE: none), only the cell at markZ of it unless
     * markZ is Integer.MIN_VALUE.
     */
    private record Extra(Move move, int depth, int bareX, List<Placement> attached, int markX, int markZ, String mark) {
        static final Extra NONE = new Extra(Move.JUMP, 0, Integer.MIN_VALUE, List.of());

        Extra(Move move, int depth, int bareX, List<Placement> attached) {
            this(move, depth, bareX, attached, Integer.MIN_VALUE, Integer.MIN_VALUE, null);
        }
    }

    private final Random rng;
    private final List<Theme> themes;
    private final Tier[] levels;
    private final int laneZ;
    private final int baseY;
    private final int minY;
    private final int maxY;
    private final int sectionMin;
    private final int sectionMax;
    private final int maxDrift;
    private final Deque<Platform> recent = new ArrayDeque<>();
    /** The space the recent jumps pass through (see {@link #path}); nothing else may be built there. */
    private final Deque<int[]> paths = new ArrayDeque<>();
    private Theme theme;
    private int tint;
    private int nextSection;
    private int fallbacks;
    private int lastResorts;
    /** The island before was built for this special jump (its far row is kept bare); null: none. */
    private Theme.Feature pending;
    /** Platforms since the last special jump. */
    private int sinceFeature;
    /** Cells past the take-off island's edge of a ladder that takes a dash. */
    static final int DASH_LADDER_CELLS = 8;
    /** A dash target built together with its take-off island: the next platform handed out. */
    private Platform nextUp;

    public CourseGenerator(long seed, List<Theme> themes, Theme start, Tier[] levels, int laneZ, int baseY,
                           int minY, int maxY, int sectionMin, int sectionMax, int maxDrift) {
        if (themes.isEmpty()) {
            throw new IllegalArgumentException("no themes");
        }
        if (levels.length == 0) {
            throw new IllegalArgumentException("no levels");
        }
        this.rng = new Random(seed);
        this.themes = List.copyOf(themes);
        this.levels = new Tier[levels.length];
        for (int i = 0; i < levels.length; i++) {
            this.levels[i] = levels[i].sanitized();
        }
        this.laneZ = laneZ;
        this.minY = Math.min(minY, maxY - 10);
        this.maxY = maxY;
        this.baseY = Math.max(this.minY + 5, Math.min(this.maxY - 5, baseY));
        this.sectionMin = Math.max(2, Math.min(sectionMin, sectionMax));
        this.sectionMax = Math.max(this.sectionMin, sectionMax);
        this.maxDrift = Math.max(4, maxDrift);
        this.theme = start != null && this.themes.contains(start) ? start : this.themes.get(0);
        this.tint = pickTint(theme);
        this.nextSection = between(this.sectionMin, this.sectionMax);
    }

    /** How often no regular platform fitted and the plain safety platform was used. */
    public int fallbacks() {
        return fallbacks;
    }

    /** How often not even the safety platform found a free spot (it may then touch other islands). */
    public int lastResorts() {
        return lastResorts;
    }

    /** The jump settings of an area. */
    public Tier tierFor(Theme t) {
        int i = Math.max(0, Math.min(levels.length - 1, t.level() - 1));
        return levels[i];
    }

    /** The start island: a large rounded island with the area marker in the middle. */
    public Platform start(int x) {
        List<int[]> cells = new ArrayList<>();
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = (dx == 0 || dx == 4) && Math.abs(dz) == 2;
                if (!corner) {
                    cells.add(new int[] {x + dx, laneZ + dz});
                }
            }
        }
        Platform p = build(0, theme, baseY, cells, Kind.START, 0, 0, true, null);
        remember(p, null);
        return p;
    }

    /** The platform after prev. */
    public Platform next(Platform prev) {
        if (nextUp != null && nextUp.index() == prev.index() + 1) {
            Platform p = nextUp;
            nextUp = null;
            sinceFeature = 0;
            return p;
        }
        nextUp = null;
        int index = prev.index() + 1;
        boolean sectionStart = index == nextSection;
        if (sectionStart) {
            theme = nextTheme();
            tint = pickTint(theme);
            // Every area has its own random length.
            nextSection = index + between(sectionMin, sectionMax);
        }
        Tier tier = tierFor(theme);
        Theme.Feature second = pending;
        pending = null;
        if (second != null && !sectionStart) {
            for (int attempt = 0; attempt < 40; attempt++) {
                Platform p = secondStep(second, prev, index, tier);
                if (p != null) {
                    sinceFeature = 0;
                    return p;
                }
            }
        } else if (!sectionStart && index >= 3 && sinceFeature >= 2 && rng.nextDouble() < theme.featureChance()) {
            Theme.Feature f = theme.pickFeature(rng);
            for (int attempt = 0; attempt < 30 && f != null; attempt++) {
                Platform p = firstStep(f, prev, index, tier);
                if (p != null) {
                    sinceFeature = 0;
                    return p;
                }
            }
        }
        sinceFeature++;
        for (int attempt = 0; attempt < 80; attempt++) {
            Platform p = tryNext(prev, index, tier, sectionStart, attempt >= 50);
            if (p != null) {
                remember(p, path(prev, cellsOf(p), p.topY()));
                return p;
            }
        }
        // Safety net: the simplest 2x2 platform that fits, close ahead of the last one.
        fallbacks++;
        Kind kind = sectionStart ? Kind.GATE : Kind.NORMAL;
        for (int dy : new int[] {0, -1, 1, -2}) {
            int y = prev.topY() + dy;
            if (y < minY || y > maxY) {
                continue;
            }
            for (int g : new int[] {2, 1, 3}) {
                for (int shift : new int[] {0, -1, 1, -2, 2, -3, 3, -4, 4}) {
                    List<int[]> cells = rect(prev.maxX() + 1 + g, prev.minZ() + shift, 2, 2);
                    double gap = gap(prev, cells);
                    if (!JumpModel.feasible(gap, dy) || gap > tier.maxGapFor(dy) + 0.25) {
                        continue;
                    }
                    int[] path = path(prev, cells, y);
                    if (pathBlocked(path, prev)) {
                        continue;
                    }
                    Platform p = build(index, theme, y, cells, kind, dy, gap, false, path);
                    if (!clashes(p.boxes().get(0))) {
                        remember(p, path);
                        return p;
                    }
                }
            }
        }
        // Last resort: two blocks ahead at the same height, whatever is there.
        lastResorts++;
        List<int[]> cells = rect(prev.maxX() + 3, prev.minZ(), 2, 2);
        Platform p = build(index, theme, prev.topY(), cells, kind, 0, gap(prev, cells), false, null);
        remember(p, path(prev, cells, p.topY()));
        return p;
    }

    private Platform tryNext(Platform prev, int index, Tier tier, boolean sectionStart, boolean easy) {
        int w;
        int d;
        Kind kind = Kind.NORMAL;
        if (sectionStart) {
            w = 3;
            d = 3;
            kind = Kind.GATE;
        } else if (rng.nextDouble() < tier.pillarChance) {
            w = 1;
            d = 1;
            kind = Kind.PILLAR;
        } else {
            w = between(tier.minSize, tier.maxSize);
            d = between(tier.minSize, tier.maxSize);
            if (w == 1 && d == 1) {
                // One-block platforms only come from the pillar chance.
                if (rng.nextBoolean()) {
                    w = 2;
                } else {
                    d = 2;
                }
            }
        }

        int dy = pickDy(tier, prev.topY());
        if (easy) {
            dy = Math.max(-1, Math.min(dy, 0));
        }
        int maxGap = tier.maxGapFor(dy);
        if (easy) {
            maxGap = Math.min(maxGap, 2);
        }
        int minGap = Math.min(tier.minGap, maxGap);

        int prevD = prev.maxZ() - prev.minZ() + 1;
        int drift = (int) Math.round(prev.spawnZ() - laneZ);
        int toCenter = drift > 0 ? -1 : 1;
        int sideDir = rng.nextBoolean() ? 1 : -1;
        if (Math.abs(drift) >= maxDrift || (Math.abs(drift) > maxDrift / 2 && rng.nextDouble() < 0.7)) {
            sideDir = toCenter;
        }

        int nMinX;
        int nMinZ;
        boolean sideways = !sectionStart && rng.nextDouble() < tier.sideChance * 0.2;
        if (sideways) {
            // A jump to the side: the X ranges overlap, the gap is along Z.
            int gz = between(minGap, maxGap);
            nMinX = between(prev.minX() - w + 1, prev.maxX());
            nMinZ = sideDir > 0 ? prev.maxZ() + 1 + gz : prev.minZ() - gz - d;
        } else {
            int gx = between(minGap, maxGap);
            nMinX = prev.maxX() + 1 + gx;
            int shift;
            if (rng.nextDouble() < tier.sideChance) {
                shift = sideDir * between(2, 3);
            } else {
                shift = between(-1, 1);
                if (Math.abs(drift) >= maxDrift) {
                    shift = toCenter;
                }
            }
            nMinZ = prev.minZ() + (prevD - d) / 2 + shift;
        }

        List<int[]> cells = shape(nMinX, nMinZ, w, d, kind == Kind.NORMAL);
        double gap = gap(prev, cells);
        if (gap < Math.max(1.0, minGap) - 1e-9 || gap > maxGap + 0.25 || !JumpModel.feasible(gap, dy)) {
            return null;
        }
        // Nothing of the recent platforms may stand in the way of this jump, and there must be room
        // ahead for the next one (else the course runs into a dead end beside an older island).
        int[] path = path(prev, cells, prev.topY() + dy);
        if (pathBlocked(path, prev) || !roomAhead(cells, prev.topY() + dy, tier)) {
            return null;
        }
        Platform p = build(index, theme, prev.topY() + dy, cells, kind, dy, gap, !easy, path);
        return clashes(p.boxes().get(0)) ? null : p;
    }

    // ------------------------------------------------------------------ special jumps

    /** The first island of a special jump: the whole jump, or the island a ladder or bounce starts from. */
    private Platform firstStep(Theme.Feature f, Platform prev, int index, Tier tier) {
        return switch (f) {
            case BEAM -> beam(prev, index, tier);
            case DROP -> drop(prev, index, tier);
            case DASH -> dashPair(prev, index, tier);
            case BOUNCE -> bouncePair(prev, index, tier);
            case CLIMB -> climb(prev, index, tier);
        };
    }

    /** A long beam, one block wide, along the course: long on easy levels, short on hard ones. */
    private Platform beam(Platform prev, int index, Tier tier) {
        int level = theme.level();
        int dy = Math.max(-1, Math.min(1, pickDy(tier, prev.topY())));
        int maxGap = tier.maxGapFor(dy);
        int g = between(Math.min(tier.minGap, maxGap), maxGap);
        int z = midZ(prev, 1) + between(-1, 1);
        int length = level <= 4 ? between(4, 5) : level <= 7 ? between(3, 4) : 3;
        List<int[]> cells = rect(prev.maxX() + 1 + g, z, length, 1);
        double gap = gap(prev, cells);
        if (gap > maxGap + 0.25 || !JumpModel.feasible(gap, dy)) {
            return null;
        }
        int topY = prev.topY() + dy;
        return finish(prev, index, cells, topY, Kind.BEAM, path(prev, cells, topY), Extra.NONE, false, tier);
    }

    /** An island far below: a drop of 5 to 9 blocks. */
    private Platform drop(Platform prev, int index, Tier tier) {
        int depth = theme.level() >= 6 ? between(6, 9) : between(5, 8);
        int topY = prev.topY() - depth;
        if (topY < minY) {
            return null;
        }
        int g = between(1, theme.level() >= 6 ? 3 : 2);
        int[] size = size(tier, 1, false);
        int w = size[0];
        int d = size[1];
        List<int[]> cells = shape(prev.maxX() + 1 + g, midZ(prev, d) + between(-1, 1), w, d, true);
        double gap = gap(prev, cells);
        if (gap > g + 0.25 || !JumpModel.feasible(gap, -depth)) {
            return null;
        }
        return finish(prev, index, cells, topY, Kind.NORMAL, path(prev, cells, topY),
                new Extra(Move.DROP, 0, Integer.MIN_VALUE, List.of()), false, tier);
    }

    /**
     * A gap too long for a jump, even with a grab at the edge: it takes a dash with the sword in the air.
     * Level only (a drop would give the jump more time in the air).
     */
    private Platform dash(Platform prev, int index, Tier tier) {
        int dy = 0;
        int level = theme.level();
        int g = 7;
        // Two deep along the dash (it moves a fixed distance, so a one-deep landing is luck), narrower when hard.
        int w = level <= 6 ? between(2, 3) : 2;
        int d = level <= 6 ? between(2, 3) : level <= 8 ? between(1, 2) : 1;
        List<int[]> cells = shape(prev.maxX() + 1 + g, midZ(prev, d) + between(-1, 1), w, d, true);
        double gap = gap(prev, cells);
        if (gap <= JumpModel.maxGapNoDash(dy) || gap > JumpModel.maxDashGap(dy) - 0.25) {
            return null;
        }
        int topY = prev.topY() + dy;
        int nearX = Integer.MAX_VALUE;
        for (int[] c : cells) {
            nearX = Math.min(nearX, c[0]);
        }
        return finish(prev, index, cells, topY, Kind.NORMAL, path(prev, cells, topY),
                new Extra(Move.DASH, 0, nearX, List.of(), nearX, Integer.MIN_VALUE, Themes.DASH_MARK), false, tier);
    }

    /**
     * A dash jump with both ends marked: a take-off island whose far row glows, and the dash target after
     * it whose near row glows. Built together (or not at all), so a glowing row always means a dash; the
     * target is handed out by the next call. Never across an area start.
     */
    private Platform dashPair(Platform prev, int index, Tier tier) {
        if (index + 1 >= nextSection) {
            return null;
        }
        Platform takeOff = launchIsland(prev, index, tier, Theme.Feature.DASH);
        if (takeOff == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            Platform target = dash(takeOff, index + 1, tier);
            if (target != null) {
                nextUp = target;
                return takeOff;
            }
        }
        forget(takeOff);
        return null;
    }

    /**
     * A bounce with its mushroom marked: the island with the mushroom on its far row (on a glowing block),
     * and the higher island after it. Built together (or not at all); the target is handed out by the next
     * call. Never across an area start.
     */
    private Platform bouncePair(Platform prev, int index, Tier tier) {
        if (index + 1 >= nextSection) {
            return null;
        }
        Platform pad = launchIsland(prev, index, tier, Theme.Feature.BOUNCE);
        if (pad == null) {
            return null;
        }
        int padZ = Integer.MIN_VALUE;
        for (Placement b : pad.blocks()) {
            if (b.block().equals(Themes.BOUNCE_PAD)) {
                padZ = b.z();
            }
        }
        for (int attempt = 0; attempt < 12 && padZ != Integer.MIN_VALUE; attempt++) {
            Platform target = bounce(pad, index + 1, tier, padZ);
            if (target != null) {
                nextUp = target;
                return pad;
            }
        }
        forget(pad);
        return null;
    }

    /**
     * A ladder jump of the area, harder with the level: walk up to a tower right next to the island (always
     * on levels 1-3, less later), jump at a ladder hanging on a tower across a gap (from level 4), or dash at
     * it (from level 7, in areas with dash jumps).
     */
    private Platform climb(Platform prev, int index, Tier tier) {
        int level = theme.level();
        double r = rng.nextDouble();
        if (level >= 7 && r < 0.3 && theme.features().contains(Theme.Feature.DASH)) {
            return ladderDashPair(prev, index, tier);
        }
        if (level >= 4 && r < (level >= 7 ? 0.7 : 0.5)) {
            return ladderTower(prev, index, tier, false);
        }
        return launch(Theme.Feature.CLIMB, prev, index, tier);
    }

    /**
     * A tower across a gap with a ladder hanging on its near side: jump (or dash) at it, grab it in the air
     * and climb up. For a jump the ladder starts 2 below the island's surface (a jump a bit short still
     * catches it lower); for a dash it starts 2 above, 8 cells past the edge, out of reach of a jump.
     */
    private Platform ladderTower(Platform prev, int index, Tier tier, boolean dash) {
        if (theme.ladder() == null) {
            return null;
        }
        int level = theme.level();
        int h = level <= 6 ? between(6, 7) : between(7, 8);
        int topY = prev.topY() + h;
        if (topY > maxY) {
            return null;
        }
        int cellsPast = dash ? DASH_LADDER_CELLS : level <= 6 ? between(3, 4) : between(4, 5);
        int ladderX = prev.maxX() + cellsPast;
        // Straight ahead of a cell of the island's far row.
        List<Integer> row = new ArrayList<>();
        for (int i = 0; i < prev.cellCount(); i++) {
            if (prev.cellX(i) == prev.maxX()) {
                row.add(prev.cellZ(i));
            }
        }
        int ladderZ = row.get(rng.nextInt(row.size()));
        int x0 = ladderX + 1;
        int tw = level <= 6 ? 2 : 1;
        int td = level <= 8 ? 2 : 1;
        List<int[]> cells = rect(x0, ladderZ - rng.nextInt(td), tw, td);
        int low = dash ? prev.topY() + 3 : prev.topY() - 2;
        List<Placement> ladder = new ArrayList<>();
        for (int y = low; y <= topY; y++) {
            ladder.add(Placement.attached(ladderX, y, ladderZ, theme.ladder(), Placement.EAST));
        }
        int[] path = {prev.maxX(), x0, Math.min(low, prev.topY() + 1), topY + 3, ladderZ, ladderZ};
        // Deep enough that the tower stands behind every piece of the ladder.
        int depth = topY - Math.min(low, prev.topY()) + 1 + between(1, 2);
        return finish(prev, index, cells, topY, Kind.TOWER, path,
                new Extra(dash ? Move.LADDER_DASH : Move.LADDER_JUMP, depth, x0, ladder), false, tier);
    }

    /** A dash at a ladder: a take-off island whose far row glows, then the tower with the ladder far ahead. */
    private Platform ladderDashPair(Platform prev, int index, Tier tier) {
        if (index + 1 >= nextSection) {
            return null;
        }
        Platform takeOff = launchIsland(prev, index, tier, Theme.Feature.DASH);
        if (takeOff == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            Platform target = ladderTower(takeOff, index + 1, tier, true);
            if (target != null) {
                nextUp = target;
                return takeOff;
            }
        }
        forget(takeOff);
        return null;
    }

    /** An island with a bare far row for the ladder of the next platform. */
    private Platform launch(Theme.Feature f, Platform prev, int index, Tier tier) {
        Platform p = launchIsland(prev, index, tier, f);
        if (p != null) {
            pending = f;
        }
        return p;
    }

    /**
     * The island a special jump starts from: the level's size and gap, a bare far row, and for a dash the
     * far row glowing.
     */
    private Platform launchIsland(Platform prev, int index, Tier tier, Theme.Feature f) {
        int dy = Math.max(-1, Math.min(1, pickDy(tier, prev.topY())));
        int maxGap = tier.maxGapFor(dy);
        int g = between(Math.min(tier.minGap, maxGap), maxGap);
        // A bounce needs a cell to land on before the mushroom; a ladder can stand on a pillar on hard levels.
        int[] size = f == Theme.Feature.BOUNCE ? size(tier, 2, false) : size(tier, 1, theme.level() >= 8);
        int w = size[0];
        int d = size[1];
        int x0 = prev.maxX() + 1 + g;
        List<int[]> cells = rect(x0, midZ(prev, d) + between(-1, 1), w, d);
        double gap = gap(prev, cells);
        if (gap > maxGap + 0.25 || !JumpModel.feasible(gap, dy)) {
            return null;
        }
        int topY = prev.topY() + dy;
        int farX = x0 + w - 1;
        int z0 = cells.get(0)[1];
        Extra extra = switch (f) {
            case DASH -> new Extra(Move.JUMP, 0, farX, List.of(), farX, Integer.MIN_VALUE, Themes.DASH_MARK);
            case BOUNCE -> {
                // The mushroom stands on the far row, on a glowing block, so it is easy to see.
                int padZ = z0 + between(0, d - 1);
                yield new Extra(Move.JUMP, 0, farX, List.of(Placement.decor(farX, topY + 1, padZ, Themes.BOUNCE_PAD)),
                        farX, padZ, Themes.BOUNCE_MARK);
            }
            default -> new Extra(Move.JUMP, 0, farX, List.of());
        };
        return finish(prev, index, cells, topY, Kind.NORMAL, path(prev, cells, topY), extra, false, tier);
    }

    /** The second island of a ladder or bounce jump; null when it does not fit (then a normal jump follows). */
    private Platform secondStep(Theme.Feature f, Platform prev, int index, Tier tier) {
        List<Integer> spots = new ArrayList<>();
        for (int i = 0; i < prev.cellCount(); i++) {
            if (prev.cellX(i) == prev.maxX() && free(prev, prev.maxX(), prev.topY() + 1, prev.cellZ(i))) {
                spots.add(prev.cellZ(i));
            }
        }
        if (spots.isEmpty()) {
            return null;
        }
        int z = spots.get(rng.nextInt(spots.size()));
        return tower(prev, index, tier, z);
    }

    /**
     * A tower right next to the island before with a ladder up its side, from that island's surface up to
     * the tower's top block (the player gets off at the top on their own).
     */
    private Platform tower(Platform prev, int index, Tier tier, int ladderZ) {
        if (theme.ladder() == null) {
            return null;
        }
        int level = theme.level();
        // Higher than a jump and a pull-up at the edge reach, so the ladder is the only way up.
        int h = level <= 3 ? 6 : level <= 6 ? between(6, 7) : between(7, 8);
        h = Math.max((int) Math.floor(JumpModel.MANTLE_HEIGHT) + 2, Math.min(h, JumpModel.MAX_CLIMB));
        int topY = prev.topY() + h;
        if (topY > maxY) {
            return null;
        }
        int x0 = prev.maxX() + 1;
        // The top: 2 x 2 on easy levels, 1 x 2 from level 7, a single block from level 9.
        int tw = level <= 6 ? 2 : 1;
        int td = level <= 8 ? 2 : 1;
        List<int[]> cells = rect(x0, ladderZ - rng.nextInt(td), tw, td);
        List<Placement> ladder = new ArrayList<>();
        for (int y = prev.topY() + 1; y <= topY; y++) {
            ladder.add(Placement.attached(prev.maxX(), y, ladderZ, theme.ladder(), Placement.EAST));
        }
        int[] path = {prev.maxX(), x0, prev.topY() + 1, topY + 3, ladderZ, ladderZ};
        return finish(prev, index, cells, topY, Kind.TOWER, path,
                new Extra(Move.CLIMB, h + between(2, 3), x0, ladder), true, tier);
    }

    /**
     * An island 5 or 6 blocks higher, reached with a bounce off a mushroom on the island before: higher than
     * a jump and a pull-up at the edge reach.
     */
    private Platform bounce(Platform prev, int index, Tier tier, int padZ) {
        int rise = theme.level() <= 6 ? 5 : between(5, 6);
        if (rise <= JumpModel.MANTLE_HEIGHT) {
            return null;
        }
        int topY = prev.topY() + rise;
        if (topY > maxY) {
            return null;
        }
        int padX = prev.maxX();
        int level = theme.level();
        int[] size = size(tier, 1, level >= 9);
        int w = size[0];
        int d = size[1];
        int x0 = padX + 1 + (level <= 4 ? between(1, 2) : level <= 7 ? between(1, 3) : between(2, 3));
        List<int[]> cells = shape(x0, padZ - d / 2 + between(-1, 1), w, d, true);
        int[] xs = new int[cells.size()];
        int[] zs = new int[cells.size()];
        int minZ = padZ;
        int maxZ = padZ;
        for (int i = 0; i < cells.size(); i++) {
            xs[i] = cells.get(i)[0];
            zs[i] = cells.get(i)[1];
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        // The feet stand on the mushroom one block above the island and must get one above the target.
        double reach = JumpModel.maxBounceGap(rise - 1);
        if (reach < 0 || Platform.gap(new int[] {padX}, new int[] {padZ}, xs, zs) > reach - 0.25) {
            return null;
        }
        int apex = prev.topY() + 2 + (int) Math.ceil(JumpModel.bounceApex()) + 1;
        int[] path = {padX, x0, prev.topY() + 1, Math.max(topY + 3, apex), minZ, maxZ};
        return finish(prev, index, cells, topY, Kind.NORMAL, path,
                new Extra(Move.BOUNCE, 0, Integer.MIN_VALUE, List.of()), true, tier);
    }

    /**
     * Builds a special island and keeps it when it fits: nothing in the way of its jump, room ahead, no clash.
     * Beside: it stands right next to the island before (a tower) or puts a block on it (ladder, mushroom),
     * so that island and the jump onto it do not count as a clash.
     */
    private Platform finish(Platform prev, int index, List<int[]> cells, int topY, Kind kind, int[] path, Extra extra,
                            boolean beside, Tier tier) {
        if (pathBlocked(path, prev) || !roomAhead(cells, topY, tier)) {
            return null;
        }
        Platform p = build(index, theme, topY, cells, kind, topY - prev.topY(), gap(prev, cells), true, path, extra);
        if (beside ? clashesBeside(p.boxes().get(0), prev) : clashes(p.boxes().get(0))) {
            return null;
        }
        remember(p, path);
        return p;
    }

    /**
     * Side lengths {along X, along Z} of a special island from the level's platform sizes, at least minX
     * along X; a single block only when pillarOk.
     */
    private int[] size(Tier tier, int minX, boolean pillarOk) {
        int w = Math.max(minX, between(tier.minSize, tier.maxSize));
        int d = between(tier.minSize, tier.maxSize);
        if (w == 1 && d == 1 && !pillarOk) {
            d = 2;
        }
        return new int[] {w, d};
    }

    /** Z of an island of depth d set in the middle of prev, pulled back towards the lane when it drifted far. */
    private int midZ(Platform prev, int d) {
        int prevD = prev.maxZ() - prev.minZ() + 1;
        int z = prev.minZ() + (prevD - d) / 2;
        int drift = (int) Math.round(prev.spawnZ() - laneZ);
        if (Math.abs(drift) > maxDrift / 2) {
            z += drift > 0 ? -1 : 1;
        }
        return z;
    }

    /** True when the island has nothing at this spot (no plant or other decoration). */
    private static boolean free(Platform p, int x, int y, int z) {
        for (Placement b : p.blocks()) {
            if (b.x() == x && b.y() == y && b.z() == z) {
                return false;
            }
        }
        return true;
    }

    private int pickDy(Tier t, int y) {
        double r = rng.nextDouble();
        int dy = 0;
        if (r < t.upChance) {
            dy = 1;
        } else if (r < t.upChance + t.downChance && t.maxDrop > 0) {
            dy = -(1 + rng.nextInt(t.maxDrop));
        }
        int band = Math.max(6, (maxY - minY) / 4);
        if (y < baseY - band && dy < 0) {
            dy = rng.nextDouble() < 0.7 ? 1 : 0;
        }
        if (y > baseY + band && dy > 0) {
            dy = rng.nextDouble() < 0.7 ? -1 : 0;
        }
        if (y + dy > maxY) {
            dy = -1;
        }
        if (y + dy < minY) {
            dy = 1;
        }
        return dy;
    }

    // ------------------------------------------------------------------ islands

    /** Footprint: a rectangle, for bigger islands with cut corners and bulges for a natural look. */
    private List<int[]> shape(int x0, int z0, int w, int d, boolean organic) {
        List<int[]> cells = rect(x0, z0, w, d);
        if (!organic || w < 3 || d < 3) {
            return cells;
        }
        int[][] corners = {{x0, z0}, {x0 + w - 1, z0}, {x0, z0 + d - 1}, {x0 + w - 1, z0 + d - 1}};
        List<int[]> order = new ArrayList<>(Arrays.asList(corners));
        Collections.shuffle(order, rng);
        int cuts = rng.nextInt(3);
        for (int i = 0; i < cuts; i++) {
            int[] k = order.get(i);
            cells.removeIf(c -> c[0] == k[0] && c[1] == k[1]);
        }
        // Bulges on the long sides (not on the front and back, which set the jump gaps).
        int bulges = rng.nextInt(3);
        for (int i = 0; i < bulges; i++) {
            int bx = x0 + 1 + rng.nextInt(Math.max(1, w - 2));
            int bz = rng.nextBoolean() ? z0 - 1 : z0 + d;
            if (!contains(cells, new int[] {bx, bz})) {
                cells.add(new int[] {bx, bz});
            }
        }
        return cells;
    }

    private Platform build(int index, Theme th, int topY, List<int[]> top, Kind kind, int dy, double gap,
                           boolean decorate, int[] path) {
        return build(index, th, topY, top, kind, dy, gap, decorate, path, Extra.NONE);
    }

    private Platform build(int index, Theme th, int topY, List<int[]> top, Kind kind, int dy, double gap,
                           boolean decorate, int[] path, Extra extra) {
        List<Placement> solid = new ArrayList<>();
        List<Placement> decor = new ArrayList<>();
        boolean marked = kind == Kind.START || kind == Kind.GATE;
        int[] center = closestToCenter(top);
        int maxDepth = extra.depth() > 0 ? extra.depth() : switch (kind) {
            case START -> 5;
            case GATE -> 4;
            case PILLAR -> 3 + rng.nextInt(3);
            case TOWER -> 5;
            case BEAM -> 1;
            case NORMAL -> Math.min(5, 2 + top.size() / 3) + rng.nextInt(2);
        };
        body(th, topY, top, kind, maxDepth, marked ? center : null, solid, decor);

        // Plants on the surface.
        if (kind != Kind.PILLAR && kind != Kind.BEAM && th.plantChance() > 0) {
            for (int[] c : top) {
                if ((marked && c[0] == center[0] && c[1] == center[1]) || c[0] == extra.bareX()) {
                    continue;
                }
                if (rng.nextDouble() < th.plantChance()) {
                    String ground = blockAt(solid, c[0], topY, c[1]);
                    String plant = plantFor(th, ground);
                    if (plant != null) {
                        decor.add(Placement.decor(c[0], topY + 1, c[1], plant));
                    }
                }
            }
        }

        decor.addAll(extra.attached());
        if (extra.markX() != Integer.MIN_VALUE) {
            // The mark of a dash or bounce: the top two layers of that row (or cell) glow.
            for (int i = 0; i < solid.size(); i++) {
                Placement b = solid.get(i);
                if (b.x() == extra.markX() && (extra.markZ() == Integer.MIN_VALUE || b.z() == extra.markZ())
                        && b.y() >= topY - 1) {
                    solid.set(i, Placement.solid(b.x(), b.y(), b.z(), extra.mark()));
                }
            }
        }

        List<int[]> boxes = new ArrayList<>();
        boxes.add(box(solid, decor, topY + 3));

        // A decoration island beside the course: a vanilla tree or ruin pillars of the area. Pillars get
        // them too, so areas with many pillars have as many decoration islands as the others.
        if (decorate && (marked || rng.nextDouble() < LANDMARK_CHANCE)) {
            double cx = 0;
            double cz = 0;
            for (int[] c : top) {
                cx += c[0] + 0.5;
                cz += c[1] + 0.5;
            }
            landmark(th, topY, cx / top.size(), cz / top.size(), boxes, path, solid, decor);
        }

        int[] xs = new int[top.size()];
        int[] zs = new int[top.size()];
        for (int i = 0; i < top.size(); i++) {
            xs[i] = top.get(i)[0];
            zs[i] = top.get(i)[1];
        }
        List<Placement> all = new ArrayList<>(solid.size() + decor.size());
        all.addAll(solid);
        all.addAll(decor);
        int bottomY = topY;
        for (Placement pl : all) {
            bottomY = Math.min(bottomY, pl.y());
        }
        return new Platform(index, th, topY, bottomY, xs, zs, all, boxes, marked && index > 0, dy, gap, tint,
                extra.move());
    }

    /**
     * The island body: every column goes down from the surface, deepest in the middle, so the
     * island looks like an upside-down cone, with a stalactite hanging under some columns.
     */
    private void body(Theme th, int topY, List<int[]> top, Kind kind, int maxDepth, int[] marker,
                      List<Placement> solid, List<Placement> decor) {
        double cx = 0;
        double cz = 0;
        for (int[] c : top) {
            cx += c[0] + 0.5;
            cz += c[1] + 0.5;
        }
        cx /= top.size();
        cz /= top.size();
        double radius = 0.5;
        for (int[] c : top) {
            radius = Math.max(radius, Math.hypot(c[0] + 0.5 - cx, c[1] + 0.5 - cz));
        }
        for (int[] c : top) {
            double t = Math.hypot(c[0] + 0.5 - cx, c[1] + 0.5 - cz) / (radius + 0.6);
            int depth;
            if (kind == Kind.PILLAR || kind == Kind.TOWER || kind == Kind.BEAM) {
                depth = maxDepth;
            } else {
                depth = (int) Math.round(1 + (maxDepth - 1) * (1 - t * t)) + rng.nextInt(3) - 1;
                depth = Math.max(top.size() <= 2 ? 2 : 1, Math.min(maxDepth + 1, depth));
                if (t < 0.25 && top.size() >= 4) {
                    depth += 1 + rng.nextInt(2);
                }
            }
            for (int k = 0; k < depth; k++) {
                int y = topY - k;
                boolean floor = k < depth - 1;
                String block;
                if (k == 0) {
                    if (marker != null && c[0] == marker[0] && c[1] == marker[1]) {
                        block = th.marker().pick(rng);
                    } else {
                        block = withFloor(kind == Kind.BEAM ? th.beam() : th.top(), floor, th.fill());
                    }
                } else if ((kind == Kind.PILLAR || kind == Kind.TOWER) && k < depth - 1) {
                    block = withFloor(th.pillar(), floor, th.bottom());
                } else if (!th.accent().isEmpty() && rng.nextDouble() < th.accentChance()) {
                    block = withFloor(th.accent(), floor, th.bottom());
                } else if (k == 1) {
                    block = withFloor(th.fill(), floor, th.bottom());
                } else {
                    block = withFloor(th.bottom(), floor, th.fill());
                }
                solid.add(Placement.solid(c[0], y, c[1], block));
            }
            int bottom = topY - depth + 1;
            if (kind != Kind.BEAM && !th.hanging().isEmpty()
                    && rng.nextDouble() < th.hangingChance() * (depth >= maxDepth ? 1.0 : 0.5)) {
                decor.add(Placement.hanging(c[0], bottom - 1, c[1], th.hanging().pick(rng)));
            }
        }
    }

    /** A decoration island 8 to 11 blocks beside the course with a tree (or ruin pillars). */
    private void landmark(Theme th, int topY, double cx, double cz, List<int[]> boxes, int[] path,
                          List<Placement> solidOut, List<Placement> decorOut) {
        if (th.trees().isEmpty()) {
            return;
        }
        int first = rng.nextBoolean() ? 1 : -1;
        for (int side : new int[] {first, -first}) {
            int lx = (int) Math.floor(cx) + between(-2, 2);
            int lz = (int) Math.floor(cz) + side * between(8, 11);
            int ly = Math.max(minY + 4, Math.min(maxY + 4, topY + between(-3, 2)));
            List<int[]> cells = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double dist = Math.hypot(dx, dz);
                    if (dist <= 1.5 || (dist <= 2.3 && rng.nextDouble() < 0.7)) {
                        cells.add(new int[] {lx + dx, lz + dz});
                    }
                }
            }
            List<Placement> solid = new ArrayList<>();
            List<Placement> decor = new ArrayList<>();
            body(th, ly, cells, Kind.NORMAL, 3 + rng.nextInt(2), null, solid, decor);
            // Ground cover and clutter.
            for (int[] c : cells) {
                if (c[0] == lx && c[1] == lz) {
                    continue;
                }
                double r = rng.nextDouble();
                if (r < th.plantChance()) {
                    String plant = plantFor(th, blockAt(solid, c[0], ly, c[1]));
                    if (plant != null) {
                        decor.add(Placement.decor(c[0], ly + 1, c[1], plant));
                    }
                } else if (r < th.plantChance() + 0.12 && !th.scatter().isEmpty()) {
                    decor.add(Placement.decor(c[0], ly + 1, c[1], th.scatter().pick(rng)));
                }
            }
            decor.removeIf(p -> p.y() == ly + 1 && p.x() == lx && p.z() == lz);
            Theme.Tree tree = th.trees().get(rng.nextInt(th.trees().size()));
            if (tree.leaves() != null) {
                int h = between(3, 5);
                for (int i = 1; i <= h; i++) {
                    solid.add(Placement.solid(lx, ly + i, lz, tree.trunk()));
                }
                int crown = ly + h;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx == 0 && dz == 0 && dy <= 0) {
                                continue;
                            }
                            double dist = Math.sqrt(dx * dx + dz * dz + (dy - 0.5) * (dy - 0.5) * 1.6);
                            if (dist <= 2.4 && rng.nextDouble() < 0.88) {
                                int y = crown + dy;
                                decor.removeIf(p -> p.x() == lx && p.z() == lz && p.y() == y);
                                final int fx = lx + dx;
                                final int fz = lz + dz;
                                // Leaves never replace ground plants or clutter.
                                if (blockAt(decor, fx, y, fz) == null) {
                                    decor.add(Placement.decor(fx, y, fz, tree.leaves()));
                                    // No plant right under low leaves: tall plants need the block above and break.
                                    decor.removeIf(q -> q.x() == fx && q.z() == fz && q.y() == y - 1
                                            && !q.block().equals(tree.leaves()));
                                }
                            }
                        }
                    }
                }
            } else {
                // Ruin: a few pillars of the area's stone with a cap.
                List<int[]> spots = new ArrayList<>(cells);
                Collections.shuffle(spots, rng);
                int pillars = 2 + rng.nextInt(2);
                for (int i = 0; i < pillars && i < spots.size(); i++) {
                    int[] s = spots.get(i);
                    decor.removeIf(p -> p.x() == s[0] && p.z() == s[1] && p.y() == ly + 1);
                    int hh = between(2, 4);
                    for (int k = 1; k <= hh; k++) {
                        solid.add(Placement.solid(s[0], ly + k, s[1], tree.trunk()));
                    }
                    solid.add(Placement.solid(s[0], ly + hh + 1, s[1], th.marker().pick(rng)));
                }
            }
            int[] b = box(solid, decor, Integer.MIN_VALUE);
            if (!clashes(b) && !overlaps(b, boxes.get(0), 2) && (path == null || !overlaps(b, path, 1))) {
                solidOut.addAll(solid);
                decorOut.addAll(decor);
                boxes.add(b);
                return;
            }
        }
    }

    private String plantFor(Theme th, String surface) {
        boolean soil = surface != null && Themes.SOIL.contains(surface);
        boolean canSoil = soil && !th.soilPlants().isEmpty();
        boolean canAny = surface != null && !th.anyPlants().isEmpty();
        if (canSoil && canAny) {
            return rng.nextBoolean() ? th.soilPlants().pick(rng) : th.anyPlants().pick(rng);
        }
        if (canSoil) {
            return th.soilPlants().pick(rng);
        }
        if (canAny) {
            return th.anyPlants().pick(rng);
        }
        return null;
    }

    /** Picks a block; blocks that need a floor are swapped out where nothing is under them. */
    private String withFloor(Pick pick, boolean floor, Pick fallback) {
        String b = pick.pick(rng);
        if (floor || !Themes.NEEDS_FLOOR.contains(b)) {
            return b;
        }
        for (int i = 0; i < 6; i++) {
            String again = pick.pick(rng);
            if (!Themes.NEEDS_FLOOR.contains(again)) {
                return again;
            }
        }
        for (String n : fallback.names()) {
            if (!Themes.NEEDS_FLOOR.contains(n)) {
                return n;
            }
        }
        return "Rock_Stone";
    }

    // ------------------------------------------------------------------ helpers

    private Theme nextTheme() {
        if (themes.size() == 1) {
            return theme;
        }
        Theme t;
        do {
            t = themes.get(rng.nextInt(themes.size()));
        } while (t == theme);
        return t;
    }

    private int pickTint(Theme th) {
        String hex = th.tints().isEmpty() ? "#5B9E28" : th.tints().pick(rng);
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0x5B9E28;
        }
    }

    /** True when the box comes near a recent platform, its decoration or a recent jump. */
    private boolean clashes(int[] box) {
        for (Platform r : recent) {
            for (int[] b : r.boxes()) {
                if (overlaps(box, b, 1)) {
                    return true;
                }
            }
        }
        for (int[] p : paths) {
            if (overlaps(box, p, 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Like {@link #clashes} for an island that stands right next to prev or puts a block on it: prev's own
     * island and the jump onto prev do not count.
     */
    private boolean clashesBeside(int[] box, Platform prev) {
        int[] lastPath = paths.peekLast();
        for (Platform r : recent) {
            List<int[]> boxes = r.boxes();
            for (int i = 0; i < boxes.size(); i++) {
                if (r == prev && i == 0) {
                    continue;
                }
                if (overlaps(box, boxes.get(i), 1)) {
                    return true;
                }
            }
        }
        for (int[] p : paths) {
            if (p != lastPath && overlaps(box, p, 1)) {
                return true;
            }
        }
        return false;
    }

    /** True when the space right in front of these cells (where the next platform goes) is free. */
    private boolean roomAhead(List<int[]> cells, int topY, Tier tier) {
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] c : cells) {
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxZ = Math.max(maxZ, c[1]);
        }
        int[] ahead = {maxX + 1, maxX + tier.maxGap + 2, topY - 5, topY + 3, minZ, maxZ};
        for (Platform r : recent) {
            for (int[] b : r.boxes()) {
                if (overlaps(ahead, b, 1)) {
                    return false;
                }
            }
        }
        for (int[] p : paths) {
            if (overlaps(ahead, p, 1)) {
                return false;
            }
        }
        return true;
    }

    /** True when a recent platform (other than the island the jump starts from) is near the jump. */
    private boolean pathBlocked(int[] path, Platform from) {
        for (Platform r : recent) {
            List<int[]> boxes = r.boxes();
            for (int i = 0; i < boxes.size(); i++) {
                if (r == from && i == 0) {
                    continue;
                }
                if (overlaps(path, boxes.get(i), 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The space a jump from a to the cells passes through, as a box: between the two closest
     * surface cells, from above the lower surface to head height over the higher one.
     */
    static int[] path(Platform a, List<int[]> cells, int topY) {
        double best = Double.MAX_VALUE;
        int ax = 0;
        int az = 0;
        int bx = 0;
        int bz = 0;
        for (int i = 0; i < a.cellCount(); i++) {
            for (int[] c : cells) {
                double d = Math.hypot(a.cellX(i) - c[0], a.cellZ(i) - c[1]);
                if (d < best) {
                    best = d;
                    ax = a.cellX(i);
                    az = a.cellZ(i);
                    bx = c[0];
                    bz = c[1];
                }
            }
        }
        return new int[] {Math.min(ax, bx), Math.max(ax, bx), Math.min(a.topY(), topY) + 1,
                Math.max(a.topY(), topY) + 3, Math.min(az, bz), Math.max(az, bz)};
    }

    private static List<int[]> cellsOf(Platform p) {
        List<int[]> cells = new ArrayList<>(p.cellCount());
        for (int i = 0; i < p.cellCount(); i++) {
            cells.add(new int[] {p.cellX(i), p.cellZ(i)});
        }
        return cells;
    }

    private static boolean overlaps(int[] a, int[] b, int margin) {
        return a[0] <= b[1] + margin && a[1] >= b[0] - margin
                && a[2] <= b[3] && a[3] >= b[2]
                && a[4] <= b[5] + margin && a[5] >= b[4] - margin;
    }

    /** {minX, maxX, minY, maxY, minZ, maxZ} of the blocks; maxY at least headTop (room above the surface). */
    private static int[] box(List<Placement> solid, List<Placement> decor, int headTop) {
        int[] b = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, headTop, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (List<Placement> list : List.of(solid, decor)) {
            for (Placement p : list) {
                b[0] = Math.min(b[0], p.x());
                b[1] = Math.max(b[1], p.x());
                b[2] = Math.min(b[2], p.y());
                b[3] = Math.max(b[3], p.y());
                b[4] = Math.min(b[4], p.z());
                b[5] = Math.max(b[5], p.z());
            }
        }
        b[2] -= 1;
        return b;
    }

    private static String blockAt(List<Placement> list, int x, int y, int z) {
        for (Placement p : list) {
            if (p.x() == x && p.y() == y && p.z() == z) {
                return p.block();
            }
        }
        return null;
    }

    /** Takes back the last platform remembered (a dash take-off whose target did not fit). */
    private void forget(Platform p) {
        if (recent.peekLast() == p) {
            recent.removeLast();
            paths.pollLast();
        }
    }

    private void remember(Platform p, int[] path) {
        recent.addLast(p);
        while (recent.size() > RECENT) {
            recent.removeFirst();
        }
        if (path != null) {
            paths.addLast(path);
            while (paths.size() > RECENT) {
                paths.removeFirst();
            }
        }
    }

    private int between(int lo, int hi) {
        if (hi <= lo) {
            return lo;
        }
        return lo + rng.nextInt(hi - lo + 1);
    }

    private static List<int[]> rect(int x0, int z0, int w, int d) {
        List<int[]> cells = new ArrayList<>();
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                cells.add(new int[] {x, z});
            }
        }
        return cells;
    }

    private static double gap(Platform prev, List<int[]> cells) {
        int[] xs = new int[cells.size()];
        int[] zs = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            xs[i] = cells.get(i)[0];
            zs[i] = cells.get(i)[1];
        }
        int[] px = new int[prev.cellCount()];
        int[] pz = new int[prev.cellCount()];
        for (int i = 0; i < px.length; i++) {
            px[i] = prev.cellX(i);
            pz[i] = prev.cellZ(i);
        }
        return Platform.gap(px, pz, xs, zs);
    }

    private static boolean contains(List<int[]> cells, int[] c) {
        for (int[] o : cells) {
            if (o[0] == c[0] && o[1] == c[1]) {
                return true;
            }
        }
        return false;
    }

    private static int[] closestToCenter(List<int[]> cells) {
        double cx = 0;
        double cz = 0;
        for (int[] c : cells) {
            cx += c[0];
            cz += c[1];
        }
        cx /= cells.size();
        cz /= cells.size();
        int[] best = cells.get(0);
        double bd = Double.MAX_VALUE;
        for (int[] c : cells) {
            double d = (c[0] - cx) * (c[0] - cx) + (c[1] - cz) * (c[1] - cz);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }
}
