package dev.endlessparkour.course;

/**
 * What a player can jump in Hytale, from the game's own numbers:
 * gravity 32 blocks/s² (PhysicsConstants), jump force 11.8, base speed 5.5 and
 * sprint multiplier 1.273 (MovementConfig "Default"), hitbox half width 0.325 (Player model).
 * Air drag and air control are not modelled, so a safety factor keeps every gap clearly doable.
 */
public final class JumpModel {

    public static final double GRAVITY = 32.0;
    public static final double JUMP_FORCE = 11.8;
    public static final double RUN_SPEED = 5.5;
    public static final double SPRINT_SPEED = 5.5 * 1.273;
    public static final double HALF_WIDTH = 0.325;
    public static final double SAFETY = 0.80;
    /** Upward speed the bouncy mushroom gives (its block's MovementSettings BounceVelocity). */
    public static final double BOUNCE_VELOCITY = 20.0;
    /** Height kept to spare on a bounce (a bounce was not measured in play yet). */
    public static final double BOUNCE_SPARE = 0.75;
    /**
     * Extra distance in blocks counted for one sword dash in the air (the dash pushes 20 blocks/s forward
     * for 0.25 s, see Server/Item/Interactions/EndlessParkour): the least measured in play (3.8 to 5.1).
     */
    public static final double DASH_GAIN = 3.8;
    /**
     * Highest ledge above the surface a player jumps from that a jump and pulling up at the edge (Hytale's
     * mantling, a client feature) reach: apex 2.18 and about 2 blocks of reach, rounded up. Towers and bounce
     * targets stand higher, so neither can be climbed without the ladder or the mushroom.
     */
    public static final double MANTLE_HEIGHT = 4.5;
    /** How much farther a jump gets by grabbing the edge of the island it falls short of. */
    public static final double MANTLE_REACH = 1.0;
    /** Highest climb up a ladder the course asks for. */
    public static final int MAX_CLIMB = 8;
    /** Height of a player (the body grabs a ladder anywhere from the feet to the head). */
    public static final double PLAYER_HEIGHT = 1.8;

    private JumpModel() {
    }

    /** Height of the highest point of a jump, in blocks (about 2.18). */
    public static double apex() {
        return JUMP_FORCE * JUMP_FORCE / (2 * GRAVITY);
    }

    /** Seconds in the air for a jump that ends dy blocks higher (negative: lower), or NaN if out of reach. */
    public static double airTime(double dy) {
        double disc = JUMP_FORCE * JUMP_FORCE - 2 * GRAVITY * dy;
        if (disc < 0) {
            return Double.NaN;
        }
        return (JUMP_FORCE + Math.sqrt(disc)) / GRAVITY;
    }

    /** Largest gap (in blocks, edge to edge) a sprint jump clears with the safety margin. */
    public static double maxGap(int dy) {
        if (dy > 1) {
            return -1;
        }
        double t = airTime(dy);
        if (Double.isNaN(t)) {
            return -1;
        }
        return SPRINT_SPEED * t * SAFETY + 2 * HALF_WIDTH;
    }

    /** Largest gap a running jump (no sprint) clears with the safety margin. */
    public static double maxGapRunning(int dy) {
        if (dy > 1) {
            return -1;
        }
        double t = airTime(dy);
        return Double.isNaN(t) ? -1 : RUN_SPEED * t * SAFETY + 2 * HALF_WIDTH;
    }

    public static boolean feasible(double gap, int dy) {
        return dy <= 1 && gap >= 1.0 && gap <= maxGap(dy);
    }

    /** Largest gap a sprint jump with one sword dash in the air clears with the safety margin. */
    public static double maxDashGap(int dy) {
        if (dy > 1) {
            return -1;
        }
        double t = airTime(dy);
        return Double.isNaN(t) ? -1 : (SPRINT_SPEED * t + DASH_GAIN) * SAFETY + 2 * HALF_WIDTH;
    }

    /**
     * Longest gap a jump without a dash can make at all: a perfect sprint jump (no safety margin) and a grab
     * at the edge of the island it falls short of. Dash gaps are longer.
     */
    public static double maxGapNoDash(int dy) {
        double t = airTime(dy);
        return Double.isNaN(t) ? -1 : SPRINT_SPEED * t + 2 * HALF_WIDTH + MANTLE_REACH;
    }

    /**
     * How far a sprint jump gets (no safety margin, no dash) while the feet stay at least feetAbove blocks
     * above where they took off (negative: below), in blocks of travel of the body's middle.
     */
    public static double travelAbove(double feetAbove) {
        double disc = JUMP_FORCE * JUMP_FORCE - 2 * GRAVITY * feetAbove;
        if (disc < 0) {
            return 0;
        }
        return SPRINT_SPEED * (JUMP_FORCE + Math.sqrt(disc)) / GRAVITY;
    }

    /**
     * Travel of the body's middle needed to touch a ladder whose cell starts this many blocks past the
     * take-off island's edge (the player stands up to a half width over the edge and touches with the front).
     */
    public static double travelToLadder(int cellsPastEdge) {
        return cellsPastEdge - 1 - 2 * HALF_WIDTH;
    }

    /** Height the feet rise above the mushroom (about 6.25 blocks). */
    public static double bounceApex() {
        return BOUNCE_VELOCITY * BOUNCE_VELOCITY / (2 * GRAVITY);
    }

    /**
     * Largest gap from the mushroom's block to an island whose standing height is rise blocks above the
     * mushroom's top, at running speed; -1 when the bounce does not lift that high (with
     * {@link #BOUNCE_SPARE} to spare).
     */
    public static double maxBounceGap(double rise) {
        if (rise > bounceApex() - BOUNCE_SPARE) {
            return -1;
        }
        double t = (BOUNCE_VELOCITY + Math.sqrt(BOUNCE_VELOCITY * BOUNCE_VELOCITY - 2 * GRAVITY * rise)) / GRAVITY;
        return RUN_SPEED * t * SAFETY + 2 * HALF_WIDTH;
    }
}
