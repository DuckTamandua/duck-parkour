package dev.endlessparkour.course;

/**
 * One block of a generated platform.
 *
 * @param decor   decoration (plants, hanging rocks, ladders): placed after and removed before the solid blocks
 * @param flipped place the block upside down (hanging stalactites and icicles)
 * @param facing  for blocks that lean on a wall (ladders): the side of their cell the wall is on
 *                ({@link #EAST} +X, {@link #SOUTH} +Z, {@link #WEST} -X, {@link #NORTH} -Z), else {@link #NONE}
 */
public record Placement(int x, int y, int z, String block, boolean decor, boolean flipped, int facing) {

    public static final int NONE = -1;
    public static final int EAST = 0;
    public static final int SOUTH = 1;
    public static final int WEST = 2;
    public static final int NORTH = 3;

    public Placement(int x, int y, int z, String block, boolean decor, boolean flipped) {
        this(x, y, z, block, decor, flipped, NONE);
    }

    public static Placement solid(int x, int y, int z, String block) {
        return new Placement(x, y, z, block, false, false);
    }

    public static Placement decor(int x, int y, int z, String block) {
        return new Placement(x, y, z, block, true, false);
    }

    public static Placement hanging(int x, int y, int z, String block) {
        return new Placement(x, y, z, block, true, true);
    }

    /** A decoration block that leans on the wall on the given side of its cell (a ladder). */
    public static Placement attached(int x, int y, int z, String block, int facing) {
        return new Placement(x, y, z, block, true, false, facing);
    }

    public boolean attached() {
        return facing >= 0;
    }
}
