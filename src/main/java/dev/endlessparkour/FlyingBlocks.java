package dev.endlessparkour;

import dev.endlessparkour.course.Placement;

/** Moving block objects for island flights (block entities in the game, a map in the tests). */
interface FlyingBlocks {

    /**
     * Shows the block of b as an object centred at (x, y, z) at this size (1 = a full block); returns a
     * handle (null when the game could not make it). The object vanishes by itself after lifetimeMs,
     * should it ever be left behind.
     */
    Object spawn(Placement b, double x, double y, double z, float scale, long lifetimeMs);

    /** Moves an object and sets its size. */
    void move(Object handle, double x, double y, double z, float scale);

    /** Takes an object away. */
    void remove(Object handle);
}
