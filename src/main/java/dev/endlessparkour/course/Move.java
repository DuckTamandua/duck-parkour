package dev.endlessparkour.course;

/** How a player gets from one platform onto the next. */
public enum Move {
    /** A normal (sprint) jump. */
    JUMP,
    /** Up a ladder on the side of a tower that stands right next to the island before. */
    CLIMB,
    /** A bounce off the mushroom on the island before, up onto a higher island. */
    BOUNCE,
    /** A gap too long to jump: it takes a dash with the sword in the air. */
    DASH,
    /** A drop far down onto an island below. */
    DROP,
    /** A jump at a ladder hanging on a tower across a gap: grab it in the air and climb up. */
    LADDER_JUMP,
    /** Like {@link #LADDER_JUMP}, but too far to jump: it takes a dash with the sword. */
    LADDER_DASH
}
