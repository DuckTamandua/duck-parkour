package dev.endlessparkour.course;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/** A weighted list of block names. */
public final class Pick {

    private static final Pick EMPTY = new Pick(List.of(), List.of());

    private final List<String> names;
    private final List<Integer> weights;
    private final int total;

    private Pick(List<String> names, List<Integer> weights) {
        this.names = List.copyOf(names);
        this.weights = List.copyOf(weights);
        int sum = 0;
        for (int w : weights) {
            sum += w;
        }
        this.total = sum;
    }

    /** Builds a list from pairs: {@code Pick.of("Soil_Grass", 10, "Rock_Stone_Mossy", 1)}. */
    public static Pick of(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Pick.of needs name/weight pairs");
        }
        List<String> names = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            int weight = (Integer) pairs[i + 1];
            if (weight > 0) {
                names.add((String) pairs[i]);
                weights.add(weight);
            }
        }
        return new Pick(names, weights);
    }

    public static Pick empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    public String pick(Random rng) {
        if (names.isEmpty()) {
            throw new IllegalStateException("empty pick list");
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < names.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return names.get(i);
            }
        }
        return names.get(names.size() - 1);
    }

    /** Keeps only the names that pass the test (used to drop blocks a server does not know). */
    public Pick filter(Predicate<String> keep) {
        List<String> n = new ArrayList<>();
        List<Integer> w = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (keep.test(names.get(i))) {
                n.add(names.get(i));
                w.add(weights.get(i));
            }
        }
        return n.size() == names.size() ? this : new Pick(n, w);
    }

    public Pick orElse(Pick fallback) {
        return isEmpty() ? fallback : this;
    }

    public List<String> names() {
        return Collections.unmodifiableList(names);
    }
}
