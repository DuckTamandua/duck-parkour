package dev.endlessparkour;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Checks the top list: bests, ranks, the record message and saving/loading. */
public final class LeaderboardTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("parkour-board");
        Path file = dir.resolve("leaderboard.json");
        Leaderboard b = new Leaderboard(file, null);
        UUID a = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        Leaderboard.Result r = b.record(a, "Alice", 0, 1);
        check(!r.newBest() && r.rank() == 0, "a zero run is no best and has no rank");
        r = b.record(a, "Alice", 12, 2);
        check(r.newBest() && r.previousBest() == 0 && r.rank() == 1, "first best");
        check(r.newRecord(), "first score on an empty board is a record");
        r = b.record(c, "Carl", 30, 3);
        check(r.newBest() && r.rank() == 1 && r.newRecord(), "Carl takes the record");
        r = b.record(a, "Alice", 20, 4);
        check(r.newBest() && r.rank() == 2 && !r.newRecord(), "better but not first: no record message");
        r = b.record(a, "Alice", 5, 5);
        check(!r.newBest() && r.previousBest() == 20, "a worse run keeps the best");
        r = b.record(c, "Carl", 31, 6);
        check(r.newBest() && r.rank() == 1 && !r.newRecord(), "beating your own record is no new record message");
        r = b.record(d, "Dana", 31, 7);
        check(r.newBest() && r.rank() == 2, "a tie ranks behind whoever got there first");

        List<Leaderboard.Line> top = b.top(10);
        check(top.size() == 3, "three players on the list");
        check(top.get(0).name().equals("Carl") && top.get(0).best() == 31, "Carl first");
        check(top.get(1).name().equals("Dana"), "Dana second");
        check(top.get(2).name().equals("Alice") && top.get(2).best() == 20, "Alice third");
        Leaderboard.Entry alice = b.get(a);
        check(alice.runs == 4 && alice.platforms == 37, "runs and platforms counted (" + alice.runs + ", " + alice.platforms + ")");
        check(b.find("alice") != null && b.find("nobody") == null, "find by name ignores case");

        b.save();
        Leaderboard loaded = new Leaderboard(file, null);
        loaded.load();
        check(loaded.top(10).equals(top), "same list after saving and loading");
        check(loaded.get(a).runs == 4, "stats survive saving");
        String json = Files.readString(file);
        check(json.contains("\"Best\": 31") && json.contains("\"Name\": \"Carl\""), "readable JSON keys");

        Files.writeString(file, "{ broken");
        Leaderboard broken = new Leaderboard(file, new FakeLog().logger());
        broken.load();
        check(broken.size() == 0, "a broken file starts empty");
        check(Files.exists(dir.resolve("leaderboard.broken.json")), "a broken file is kept as a copy");

        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("Leaderboard OK");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    /** The real server logger, so the warning path runs like on a server. */
    private static final class FakeLog {
        com.hypixel.hytale.logger.HytaleLogger logger() {
            return com.hypixel.hytale.logger.HytaleLogger.get("LeaderboardTest");
        }
    }
}
