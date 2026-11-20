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
}
