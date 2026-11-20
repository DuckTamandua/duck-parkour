package dev.endlessparkour;

import dev.endlessparkour.course.Placement;

/** The few block operations the island mover needs (the world, or a map in the tests). */
interface BlockAccess {

    /** The block id at a position: 0 for air, -1 when its section is not loaded. */
    int blockAt(int x, int y, int z);

    /** Sets the block of p at (x, y, z) instead of its own position; false when the section is not loaded. */
    boolean place(Placement p, int x, int y, int z);

    /** Clears a position; false when the section is not loaded. */
    boolean clear(int x, int y, int z);
}
