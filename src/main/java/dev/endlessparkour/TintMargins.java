package dev.endlessparkour;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which grass columns get an island's colour. Hytale blends the grass colour of a solid block with the
 * columns around it, but a flying block object has one plain colour: an island whose neighbouring
 * columns have another colour looked different the moment it became solid. So the columns around an
 * island get its colour too, up to {@link #RADIUS} away, except where an island of another colour is
 * at least as near (each column goes to its nearest island; a tie keeps what is there). Columns are
 * packed as {@code x << 32 | z} (see {@link #pack}). Pure logic, no Hytale classes.
 */
final class TintMargins {

    /** How many columns around an island get its colour. */
    static final int RADIUS = 4;

    private TintMargins() {
    }

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int x(long column) {
        return (int) (column >> 32);
    }

    static int z(long column) {
        return (int) column;
    }

    /**
     * The island's own columns plus those up to radius away (counted like a king moves: a diagonal step
     * is one) that are nearer to the island than to every column of the other islands. The others are
     * the islands of another colour (islands of the same colour never compete).
     */
    static Set<Long> columns(Set<Long> own, List<Set<Long>> others, int radius) {
        Set<Long> out = new HashSet<>(own);
        if (radius <= 0 || own.isEmpty()) {
            return out;
        }
        Set<Long> foreign = new HashSet<>();
        for (Set<Long> o : others) {
            foreign.addAll(o);
        }
        for (long c : own) {
            int cx = x(c);
            int cz = z(c);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long m = pack(cx + dx, cz + dz);
                    if (out.contains(m) || foreign.contains(m)) {
                        continue;
                    }
                    int mine = distance(m, own, radius);
                    if (mine <= radius && !foreignWithin(m, foreign, mine)) {
                        out.add(m);
                    }
                }
            }
        }
        return out;
    }

    /** Steps from the column to the nearest of these columns, or more than limit when none is that near. */
    private static int distance(long column, Set<Long> set, int limit) {
        int x = x(column);
        int z = z(column);
        for (int d = 0; d <= limit; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == d && set.contains(pack(x + dx, z + dz))) {
                        return d;
                    }
                }
            }
        }
        return limit + 1;
    }

    /** True when a foreign column is at most d steps away (as near as the island, or nearer). */
    private static boolean foreignWithin(long column, Set<Long> foreign, int d) {
        if (foreign.isEmpty()) {
            return false;
        }
        return distance(column, foreign, d) <= d;
    }
}
