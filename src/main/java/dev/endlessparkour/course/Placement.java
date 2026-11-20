package dev.endlessparkour.course;

/**
 * One block of a generated platform.
 *
 * @param decor   decoration (plants, hanging rocks): placed after and removed before the solid blocks
 * @param flipped place the block upside down (hanging stalactites and icicles)
 */
public record Placement(int x, int y, int z, String block, boolean decor, boolean flipped) {

    public static Placement solid(int x, int y, int z, String block) {
        return new Placement(x, y, z, block, false, false);
    }

    public static Placement decor(int x, int y, int z, String block) {
        return new Placement(x, y, z, block, true, false);
    }

    public static Placement hanging(int x, int y, int z, String block) {
        return new Placement(x, y, z, block, true, true);
    }
}
