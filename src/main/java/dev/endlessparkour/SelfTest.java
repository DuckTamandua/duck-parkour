package dev.endlessparkour;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.endlessparkour.course.CourseGenerator;
import dev.endlessparkour.course.JumpModel;
import dev.endlessparkour.course.Placement;
import dev.endlessparkour.course.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Builds a course in a spare lane (no player needed), reads every block back after a few
 * seconds (so plants or rocks that break for lack of support show up), then clears it again.
 */
final class SelfTest {

    /** Owner of the islands of the flight test (never a player). */
    private static final UUID TEST_OWNER = new UUID(0x5E1F7E57L, 0xF1L);
    /** Time between two islands of the flight test: about ten fly at once. */
    private static final long FLY_STAGGER = 100;

    private SelfTest() {
    }

    static void run(RunManager runs, ParkourConfig cfg, int count, long seed, boolean fly, Consumer<Message> out) {
        World w = runs.world();
        if (w == null) {
            out.accept(Msg.error("The parkour world is not ready."));
            return;
        }
        w.execute(() -> {
            BlockPlacer placer = runs.placer();
            // Blocks are tested far away, out of sight. Flying objects only exist where the game is
            // active, near a player: that test runs well below a player's course.
            int laneZ = -64 * cfg.laneSpacing;
            int startX = 0;
            int baseY = cfg.startY;
            int lowY = cfg.minY;
            int highY = cfg.maxY;
            if (fly) {
                Run near = runs.anyRun();
                if (near == null) {
                    out.accept(Msg.error("The flight test needs a player in the parkour: objects only work near players."));
                    return;
                }
                Platform here = near.get(near.score) != null ? near.get(near.score) : near.first();
                laneZ = here.minZ();
                startX = here.minX();
                baseY = Math.max(28, cfg.minY - 50);
                lowY = baseY - 20;
                highY = baseY + 20;
            }
            CourseGenerator gen = new CourseGenerator(seed, runs.themes(), runs.startTheme(), cfg.levels, laneZ,
                    baseY, lowY, highY, cfg.sectionLengthMin, cfg.sectionLengthMax, cfg.laneDrift);
            List<Platform> list = new ArrayList<>();
            Platform p = gen.start(startX);
            list.add(p);
            while (list.size() < count) {
                p = gen.next(p);
                list.add(p);
            }

            int impossible = 0;
            double maxGap = 0;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            Map<Integer, Integer> dys = new TreeMap<>();
            Map<String, Integer> themes = new LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                Platform b = list.get(i);
                minY = Math.min(minY, b.topY());
                maxY = Math.max(maxY, b.topY());
                themes.merge(b.theme().name(), 1, Integer::sum);
                if (i == 0) {
                    continue;
                }
                Platform a = list.get(i - 1);
                double gap = a.gapTo(b);
                int dy = b.topY() - a.topY();
                maxGap = Math.max(maxGap, gap);
                dys.merge(dy, 1, Integer::sum);
                if (!JumpModel.feasible(gap, dy)) {
                    impossible++;
                }
            }
            List<Placement> all = new ArrayList<>();
            for (Platform pl : list) {
                all.addAll(pl.blocks());
            }
            out.accept(Msg.line().text("Self test: ").value(list.size()).text(" platforms, ").value(all.size())
                    .text(" blocks, seed ").value(seed).build());
            out.accept(Msg.line().text("Jumps out of reach: ").value(impossible)
                    .text(", widest gap ").value(String.format("%.2f", maxGap))
                    .text(", height ").value(minY + ".." + maxY).text(", dy ").value(dys).build());
            out.accept(Msg.line().text("Biomes: ").value(themes).build());
            if (fly) {
                flyTest(runs, cfg, list, out, w);
                return;
            }

            long t0 = System.nanoTime();
            placer.placePlatforms(list, list, cfg.areaColors, cfg.areaMusic).whenComplete((v, err) -> {
                long ms = (System.nanoTime() - t0) / 1_000_000;
                out.accept(Msg.line().text("Placed in ").value(ms + " ms")
                        .text(err == null ? "" : ", error: " + err).build());
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> w.execute(() -> {
                    verify(placer, all, out, w);
                    verifyAreas(placer, cfg, list, out);
                    clear(placer, all, out, w);
                }), 3, TimeUnit.SECONDS);
            });
        });
    }

    /**
     * Lets every island fly in (about ten at once), checks each block at its place and that nothing
     * stayed behind on the ways, then lets them all fly away and checks that nothing is left.
     */
    private static void flyTest(RunManager runs, ParkourConfig cfg, List<Platform> list, Consumer<Message> out, World w) {
        BlockPlacer placer = runs.placer();
        IslandMover mover = runs.mover();
        mover.resetStats();
        Map<Platform, int[]> ways = new IdentityHashMap<>();
        List<Platform> known = new ArrayList<>();
        long start = System.currentTimeMillis() + 200;
        for (Platform p : list) {
            known.add(p);
            IslandMover.Plan plan = mover.chooseWay(p, known, cfg.flyDistance, start, cfg.flyMillis);
            ways.put(p, plan.way());
            placer.prepareFlight(List.of(p), known, plan.way(), cfg.flyDistance, cfg.areaColors, cfg.areaMusic);
            mover.flyIn(TEST_OWNER, p, plan.way(), cfg.flyDistance, plan.start(), cfg.flyMillis,
                    IslandMover.PlantStyle.GROW);
            start = plan.start() + FLY_STAGGER;
        }
        List<Placement> all = new ArrayList<>();
        Set<Long> finals = new HashSet<>();
        for (Platform p : list) {
            all.addAll(p.blocks());
            for (Placement b : p.blocks()) {
                finals.add(RunManager.pack(b.x(), b.y(), b.z()));
            }
        }
        long t0 = System.currentTimeMillis();
        out.accept(Msg.line().text("Flight test: ").value(list.size()).text(" islands fly in, ")
                .value(FLY_STAGGER + " ms").text(" apart").build());
        whenLanded(mover, w, t0, list.size() * FLY_STAGGER * 4 + 20_000, () -> {
            out.accept(Msg.line().text("Landed after ").value((System.currentTimeMillis() - t0) + " ms").build());
            verify(placer, all, out, w);
            int stray = 0;
            for (Platform p : list) {
                int[] d = ways.get(p);
                for (Placement b : p.blocks()) {
                    for (int s = 1; s <= cfg.flyDistance; s++) {
                        int x = b.x() + d[0] * s;
                        int y = b.y() + d[1] * s;
                        int z = b.z() + d[2] * s;
                        if (!finals.contains(RunManager.pack(x, y, z)) && placer.blockAt(x, y, z) > 0) {
                            stray++;
                        }
                    }
                }
            }
            out.accept(Msg.line().text("Blocks on the ways in: ").value(stray).text(", flying objects left: ")
                    .value(mover.objects()).build());
            long t1 = System.currentTimeMillis();
            long leave = t1;
            for (Platform p : list) {
                mover.flyOut(TEST_OWNER, p, cfg.flyDistance, leave, cfg.flyMillis, IslandMover.PlantStyle.GROW);
                leave += FLY_STAGGER;
            }
            whenLanded(mover, w, t1, list.size() * FLY_STAGGER * 4 + 20_000, () -> {
                int left = 0;
                for (Platform p : list) {
                    for (Placement b : p.blocks()) {
                        for (int s = 0; s <= cfg.flyDistance; s++) {
                            if (placer.blockAt(b.x() - s, b.y(), b.z()) > 0) {
                                left++;
                            }
                        }
                    }
                }
                out.accept(Msg.line().text("Flown away after ").value((System.currentTimeMillis() - t1) + " ms")
                        .text(", blocks left: ").value(left).text(", flying objects left: ").value(mover.objects()).build());
                out.accept(Msg.line().text("Flights: ").value(mover.stats()).build());
                EntityBlocks eb = runs.objects();
                out.accept(Msg.line().text("Objects not valid: at spawn ").value(eb.spawnedInvalid).text(", when moved ")
                        .value(eb.lostOnMove).text(", when removed ").value(eb.lostOnRemove).build());
            });
        });
    }

    /** Runs then on the world thread once no test island flies any more (or after timeoutMs). */
    private static void whenLanded(IslandMover mover, World w, long since, long timeoutMs, Runnable then) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> w.execute(() -> {
            if (mover.busy(TEST_OWNER) && System.currentTimeMillis() - since < timeoutMs) {
                whenLanded(mover, w, since, timeoutMs, then);
                return;
            }
            if (mover.busy(TEST_OWNER)) {
                mover.cancel(TEST_OWNER);
            }
            then.run();
        }), 500, TimeUnit.MILLISECONDS);
    }

    private static void verify(BlockPlacer placer, List<Placement> all, Consumer<Message> out, World w) {
        int ok = 0;
        int unloaded = 0;
        Map<String, Integer> wrong = new TreeMap<>();
        List<String> samples = new ArrayList<>();
        for (Placement pl : all) {
            int want = placer.id(pl.block());
            int got = placer.blockAt(pl.x(), pl.y(), pl.z());
            if (got == -1) {
                unloaded++;
            } else if (got == want) {
                ok++;
            } else {
                wrong.merge(pl.block(), 1, Integer::sum);
                if (samples.size() < 6) {
                    samples.add(pl.block() + " at " + pl.x() + " " + pl.y() + " " + pl.z() + " is " + name(got));
                }
            }
        }
        out.accept(Msg.line().text("Check after 3 s: ").value(ok).text(" ok, ").value(wrong.values().stream()
                .mapToInt(Integer::intValue).sum()).text(" wrong, ").value(unloaded).text(" not loaded").build());
        if (!wrong.isEmpty()) {
            out.accept(Msg.line().bad("Wrong blocks: ").value(wrong).build());
            for (String s : samples) {
                out.accept(Msg.plain().gray("  " + s).build());
            }
        }
    }

    /**
     * Every section must keep the environment (music, ambient sounds) of the last area built into it,
     * and every column the grass colour of the last platform in it, as the placer sets them.
     */
    private static void verifyAreas(BlockPlacer placer, ParkourConfig cfg, List<Platform> list, Consumer<Message> out) {
        Map<Long, Integer> wantEnv = new HashMap<>();
        Map<Long, Integer> wantTint = new HashMap<>();
        Map<Long, int[]> sectionBlock = new HashMap<>();
        Map<Long, int[]> columnBlock = new HashMap<>();
        Map<Long, Integer> wantRim = new HashMap<>();
        for (Platform p : list) {
            // The columns around the island get its colour too (where no island of another colour is as near).
            for (long col : BlockPlacer.marginColumns(p, list)) {
                wantRim.put(col, p.tint());
            }
        }
        for (Platform p : list) {
            int env = cfg.areaMusic ? placer.environmentId(p.theme().environment()) : -1;
            for (Placement pl : p.blocks()) {
                long col = ((long) pl.x() << 32) | (pl.z() & 0xFFFFFFFFL);
                wantTint.put(col, p.tint());
                columnBlock.putIfAbsent(col, new int[] {pl.x(), pl.z()});
                if (env >= 0) {
                    long sec = BlockPlacer.packSection(pl.x() >> 5, pl.y() >> 5, pl.z() >> 5);
                    wantEnv.put(sec, env);
                    sectionBlock.putIfAbsent(sec, new int[] {pl.x(), pl.y(), pl.z()});
                }
            }
            if (env >= 0) {
                for (int i = 0; i < p.cellCount(); i++) {
                    int x = p.cellX(i);
                    int y = p.topY() + 3;
                    int z = p.cellZ(i);
                    long sec = BlockPlacer.packSection(x >> 5, y >> 5, z >> 5);
                    wantEnv.put(sec, env);
                    sectionBlock.putIfAbsent(sec, new int[] {x, y, z});
                }
            }
        }
        int envOk = 0;
        int envWrong = 0;
        int unloaded = 0;
        Map<Integer, Integer> areasSeen = new TreeMap<>();
        for (Map.Entry<Long, Integer> e : wantEnv.entrySet()) {
            int[] b = sectionBlock.get(e.getKey());
            int got = placer.sectionEnvironmentAt(b[0], b[1], b[2]);
            if (got == -1) {
                unloaded++;
                continue;
            }
            if (got == e.getValue()) {
                envOk++;
                areasSeen.merge(got, 1, Integer::sum);
            } else {
                envWrong++;
            }
        }
        int tintOk = 0;
        int tintWrong = 0;
        for (Map.Entry<Long, Integer> e : wantTint.entrySet()) {
            int[] c = columnBlock.get(e.getKey());
            int got = placer.tintAt(c[0], c[1]);
            if (got == -1) {
                unloaded++;
            } else if (got == e.getValue()) {
                tintOk++;
            } else {
                tintWrong++;
            }
        }
        int rimOk = 0;
        int rimWrong = 0;
        for (Map.Entry<Long, Integer> e : wantRim.entrySet()) {
            if (wantTint.containsKey(e.getKey())) {
                continue;
            }
            int got = placer.tintAt(TintMargins.x(e.getKey()), TintMargins.z(e.getKey()));
            if (got == -1) {
                unloaded++;
            } else if (got == e.getValue()) {
                rimOk++;
            } else {
                rimWrong++;
            }
        }
        out.accept(Msg.line().text("Areas: environment right in ").value(envOk).text(" sections (")
                .value(areasSeen.size()).text(" kinds), wrong ").value(envWrong).text("; colour right in ").value(tintOk).text(" columns, wrong ")
                .value(tintWrong).text("; rim right in ").value(rimOk).text(" columns, wrong ").value(rimWrong)
                .text(", not loaded ").value(unloaded).build());
    }

    private static void clear(BlockPlacer placer, List<Placement> all, Consumer<Message> out, World w) {
        placer.clear(all);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> w.execute(() -> {
            int left = 0;
            for (Placement pl : all) {
                int got = placer.blockAt(pl.x(), pl.y(), pl.z());
                if (got > 0) {
                    left++;
                }
            }
            out.accept(Msg.line().text("Cleared, blocks left: ").value(left).build());
        }), 2, TimeUnit.SECONDS);
    }

    private static String name(int id) {
        if (id == 0) {
            return "air";
        }
        BlockType t = BlockType.getAssetMap().getAsset(id);
        return t == null ? "#" + id : t.getId();
    }
}
