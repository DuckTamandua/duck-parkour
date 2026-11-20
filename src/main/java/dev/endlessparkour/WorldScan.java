package dev.endlessparkour;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * /parkour scan: reads every loaded block of the parkour world and counts the ones that do not
 * belong to a running course (left over from old courses). Unloaded chunks are not saved in this
 * world, so they come back empty and need no check.
 */
final class WorldScan {

    private WorldScan() {
    }

    static void run(RunManager runs, Consumer<Message> out) {
        World w = runs.world();
        if (w == null) {
            out.accept(Msg.error("The parkour world is not ready."));
            return;
        }
        w.execute(() -> {
            Set<Long> live = runs.livePositions();
            ChunkStore cs = w.getChunkStore();
            Store<ChunkStore> store = cs.getStore();
            int chunks = 0;
            int sections = 0;
            long found = 0;
            long liveFound = 0;
            long leftover = 0;
            List<String> samples = new ArrayList<>();
            LongIterator it = cs.getChunkIndexes().iterator();
            while (it.hasNext()) {
                long index = it.nextLong();
                int cx = ChunkUtil.xOfChunkIndex(index);
                int cz = ChunkUtil.zOfChunkIndex(index);
                chunks++;
                for (Ref<ChunkStore> ref : cs.getLoadedSectionReferences(cx, cz)) {
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    BlockSection blocks = store.getComponent(ref, BlockSection.getComponentType());
                    ChunkSection info = store.getComponent(ref, ChunkSection.getComponentType());
                    if (blocks == null || info == null) {
                        continue;
                    }
                    sections++;
                    if (blocks.isSolidAir()) {
                        continue;
                    }
                    for (int i = 0; i < ChunkUtil.SIZE_BLOCKS; i++) {
                        int id = blocks.get(i);
                        if (id == 0) {
                            continue;
                        }
                        int x = cx * ChunkUtil.SIZE + ChunkUtil.xFromIndex(i);
                        int y = info.getY() * ChunkUtil.SIZE + ChunkUtil.yFromIndex(i);
                        int z = cz * ChunkUtil.SIZE + ChunkUtil.zFromIndex(i);
                        found++;
                        if (live.contains(RunManager.pack(x, y, z))) {
                            liveFound++;
                        } else {
                            leftover++;
                            if (samples.size() < 5) {
                                BlockType t = BlockType.getAssetMap().getAsset(id);
                                samples.add((t == null ? "#" + id : t.getId()) + " at " + x + " " + y + " " + z);
                            }
                        }
                    }
                }
            }
            out.accept(Msg.line().text("Scan: ").value(chunks).text(" chunks, ").value(sections)
                    .text(" sections loaded, ").value(found).text(" blocks").build());
            out.accept(Msg.line().text("Running courses: ").value(liveFound).text(" of ").value(live.size())
                    .text(" blocks found, left over from old courses: ").value(leftover).build());
            for (String s : samples) {
                out.accept(Msg.plain().gray("  " + s).build());
            }
        });
    }
}
