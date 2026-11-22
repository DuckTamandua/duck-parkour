package dev.endlessparkour.course;

import java.util.List;

/** One generated platform (a small floating island) of a run, with its decoration. */
public final class Platform {

    private final int index;
    private final Theme theme;
    private final int topY;
    private final int bottomY;
    private final int[] cellX;
    private final int[] cellZ;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final List<Placement> blocks;
    private final List<int[]> boxes;
    private final boolean sectionStart;
    private final int jumpDy;
    private final double jumpGap;
    private final int tint;
    private final double spawnX;
    private final double spawnZ;
    private final Move move;

    Platform(int index, Theme theme, int topY, int bottomY, int[] cellX, int[] cellZ,
             List<Placement> blocks, List<int[]> boxes, boolean sectionStart, int jumpDy, double jumpGap, int tint) {
        this(index, theme, topY, bottomY, cellX, cellZ, blocks, boxes, sectionStart, jumpDy, jumpGap, tint, Move.JUMP);
    }

    Platform(int index, Theme theme, int topY, int bottomY, int[] cellX, int[] cellZ,
             List<Placement> blocks, List<int[]> boxes, boolean sectionStart, int jumpDy, double jumpGap, int tint,
             Move move) {
        this.index = index;
        this.theme = theme;
        this.topY = topY;
        this.bottomY = bottomY;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.blocks = List.copyOf(blocks);
        this.boxes = List.copyOf(boxes);
        this.sectionStart = sectionStart;
        this.jumpDy = jumpDy;
        this.jumpGap = jumpGap;
        this.tint = tint;
        this.move = move;
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        double sx = 0;
        double sz = 0;
        for (int i = 0; i < cellX.length; i++) {
            x0 = Math.min(x0, cellX[i]);
            x1 = Math.max(x1, cellX[i]);
            z0 = Math.min(z0, cellZ[i]);
            z1 = Math.max(z1, cellZ[i]);
            sx += cellX[i] + 0.5;
            sz += cellZ[i] + 0.5;
        }
        this.minX = x0;
        this.maxX = x1;
        this.minZ = z0;
        this.maxZ = z1;
        // Spawn on the surface cell closest to the middle of the island.
        double cx = sx / cellX.length;
        double cz = sz / cellX.length;
        double best = Double.MAX_VALUE;
        double bx = cx;
        double bz = cz;
        for (int i = 0; i < cellX.length; i++) {
            double dx = cellX[i] + 0.5 - cx;
            double dz = cellZ[i] + 0.5 - cz;
            double d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                bx = cellX[i] + 0.5;
                bz = cellZ[i] + 0.5;
            }
        }
        this.spawnX = bx;
        this.spawnZ = bz;
    }

    public int index() {
        return index;
    }

    public Theme theme() {
        return theme;
    }

    /** Y of the surface blocks; players stand at topY + 1. */
    public int topY() {
        return topY;
    }

    /** Lowest Y used by the island, decoration included. */
    public int bottomY() {
        return bottomY;
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public int cellCount() {
        return cellX.length;
    }

    public int cellX(int i) {
        return cellX[i];
    }

    public int cellZ(int i) {
        return cellZ[i];
    }

    public List<Placement> blocks() {
        return blocks;
    }

    /**
     * Space taken by the island and its decoration: {minX, maxX, minY, maxY, minZ, maxZ} each,
     * the island first. Other platforms keep clear of these boxes.
     */
    public List<int[]> boxes() {
        return boxes;
    }

    /** First platform of a new area. */
    public boolean sectionStart() {
        return sectionStart;
    }

    /** Height change of the jump that leads to this platform. */
    public int jumpDy() {
        return jumpDy;
    }

    /** Gap (in blocks) of the jump that leads to this platform. */
    public double jumpGap() {
        return jumpGap;
    }

    /** How the player gets onto this platform from the one before. */
    public Move move() {
        return move;
    }

    /** Grass colour of this platform's columns as 0xRRGGBB. */
    public int tint() {
        return tint;
    }

    public double spawnX() {
        return spawnX;
    }

    public double spawnZ() {
        return spawnZ;
    }

    public boolean hasCell(int x, int z) {
        for (int i = 0; i < cellX.length; i++) {
            if (cellX[i] == x && cellZ[i] == z) {
                return true;
            }
        }
        return false;
    }

    /** True when a player whose feet are at (x, y, z) stands on (or just above) the surface. */
    public boolean isStandingOn(double x, double y, double z) {
        if (y < topY + 0.9 || y > topY + 1.35) {
            return false;
        }
        double r = JumpModel.HALF_WIDTH - 0.05;
        return hasCell(floor(x - r), floor(z - r)) || hasCell(floor(x + r), floor(z - r))
                || hasCell(floor(x - r), floor(z + r)) || hasCell(floor(x + r), floor(z + r));
    }

    /** True when (x, z) lies within this platform's footprint grown by margin blocks. */
    public boolean isNear(double x, double z, double margin) {
        return x >= minX - margin && x <= maxX + 1 + margin && z >= minZ - margin && z <= maxZ + 1 + margin;
    }

    /** Smallest distance in blocks between the surface cells of two platforms (0 when they touch). */
    public double gapTo(Platform other) {
        return gap(cellX, cellZ, other.cellX, other.cellZ);
    }

    static double gap(int[] ax, int[] az, int[] bx, int[] bz) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < ax.length; i++) {
            for (int j = 0; j < bx.length; j++) {
                int dx = Math.max(0, Math.abs(ax[i] - bx[j]) - 1);
                int dz = Math.max(0, Math.abs(az[i] - bz[j]) - 1);
                double d = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (d < best) {
                    best = d;
                }
            }
        }
        return best;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
