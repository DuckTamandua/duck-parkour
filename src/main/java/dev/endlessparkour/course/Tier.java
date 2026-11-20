package dev.endlessparkour.course;

/**
 * The jumps of one difficulty level. Gaps are counted in air blocks between two platforms
 * (a diagonal gap is measured corner to corner).
 */
public final class Tier {

    /** Smallest gap. */
    public int minGap;
    /** Largest gap for level jumps and one-block drops. */
    public int maxGap;
    /** Largest gap when the next platform is one block higher. */
    public int maxGapUp;
    /** Largest gap when the next platform is two or more blocks lower. */
    public int maxGapDrop;
    /** Largest drop in blocks. */
    public int maxDrop;
    /** Chance that the next platform is one block higher. */
    public double upChance;
    /** Chance that the next platform is lower. */
    public double downChance;
    /** Chance of a sideways offset (a diagonal or sideways jump). */
    public double sideChance;
    /** Chance of a one-block pillar. */
    public double pillarChance;
    /** Side length range of normal platforms. */
    public int minSize;
    public int maxSize;

    public Tier() {
    }

    public Tier(int minGap, int maxGap, int maxGapUp, int maxGapDrop, int maxDrop,
                double upChance, double downChance, double sideChance, double pillarChance,
                int minSize, int maxSize) {
        this.minGap = minGap;
        this.maxGap = maxGap;
        this.maxGapUp = maxGapUp;
        this.maxGapDrop = maxGapDrop;
        this.maxDrop = maxDrop;
        this.upChance = upChance;
        this.downChance = downChance;
        this.sideChance = sideChance;
        this.pillarChance = pillarChance;
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    /**
     * Levels 1 to 10, each a step you can feel: 1 big islands and short gaps, 2 every gap two blocks,
     * 3 gaps up to three, 4 up-jumps over three blocks, 5 narrow platforms, 6 every gap at least
     * three, 7 gaps up to four, 8 to 10 more and more one-block pillars (2 of 3 at level 10).
     * Every value stays within what a Hytale sprint jump clears with a safety margin (see
     * {@link JumpModel}): gaps of more than four blocks are not safely jumpable, so the top levels
     * get harder through smaller platforms, pillars and up-jumps.
     */
    public static Tier[] defaults() {
        return new Tier[] {
            new Tier(1, 2, 1, 2, 1, 0.15, 0.15, 0.15, 0.00, 3, 3),
            new Tier(2, 2, 2, 2, 1, 0.25, 0.20, 0.25, 0.00, 2, 3),
            new Tier(2, 3, 2, 3, 2, 0.25, 0.25, 0.30, 0.05, 2, 3),
            new Tier(2, 3, 3, 3, 2, 0.30, 0.25, 0.35, 0.10, 2, 2),
            new Tier(2, 3, 3, 4, 2, 0.30, 0.25, 0.40, 0.15, 1, 2),
            new Tier(3, 3, 3, 4, 3, 0.30, 0.30, 0.45, 0.20, 1, 2),
            new Tier(3, 4, 3, 4, 3, 0.35, 0.30, 0.50, 0.30, 1, 2),
            new Tier(3, 4, 3, 5, 3, 0.35, 0.30, 0.55, 0.40, 1, 2),
            new Tier(3, 4, 3, 5, 3, 0.40, 0.30, 0.60, 0.50, 1, 2),
            new Tier(3, 4, 3, 5, 4, 0.45, 0.35, 0.65, 0.65, 1, 2),
        };
    }

    /** The largest gap allowed for a jump with this height change. */
    public int maxGapFor(int dy) {
        if (dy > 0) {
            return maxGapUp;
        }
        if (dy <= -2) {
            return Math.max(maxGap, maxGapDrop);
        }
        return maxGap;
    }

    /** Clamps the values into a range the jump model allows. */
    public Tier sanitized() {
        Tier t = new Tier(minGap, maxGap, maxGapUp, maxGapDrop, maxDrop, upChance, downChance,
                sideChance, pillarChance, minSize, maxSize);
        // The +0.25 tolerance for diagonal gaps must still be within reach.
        int physicalFlat = (int) Math.floor(JumpModel.maxGap(0) - 0.25);
        int physicalUp = (int) Math.floor(JumpModel.maxGap(1) - 0.25);
        int physicalDrop = (int) Math.floor(JumpModel.maxGap(-2) - 0.25);
        t.maxGap = clamp(t.maxGap, 1, physicalFlat);
        t.minGap = clamp(t.minGap, 1, t.maxGap);
        t.maxGapUp = clamp(t.maxGapUp, 1, physicalUp);
        t.maxGapDrop = clamp(t.maxGapDrop, t.maxGap, physicalDrop);
        t.maxDrop = clamp(t.maxDrop, 0, 4);
        t.upChance = clamp(t.upChance, 0, 1);
        t.downChance = clamp(t.downChance, 0, 1 - t.upChance);
        t.sideChance = clamp(t.sideChance, 0, 1);
        t.pillarChance = clamp(t.pillarChance, 0, 1);
        t.minSize = clamp(t.minSize, 1, 4);
        t.maxSize = clamp(t.maxSize, t.minSize, 4);
        return t;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
