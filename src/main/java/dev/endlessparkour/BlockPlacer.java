package dev.endlessparkour;

import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection;
import dev.endlessparkour.course.Platform;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.endlessparkour.course.Placement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Sets and clears platform blocks. All calls must run on the world thread. */
final class BlockPlacer implements BlockAccess {

    /** Quiet placement: no sounds, particles or drops. */
    static final int SETTINGS = SetBlockSettings.NO_SEND_AUDIO | SetBlockSettings.NO_SEND_PARTICLES
            | SetBlockSettings.NO_DROP_ITEMS;

    private static final int MAX_ROUNDS = 4;

    private final World world;
    private final HytaleLogger log;
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<String, Integer> envIds = new HashMap<>();
    private final int flipped;
    /** For blocks that lean on a wall (ladders): the rotation for each side of the cell, by block name. */
    private final Map<String, int[]> wallTurns = new HashMap<>();
    private boolean warned;
    private boolean warnedArea;

    BlockPlacer(World world, HytaleLogger log) {
        this.world = world;
        this.log = log;
        this.flipped = RotationTuple.index(Rotation.None, Rotation.OneEighty, Rotation.None);
    }

    /** True when the server knows a block with this name. */
    static boolean exists(String name) {
        int id = BlockType.getAssetMap().getIndex(name);
        return id != AssetMapWithIndexes.NOT_FOUND && id != BlockType.EMPTY_ID && id != BlockType.UNKNOWN_ID;
    }

    int id(String name) {
        return ids.computeIfAbsent(name, n -> {
            int id = BlockType.getAssetMap().getIndex(n);
            return id == AssetMapWithIndexes.NOT_FOUND ? -1 : id;
        });
    }

    /**
     * Places platforms, then gives their columns the area's grass colour and their sections the
     * area's environment (music and ambient sounds), and tells the clients. Completes on the world thread.
     */
    CompletableFuture<Void> placePlatforms(List<Platform> platforms, boolean tints, boolean environments) {
        return placePlatforms(platforms, null, tints, environments);
    }

    /**
     * Like {@link #placePlatforms(List, boolean, boolean)}; with neighbours (the other islands around,
     * may include these) the columns around each island get its colour too ({@link TintMargins}).
     */
    CompletableFuture<Void> placePlatforms(List<Platform> platforms, Collection<Platform> neighbours, boolean tints,
                                           boolean environments) {
        List<Placement> blocks = new ArrayList<>();
        for (Platform p : platforms) {
            blocks.addAll(p.blocks());
        }
        return place(blocks).thenRun(() -> applyAreas(platforms, neighbours, tints, environments, NO_WAY, 0));
    }

    private static final int[] NO_WAY = {0, 0, 0};

    /**
     * Gets islands ready to fly: loads the sections along their way (up to distance blocks along
     * way), colours the grass of the whole way and gives their final place the area environment.
     * Completes on the world thread.
     */
    CompletableFuture<Void> prepareFlight(List<Platform> platforms, int[] way, int distance, boolean tints,
                                          boolean environments) {
        return prepareFlight(platforms, null, way, distance, tints, environments);
    }

    /**
     * Like {@link #prepareFlight(List, int[], int, boolean, boolean)}; with neighbours (the other islands
     * around) the columns around each island get its colour too ({@link TintMargins}), so it looks the
     * same once solid as while flying.
     */
    CompletableFuture<Void> prepareFlight(List<Platform> platforms, Collection<Platform> neighbours, int[] way,
                                          int distance, boolean tints, boolean environments) {
        List<int[]> positions = new ArrayList<>();
        for (Platform p : platforms) {
            for (Placement pl : p.blocks()) {
                for (int s = 0; s < distance; s += 16) {
                    positions.add(new int[] {pl.x() + way[0] * s, pl.y() + way[1] * s, pl.z() + way[2] * s});
                }
                positions.add(new int[] {pl.x() + way[0] * distance, pl.y() + way[1] * distance,
                        pl.z() + way[2] * distance});
            }
        }
        return load(positions).thenRun(() -> applyAreas(platforms, neighbours, tints, environments, way, distance));
    }

    /** Loads the sections of these positions; completes on the world thread. */
    CompletableFuture<Void> load(List<int[]> positions) {
        ChunkStore cs = world.getChunkStore();
        Set<Long> asked = new HashSet<>();
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int[] p : positions) {
            if (!asked.add(packSection(p[0] >> 5, p[1] >> 5, p[2] >> 5))) {
                continue;
            }
            Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(p[0], p[1], p[2]);
            if (sec == null || !sec.isValid()) {
                loads.add(cs.getChunkSectionReferenceAtBlockAsync(p[0], p[1], p[2]));
            }
        }
        if (loads.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture.allOf(loads.toArray(new CompletableFuture<?>[0])).whenComplete((v, err) ->
                world.execute(() -> done.complete(null)));
        return done;
    }

    /** Area colour (also along a flight way of distance blocks) and environment of the islands. */
    private void applyAreas(List<Platform> platforms, Collection<Platform> neighbours, boolean tints,
                            boolean environments, int[] way, int distance) {
        Set<Long> tintChunks = new HashSet<>();
        Set<Long> envSections = new HashSet<>();
        for (Platform p : platforms) {
            try {
                applyArea(p, neighbours, tints, environments, way, distance, tintChunks, envSections);
            } catch (RuntimeException e) {
                if (!warnedArea) {
                    warnedArea = true;
                    log.at(Level.WARNING).withCause(e).log("Could not set the area colour or environment");
                }
            }
        }
        for (long chunk : tintChunks) {
            world.getNotificationHandler().updateChunkTints(chunk);
        }
        for (long sec : envSections) {
            world.getNotificationHandler().updateSectionEnvironments(unpackX(sec), unpackY(sec), unpackZ(sec));
        }
    }

    private void applyArea(Platform p, Collection<Platform> neighbours, boolean tints, boolean environments,
                           int[] way, int distance, Set<Long> tintChunks, Set<Long> envSections) {
        ChunkStore cs = world.getChunkStore();
        Store<ChunkStore> store = cs.getStore();
        if (tints) {
            int rgb = p.tint();
            int argb = ColorParseUtil.colorToARGBInt(new Color((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb));
            // Grass colour is per column: colour every column the island passes on its way too.
            int steps = way[0] == 0 && way[2] == 0 ? 0 : distance;
            Set<Long> columns = new HashSet<>();
            for (Placement pl : p.blocks()) {
                for (int s = 0; s <= steps; s++) {
                    int x = pl.x() + way[0] * s;
                    int z = pl.z() + way[2] * s;
                    columns.add(((long) x << 32) | (z & 0xFFFFFFFFL));
                }
            }
            if (neighbours != null) {
                // Solid grass blends with the columns around it; a flying island has one plain colour.
                columns.addAll(marginColumns(p, neighbours));
            }
            for (long column : columns) {
                int x = (int) (column >> 32);
                int z = (int) column;
                long chunk = ChunkUtil.indexChunkFromBlock(x, z);
                Ref<ChunkStore> ref = cs.getChunkReference(chunk);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                BlockChunk bc = store.getComponent(ref, BlockChunk.getComponentType());
                if (bc != null) {
                    bc.setTint(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK, argb);
                    tintChunks.add(chunk);
                }
            }
        }
        int env = environments ? environmentId(p.theme().environment()) : -1;
        if (env >= 0) {
            Set<Long> sections = new HashSet<>();
            for (Placement pl : p.blocks()) {
                sections.add(packSection(pl.x() >> 5, pl.y() >> 5, pl.z() >> 5));
            }
            // The space players move through above the surface as well.
            for (int i = 0; i < p.cellCount(); i++) {
                sections.add(packSection(p.cellX(i) >> 5, (p.topY() + 3) >> 5, p.cellZ(i) >> 5));
            }
            for (long sec : sections) {
                Ref<ChunkStore> ref = cs.getChunkSectionReference(unpackX(sec), unpackY(sec), unpackZ(sec));
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                EnvironmentSection es = store.getComponent(ref, EnvironmentSection.getComponentType());
                if (es != null && es.setUniform(env)) {
                    envSections.add(sec);
                }
            }
        }
    }

    /** The island's columns and those around it that get its colour ({@link TintMargins}). */
    static Set<Long> marginColumns(Platform p, Collection<Platform> neighbours) {
        Set<Long> own = new HashSet<>();
        for (Placement pl : p.blocks()) {
            own.add(TintMargins.pack(pl.x(), pl.z()));
        }
        List<Set<Long>> others = new ArrayList<>();
        for (Platform q : neighbours) {
            if (q == p || q.tint() == p.tint()) {
                continue;
            }
            Set<Long> qc = new HashSet<>();
            for (Placement pl : q.blocks()) {
                qc.add(TintMargins.pack(pl.x(), pl.z()));
            }
            others.add(qc);
        }
        return TintMargins.columns(own, others, TintMargins.RADIUS);
    }

    int environmentId(String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }
        return envIds.computeIfAbsent(name, n -> {
            int id = Environment.getAssetMap().getIndex(n);
            return id == AssetMapWithIndexes.NOT_FOUND ? -1 : id;
        });
    }

    /** The environment id the section at this block keeps, or -1 when it is not loaded. */
    int sectionEnvironmentAt(int x, int y, int z) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> ref = cs.getChunkSectionReferenceAtBlock(x, y, z);
        if (ref == null || !ref.isValid()) {
            return -1;
        }
        EnvironmentSection es = cs.getStore().getComponent(ref, EnvironmentSection.getComponentType());
        return es == null ? -1 : es.get(x & ChunkUtil.SIZE_MASK, y & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK);
    }

    /** The grass colour of a column as 0xRRGGBB, or -1 when its chunk is not loaded. */
    int tintAt(int x, int z) {
        BlockChunk bc = blockChunk(x, z);
        return bc == null ? -1 : bc.getTint(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK) & 0xFFFFFF;
    }

    private BlockChunk blockChunk(int x, int z) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> ref = cs.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return cs.getStore().getComponent(ref, BlockChunk.getComponentType());
    }

    /** Section coordinates in one long: 21 bits x, 21 bits z, 22 bits y (all signed). */
    static long packSection(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) << 43 | ((long) z & 0x1FFFFFL) << 22 | ((long) y & 0x3FFFFFL);
    }

    static int unpackX(long s) {
        int v = (int) ((s >>> 43) & 0x1FFFFFL);
        return (v << 11) >> 11;
    }

    static int unpackZ(long s) {
        int v = (int) ((s >>> 22) & 0x1FFFFFL);
        return (v << 11) >> 11;
    }

    static int unpackY(long s) {
        int v = (int) (s & 0x3FFFFFL);
        return (v << 10) >> 10;
    }

    /** Places the blocks, solid ones first. Completes on the world thread. */
    CompletableFuture<Void> place(List<Placement> blocks) {
        List<Placement> ordered = new ArrayList<>(blocks.size());
        for (Placement p : blocks) {
            if (!p.decor()) {
                ordered.add(p);
            }
        }
        for (Placement p : blocks) {
            if (p.decor()) {
                ordered.add(p);
            }
        }
        return apply(ordered, false, 0);
    }

    /**
     * Removes the blocks, decoration first. Only touches loaded chunks: the parkour world does
     * not save chunks, so an unloaded chunk comes back empty anyway. Loading it just to clear it
     * could also race with a new course built at the same spot.
     */
    void clear(List<Placement> blocks) {
        ChunkStore cs = world.getChunkStore();
        for (int pass = 0; pass < 2; pass++) {
            boolean decorPass = pass == 0;
            for (Placement p : blocks) {
                if (p.decor() != decorPass) {
                    continue;
                }
                Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
                if (sec != null && sec.isValid()) {
                    set(cs, sec, p.x(), p.y(), p.z(), p, true);
                }
            }
        }
    }

    /** The block id at a position, or -1 when its chunk is not loaded. */
    @Override
    public int blockAt(int x, int y, int z) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(x, y, z);
        if (sec == null || !sec.isValid()) {
            return -1;
        }
        BlockSection bs = cs.getStore().getComponent(sec, BlockSection.getComponentType());
        return bs == null ? -1 : bs.get(x, y, z);
    }

    /** The rotation of the block at a position, or -1 when its section is not loaded. */
    int rotationAt(int x, int y, int z) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(x, y, z);
        if (sec == null || !sec.isValid()) {
            return -1;
        }
        BlockSection bs = cs.getStore().getComponent(sec, BlockSection.getComponentType());
        return bs == null ? -1 : bs.getRotationIndex(x, y, z);
    }

    @Override
    public boolean place(Placement p, int x, int y, int z) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(x, y, z);
        if (sec == null || !sec.isValid()) {
            return false;
        }
        set(cs, sec, x, y, z, p, false);
        return true;
    }

    @Override
    public boolean clear(int x, int y, int z) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(x, y, z);
        if (sec == null || !sec.isValid()) {
            return false;
        }
        try {
            BlockOperations.setBlock(cs, sec, x, y, z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SETTINGS);
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                log.at(Level.WARNING).withCause(e).log("Could not clear %d %d %d", x, y, z);
            }
        }
        return true;
    }

    private CompletableFuture<Void> apply(List<Placement> list, boolean clear, int round) {
        ChunkStore cs = world.getChunkStore();
        // Everything at once when all sections are there, so plants never come before their ground.
        List<Placement> missing = new ArrayList<>();
        for (Placement p : list) {
            Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
            if (sec == null || !sec.isValid()) {
                missing.add(p);
            }
        }
        if (missing.isEmpty() || round >= MAX_ROUNDS) {
            int skipped = 0;
            for (Placement p : list) {
                Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
                if (sec == null || !sec.isValid()) {
                    skipped++;
                    continue;
                }
                set(cs, sec, p.x(), p.y(), p.z(), p, clear);
            }
            if (skipped > 0) {
                log.at(Level.WARNING).log("%d blocks skipped, their chunks did not load", skipped);
            }
            return CompletableFuture.completedFuture(null);
        }
        Set<Long> asked = new HashSet<>();
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (Placement p : missing) {
            long key = ((long) (p.x() >> 4) & 0x3FFFFFL) << 42 | ((long) (p.z() >> 4) & 0x3FFFFFL) << 20
                    | ((long) (p.y() >> 4) & 0xFFFFFL);
            if (asked.add(key)) {
                loads.add(cs.getChunkSectionReferenceAtBlockAsync(p.x(), p.y(), p.z()));
            }
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture.allOf(loads.toArray(new CompletableFuture<?>[0])).whenComplete((v, err) ->
                world.execute(() -> apply(list, clear, round + 1).whenComplete((v2, err2) -> {
                    if (err2 != null) {
                        done.completeExceptionally(err2);
                    } else {
                        done.complete(null);
                    }
                })));
        return done;
    }

    /** The rotation of a placed block: upside down, turned to lean on its wall, or as it comes. */
    int rotation(Placement p, BlockType type) {
        if (p.flipped()) {
            return flipped;
        }
        if (!p.attached()) {
            return 0;
        }
        return wallTurns.computeIfAbsent(p.block(), n -> wallTurns(type))[p.facing()];
    }

    /**
     * For each side of a cell (+X, +Z, -X, -Z, as in {@link Placement}), the turn that puts the block's
     * hitbox against that side: a ladder's board against the wall it leans on. Taken from the game's own
     * turned hitboxes, so it needs no knowledge of which way a turn goes.
     */
    static int[] wallTurns(BlockType type) {
        int[] out = {-1, -1, -1, -1};
        BlockBoundingBoxes boxes = type == null ? null : BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        if (boxes != null) {
            for (Rotation yaw : Rotation.VALUES) {
                Box b = boxes.get(yaw, Rotation.None, Rotation.None).getBoundingBox();
                int side = side(b);
                if (side >= 0 && out[side] < 0) {
                    out[side] = RotationTuple.index(yaw, Rotation.None, Rotation.None);
                }
            }
        }
        for (int i = 0; i < out.length; i++) {
            if (out[i] < 0) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** The side of its cell a box lies against (+X, +Z, -X, -Z as in {@link Placement}), -1 when it is in the middle. */
    static int side(Box b) {
        double cx = (b.min.x + b.max.x) / 2 - 0.5;
        double cz = (b.min.z + b.max.z) / 2 - 0.5;
        if (Math.abs(cx) < 0.2 && Math.abs(cz) < 0.2) {
            return -1;
        }
        if (Math.abs(cx) >= Math.abs(cz)) {
            return cx > 0 ? Placement.EAST : Placement.WEST;
        }
        return cz > 0 ? Placement.SOUTH : Placement.NORTH;
    }

    /** Sets (or clears) the block of p at (x, y, z) instead of its own position. */
    private void set(ChunkStore cs, Ref<ChunkStore> sec, int x, int y, int z, Placement p, boolean clear) {
        try {
            if (clear) {
                BlockOperations.setBlock(cs, sec, x, y, z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SETTINGS);
                return;
            }
            int id = id(p.block());
            if (id < 0) {
                return;
            }
            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (type == null) {
                return;
            }
            BlockOperations.setBlock(cs, sec, x, y, z, id, type, rotation(p, type), 0, SETTINGS);
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                log.at(Level.WARNING).withCause(e).log("Could not set %s at %d %d %d", p.block(), x, y, z);
            }
        }
    }
}
