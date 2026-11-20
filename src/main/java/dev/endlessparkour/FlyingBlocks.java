package dev.endlessparkour;

import dev.endlessparkour.course.Placement;

import java.util.UUID;
import java.util.function.Predicate;

/** Moving block objects for island flights (block entities in the game, a map in the tests). */
interface FlyingBlocks {

    /**
     * Tells which objects the game of this player has right now (the server sent them and has not taken
     * them back), or null when that cannot be known (player not here). Valid for the current tick only.
     */
    default Predicate<Object> sentTo(UUID player) {
        return null;
    }

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
