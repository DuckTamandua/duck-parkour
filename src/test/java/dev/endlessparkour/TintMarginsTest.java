package dev.endlessparkour;

import dev.endlessparkour.course.CourseGenerator;
import dev.endlessparkour.course.Placement;
import dev.endlessparkour.course.Platform;
import dev.endlessparkour.course.Themes;
import dev.endlessparkour.course.Tier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the grass colour rim around islands: solid grass blends with the columns around it, a flying
 * island has one plain colour, so every column next to an island must have its colour (unless an island
 * of another colour is as near), and no column may be claimed by two colours. Run with:
 * java -cp <classes> dev.endlessparkour.TintMarginsTest
 */
public final class TintMarginsTest {

    private static int failures;

    public static void main(String[] args) {
        simple();
        courses();
        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("Grass colour rims OK");
    }

    /** One island alone gets the whole square around it; two of different colours split the space between them. */
    private static void simple() {
        Set<Long> a = square(0, 0, 3);
        Set<Long> alone = TintMargins.columns(a, List.of(), 4);
        check(alone.size() == 11 * 11, "an island of 3 x 3 alone gets 11 x 11 columns (" + alone.size() + ")");
        Set<Long> b = square(8, 0, 3); // columns 8..10, a gap of 5 columns (3..7) to the first
        Set<Long> ra = TintMargins.columns(a, List.of(b), 4);
        Set<Long> rb = TintMargins.columns(b, List.of(a), 4);
        check(ra.contains(TintMargins.pack(3, 0)) && ra.contains(TintMargins.pack(4, 0)), "near columns go to the first");
        check(rb.contains(TintMargins.pack(7, 0)) && rb.contains(TintMargins.pack(6, 0)), "near columns go to the second");
        check(!ra.contains(TintMargins.pack(5, 0)) && !rb.contains(TintMargins.pack(5, 0)),
                "the column in the middle (a tie) keeps what it has");
        check(!ra.contains(TintMargins.pack(8, 0)) && !rb.contains(TintMargins.pack(2, 0)),
                "an island never takes the other's own columns");
    }

    /**
     * Real courses: every column around an island that is nearer to it than to any island of another
     * colour has its colour, and no column gets two colours. Without the rim (radius 0, as before 1.3.6)
     * the columns next to most islands keep another colour.
     */
    private static void courses() {
        int islands = 0;
        int rimColumns = 0;
        int clashes = 0;
        int uncovered = 0;
        int uncoveredWithoutRim = 0;
        int areaEdges = 0;
        for (int s = 0; s < 40; s++) {
            CourseGenerator g = new CourseGenerator(7000L + s, Themes.ALL, Themes.START, Tier.defaults(), 0, 120, 80, 170,
                    22, 45, 14);
            List<Platform> run = new ArrayList<>();
            Platform p = g.start(0);
            run.add(p);
            while (run.size() < 120) {
                p = g.next(p);
                run.add(p);
            }
            // Islands live in windows like a real run (3 behind, 7 ahead): check each window.
            for (int i = 3; i < run.size() - 7; i += 5) {
                List<Platform> window = run.subList(i - 3, i + 8);
                Map<Long, Integer> colour = new HashMap<>();
                Map<Long, Integer> colourWithoutRim = new HashMap<>();
                for (Platform q : window) {
                    Set<Long> got = BlockPlacer.marginColumns(q, window);
                    rimColumns += got.size() - own(q).size();
                    for (long c : got) {
                        Integer before = colour.put(c, q.tint());
                        if (before != null && before != q.tint()) {
                            clashes++;
                        }
                    }
                    for (long c : own(q)) {
                        colourWithoutRim.put(c, q.tint());
                    }
                }
                for (Platform q : window) {
                    islands++;
                    boolean edge = false;
                    for (long c : own(q)) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                long n = TintMargins.pack(TintMargins.x(c) + dx, TintMargins.z(c) + dz);
                                if (nearestForeign(n, q, window) <= nearestOwn(n, q)) {
                                    edge = true; // an island of another colour is as near: a blend is unavoidable
                                    continue;
                                }
                                Integer got = colour.get(n);
                                if (got == null || got != q.tint()) {
                                    uncovered++;
                                }
                                Integer old = colourWithoutRim.get(n);
                                if (old == null || old != q.tint()) {
                                    uncoveredWithoutRim++;
                                }
                            }
                        }
                    }
                    if (edge) {
                        areaEdges++;
                    }
                }
            }
        }
        System.out.printf("Grass colour rims: %d islands, %d rim columns, %d next-door columns of another colour "
                + "(%d without the rim), %d islands next to another area, %d clashes%n", islands, rimColumns,
                uncovered, uncoveredWithoutRim, areaEdges, clashes);
        check(uncovered == 0, "every column next to an island has its colour (where no other area is as near)");
        check(clashes == 0, "no column gets two colours");
        check(uncoveredWithoutRim > islands, "without the rim most islands blend with other colours at their edges");
    }

    private static int nearestOwn(long c, Platform q) {
        int best = Integer.MAX_VALUE;
        for (long o : own(q)) {
            best = Math.min(best, steps(c, o));
        }
        return best;
    }

    private static int nearestForeign(long c, Platform q, List<Platform> window) {
        int best = Integer.MAX_VALUE;
        for (Platform o : window) {
            if (o == q || o.tint() == q.tint()) {
                continue;
            }
            for (long f : own(o)) {
                best = Math.min(best, steps(c, f));
            }
        }
        return best;
    }

    private static int steps(long a, long b) {
        return Math.max(Math.abs(TintMargins.x(a) - TintMargins.x(b)), Math.abs(TintMargins.z(a) - TintMargins.z(b)));
    }

    private static Set<Long> own(Platform p) {
        Set<Long> out = new HashSet<>();
        for (Placement b : p.blocks()) {
            out.add(TintMargins.pack(b.x(), b.z()));
        }
        return out;
    }

    private static Set<Long> square(int x0, int z0, int size) {
        Set<Long> out = new HashSet<>();
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                out.add(TintMargins.pack(x, z));
            }
        }
        return out;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }
}
