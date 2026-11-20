package dev.endlessparkour.course;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One area of the course, modelled on a region or structure of Orbis: block palettes and
 * plants from the vanilla world generation, its grass colours, weather, discovery sound,
 * trees and the difficulty level of its jumps.
 */
public final class Theme {

    /** A tree kind for decoration islands: trunk block and leaves block (no leaves: a stone pillar). */
    public record Tree(String trunk, String leaves) {
    }

    private static final Pick FALLBACK = Pick.of("Rock_Stone", 1);

    private final String id;
    private final String name;
    private final String zone;
    private final String color;
    private final String sound;
    private final String weather;
    private final String environment;
    private final int level;
    private final Pick top;
    private final Pick fill;
    private final Pick bottom;
    private final Pick accent;
    private final double accentChance;
    private final Pick marker;
    private final Pick soilPlants;
    private final Pick anyPlants;
    private final double plantChance;
    private final Pick hanging;
    private final double hangingChance;
    private final Pick pillar;
    private final Pick tints;
    private final List<Tree> trees;
    private final Pick scatter;

    private Theme(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.zone = b.zone;
        this.color = b.color;
        this.sound = b.sound;
        this.weather = b.weather;
        this.environment = b.environment;
        this.level = b.level;
        this.top = b.top;
        this.fill = b.fill;
        this.bottom = b.bottom;
        this.accent = b.accent;
        this.accentChance = b.accentChance;
        this.marker = b.marker;
        this.soilPlants = b.soilPlants;
        this.anyPlants = b.anyPlants;
        this.plantChance = b.plantChance;
        this.hanging = b.hanging;
        this.hangingChance = b.hangingChance;
        this.pillar = b.pillar;
        this.tints = b.tints;
        this.trees = List.copyOf(b.trees);
        this.scatter = b.scatter;
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public String id() {
        return id;
    }

    /** Region name shown as the title. */
    public String name() {
        return name;
    }

    /** Zone name shown under the title. */
    public String zone() {
        return zone;
    }

    public String color() {
        return color;
    }

    public String sound() {
        return sound;
    }

    public String weather() {
        return weather;
    }

    /** Vanilla environment of the area: it brings the area's music and ambient sounds. */
    public String environment() {
        return environment;
    }

    /** Difficulty level 1 (easy) to 10 (hard), after where the area lies in Orbis. */
    public int level() {
        return level;
    }

    public Pick top() {
        return top;
    }

    public Pick fill() {
        return fill;
    }

    public Pick bottom() {
        return bottom;
    }

    public Pick accent() {
        return accent;
    }

    public double accentChance() {
        return accentChance;
    }

    public Pick marker() {
        return marker;
    }

    public Pick soilPlants() {
        return soilPlants;
    }

    public Pick anyPlants() {
        return anyPlants;
    }

    public double plantChance() {
        return plantChance;
    }

    public Pick hanging() {
        return hanging;
    }

    public double hangingChance() {
        return hangingChance;
    }

    public Pick pillar() {
        return pillar;
    }

    /** Grass colours as hex strings ("#5B9E28"). */
    public Pick tints() {
        return tints;
    }

    public List<Tree> trees() {
        return trees;
    }

    /** Small solid clutter (rubble, sticks) for decoration islands only, never on landing spots. */
    public Pick scatter() {
        return scatter;
    }

    /** All block names this theme uses (for checks against the server's blocks). */
    public List<String> blockNames() {
        List<String> out = new ArrayList<>();
        for (Pick p : new Pick[] {top, fill, bottom, accent, marker, soilPlants, anyPlants, hanging, pillar, scatter}) {
            out.addAll(p.names());
        }
        for (Tree t : trees) {
            out.add(t.trunk());
            if (t.leaves() != null) {
                out.add(t.leaves());
            }
        }
        return out;
    }

    /** Drops blocks the server does not know; the main layers fall back to plain stone. */
    public Theme filter(Predicate<String> exists) {
        Builder b = new Builder(id, name);
        b.zone = zone;
        b.color = color;
        b.sound = sound;
        b.weather = weather;
        b.environment = environment;
        b.level = level;
        b.top = top.filter(exists).orElse(FALLBACK);
        b.fill = fill.filter(exists).orElse(b.top);
        b.bottom = bottom.filter(exists).orElse(b.fill);
        b.accent = accent.filter(exists);
        b.accentChance = accentChance;
        b.marker = marker.filter(exists).orElse(b.top);
        b.soilPlants = soilPlants.filter(exists);
        b.anyPlants = anyPlants.filter(exists);
        b.plantChance = plantChance;
        b.hanging = hanging.filter(exists);
        b.hangingChance = hangingChance;
        b.pillar = pillar.filter(exists).orElse(b.fill);
        b.tints = tints;
        for (Tree t : trees) {
            if (exists.test(t.trunk()) && (t.leaves() == null || exists.test(t.leaves()))) {
                b.trees.add(t);
            }
        }
        b.scatter = scatter.filter(exists);
        return new Theme(b);
    }

    @Override
    public String toString() {
        return name;
    }

    /** Builds a theme; unset palettes stay empty. */
    public static final class Builder {
        private final String id;
        private final String name;
        private String zone = "";
        private String color = "#FFFFFF";
        private String sound = "";
        private String weather = "";
        private String environment = "";
        private int level = 1;
        private Pick top = Pick.empty();
        private Pick fill = Pick.empty();
        private Pick bottom = Pick.empty();
        private Pick accent = Pick.empty();
        private double accentChance;
        private Pick marker = Pick.empty();
        private Pick soilPlants = Pick.empty();
        private Pick anyPlants = Pick.empty();
        private double plantChance;
        private Pick hanging = Pick.empty();
        private double hangingChance;
        private Pick pillar = Pick.empty();
        private Pick tints = Pick.of("#5B9E28", 1);
        private final List<Tree> trees = new ArrayList<>();
        private Pick scatter = Pick.empty();

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder zone(String zone) {
            this.zone = zone;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder sound(String sound) {
            this.sound = sound;
            return this;
        }

        public Builder weather(String weather) {
            this.weather = weather;
            return this;
        }

        public Builder env(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder level(int level) {
            this.level = Math.max(1, Math.min(10, level));
            return this;
        }

        public Builder top(Object... pairs) {
            this.top = Pick.of(pairs);
            return this;
        }

        public Builder fill(Object... pairs) {
            this.fill = Pick.of(pairs);
            return this;
        }

        public Builder bottom(Object... pairs) {
            this.bottom = Pick.of(pairs);
            return this;
        }

        public Builder accent(double chance, Object... pairs) {
            this.accentChance = chance;
            this.accent = Pick.of(pairs);
            return this;
        }

        public Builder marker(String block) {
            this.marker = Pick.of(block, 1);
            return this;
        }

        public Builder plants(double chance) {
            this.plantChance = chance;
            return this;
        }

        public Builder soilPlants(Object... pairs) {
            this.soilPlants = Pick.of(pairs);
            return this;
        }

        public Builder anyPlants(Object... pairs) {
            this.anyPlants = Pick.of(pairs);
            return this;
        }

        public Builder hanging(double chance, Object... pairs) {
            this.hangingChance = chance;
            this.hanging = Pick.of(pairs);
            return this;
        }

        public Builder pillar(String block) {
            this.pillar = Pick.of(block, 1);
            return this;
        }

        public Builder tints(Object... pairs) {
            this.tints = Pick.of(pairs);
            return this;
        }

        public Builder tree(String trunk, String leaves) {
            this.trees.add(new Tree(trunk, leaves));
            return this;
        }

        public Builder scatter(Object... pairs) {
            this.scatter = Pick.of(pairs);
            return this;
        }

        public Theme build() {
            return new Theme(this);
        }
    }
}
