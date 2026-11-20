package dev.endlessparkour;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Best scores per player, saved as leaderboard.json. Thread safe. */
public final class Leaderboard {

    /** One player's stats. */
    public static final class Entry {
        public String name;
        public int best;
        public int runs;
        public long platforms;
        public long bestAt;
        public long lastPlayed;

        Entry copy() {
            Entry e = new Entry();
            e.name = name;
            e.best = best;
            e.runs = runs;
            e.platforms = platforms;
            e.bestAt = bestAt;
            e.lastPlayed = lastPlayed;
            return e;
        }
    }

    /** Result of a finished run. */
    public record Result(int score, int previousBest, boolean newBest, int rank, boolean newRecord) {
    }

    /** A line of the top list. */
    public record Line(int rank, UUID id, String name, int best) {
    }

    private static final Type TYPE = new TypeToken<Map<String, Entry>>() { }.getType();
    private static final Comparator<Map.Entry<UUID, Entry>> ORDER =
            Comparator.<Map.Entry<UUID, Entry>>comparingInt(e -> -e.getValue().best)
                    .thenComparingLong(e -> e.getValue().bestAt)
                    .thenComparing(e -> e.getValue().name == null ? "" : e.getValue().name);

    private final Path file;
    private final HytaleLogger log;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private int version;

    public Leaderboard(Path file, HytaleLogger log) {
        this.file = file;
        this.log = log;
    }

    public synchronized void load() {
        entries.clear();
        if (!Files.exists(file)) {
            return;
        }
        try {
            Map<String, Entry> read = ParkourConfig.GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE);
            if (read != null) {
                for (Map.Entry<String, Entry> e : read.entrySet()) {
                    try {
                        if (e.getValue() != null) {
                            entries.put(UUID.fromString(e.getKey()), e.getValue());
                        }
                    } catch (IllegalArgumentException ignored) {
                        // skip broken keys
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            // Keep the broken file for the admin and start with an empty board.
            log.at(Level.WARNING).withCause(e).log("Could not read %s", file);
            try {
                Files.copy(file, file.resolveSibling("leaderboard.broken.json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // nothing more to do
            }
        }
        version++;
    }

    public synchronized void save() {
        Map<String, Entry> out = new HashMap<>();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            out.put(e.getKey().toString(), e.getValue());
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, ParkourConfig.GSON.toJson(out, TYPE), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.at(Level.WARNING).withCause(e).log("Could not save %s", file);
        }
    }

    /** Stores a finished run. */
    public synchronized Result record(UUID id, String name, int score, long now) {
        UUID leaderBefore = leader();
        Entry e = entries.computeIfAbsent(id, k -> new Entry());
        e.name = name;
        e.runs++;
        e.platforms += Math.max(0, score);
        e.lastPlayed = now;
        int previous = e.best;
        boolean newBest = score > previous;
        if (newBest) {
            e.best = score;
            e.bestAt = now;
            version++;
        }
        int rank = rankOf(id);
        boolean newRecord = newBest && rank == 1 && !id.equals(leaderBefore);
        return new Result(score, previous, newBest, rank, newRecord);
    }

    public synchronized List<Line> top(int n) {
        List<Map.Entry<UUID, Entry>> list = sorted();
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < list.size() && out.size() < n; i++) {
            Map.Entry<UUID, Entry> e = list.get(i);
            if (e.getValue().best <= 0) {
                break;
            }
            out.add(new Line(i + 1, e.getKey(), e.getValue().name, e.getValue().best));
        }
        return out;
    }

    public synchronized Entry get(UUID id) {
        Entry e = entries.get(id);
        return e == null ? null : e.copy();
    }

    public synchronized int best(UUID id) {
        Entry e = entries.get(id);
        return e == null ? 0 : e.best;
    }

    /** Finds a player by name (ignoring case). */
    public synchronized Map.Entry<UUID, Entry> find(String name) {
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            if (e.getValue().name != null && e.getValue().name.equalsIgnoreCase(name)) {
                return Map.entry(e.getKey(), e.getValue().copy());
            }
        }
        return null;
    }

    /** 1-based rank, or 0 without a score. */
    public synchronized int rankOf(UUID id) {
        Entry own = entries.get(id);
        if (own == null || own.best <= 0) {
            return 0;
        }
        List<Map.Entry<UUID, Entry>> list = sorted();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Changes whenever a best score changes (to know when panels need new top lines). */
    public synchronized int version() {
        return version;
    }

    public synchronized int size() {
        return entries.size();
    }

    private UUID leader() {
        List<Map.Entry<UUID, Entry>> list = sorted();
        return list.isEmpty() || list.get(0).getValue().best <= 0 ? null : list.get(0).getKey();
    }

    private List<Map.Entry<UUID, Entry>> sorted() {
        List<Map.Entry<UUID, Entry>> list = new ArrayList<>(entries.entrySet());
        list.sort(ORDER);
        return list;
    }
}
