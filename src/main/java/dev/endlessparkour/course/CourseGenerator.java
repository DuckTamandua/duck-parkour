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

    private enum Kind { START, GATE, NORMAL, PILLAR }

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
        int index = prev.index() + 1;
        boolean sectionStart = index == nextSection;
        if (sectionStart) {
            theme = nextTheme();
            tint = pickTint(theme);
            // Every area has its own random length.
            nextSection = index + between(sectionMin, sectionMax);
        }
        Tier tier = tierFor(theme);
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
        List<Placement> solid = new ArrayList<>();
        List<Placement> decor = new ArrayList<>();
        boolean marked = kind == Kind.START || kind == Kind.GATE;
        int[] center = closestToCenter(top);
        int maxDepth = switch (kind) {
            case START -> 5;
            case GATE -> 4;
            case PILLAR -> 3 + rng.nextInt(3);
            case NORMAL -> Math.min(5, 2 + top.size() / 3) + rng.nextInt(2);
        };
        body(th, topY, top, kind, maxDepth, marked ? center : null, solid, decor);

        // Plants on the surface.
        if (kind != Kind.PILLAR && th.plantChance() > 0) {
            for (int[] c : top) {
                if (marked && c[0] == center[0] && c[1] == center[1]) {
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
        return new Platform(index, th, topY, bottomY, xs, zs, all, boxes, marked && index > 0, dy, gap, tint);
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
            if (kind == Kind.PILLAR) {
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
                        block = withFloor(th.top(), floor, th.fill());
                    }
                } else if (kind == Kind.PILLAR && k < depth - 1) {
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
            if (!th.hanging().isEmpty()
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
