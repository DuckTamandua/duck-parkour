package dev.endlessparkour;

import dev.endlessparkour.course.CourseGenerator;
import dev.endlessparkour.course.Placement;
import dev.endlessparkour.course.Platform;
import dev.endlessparkour.course.Theme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** One player's current run: their lane, the live platforms and the score. World thread only. */
final class Run {

    final UUID playerId;
    final String playerName;
    final int lane;
    final CourseGenerator generator;
    final long startedAt;
    private final Deque<Platform> platforms = new ArrayDeque<>();

    /** Highest platform reached (0 = start island). */
    int score;
    Theme theme;
    /** Waiting until the player arrives on the start island. */
    boolean awaitingSpawn = true;
    /** Started right after a fall (no biome title, the score title stays). */
    boolean afterFall;
    /** Built while the player is still connecting (they enter the world on the start island). */
    boolean joining;
    /** Completes when the blocks of the start course are placed. */
    CompletableFuture<Void> built = CompletableFuture.completedFuture(null);
    long teleportAt;
    /** Islands that fly in as soon as the player stands on the start island. */
    List<Platform> pendingFlights;
    int teleportTries;
    ParkourHud hud;
    int hudBoardVersion = -1;
    /** When the player last reached a platform (for the special jump log). */
    long reachedAt;
    /** Dash watch: the last position seen and, during a dash, where and when it began and its top speed. */
    double lastX;
    double lastY;
    double lastZ;
    long lastAt;
    boolean dashing;
    double dashX;
    double dashY;
    double dashZ;
    long dashAt;
    double dashTop;
    /** Bounce watch: rising fast, from where, and the highest point so far. */
    boolean bouncing;
    double bounceFrom;
    double bounceTop;
    /** Pull-up watch (Hytale's mantling at an edge): going on, and the height it began at. */
    boolean mantling;
    double mantleFrom;

    Run(UUID playerId, String playerName, int lane, CourseGenerator generator, long now) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.lane = lane;
        this.generator = generator;
        this.startedAt = now;
    }

    void add(Platform p) {
        platforms.addLast(p);
    }

    Platform first() {
        return platforms.peekFirst();
    }

    Platform last() {
        return platforms.peekLast();
    }

    Platform removeFirst() {
        return platforms.pollFirst();
    }

    int size() {
        return platforms.size();
    }

    /** The platform with this index, or null when it is not built (yet or any more). */
    Platform get(int index) {
        for (Platform p : platforms) {
            if (p.index() == index) {
                return p;
            }
        }
        return null;
    }

    Iterable<Platform> platforms() {
        return platforms;
    }

    List<Platform> platformList() {
        return new ArrayList<>(platforms);
    }

    List<Placement> allBlocks() {
        List<Placement> all = new ArrayList<>();
        for (Platform p : platforms) {
            all.addAll(p.blocks());
        }
        return all;
    }
}
