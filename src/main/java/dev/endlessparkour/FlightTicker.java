package dev.endlessparkour;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.Supplier;
import java.util.logging.Level;

/** Moves the flying islands once every world tick (30 times a second), in the parkour world only. */
final class FlightTicker extends TickingSystem<EntityStore> {

    private final Supplier<World> parkour;
    private final Supplier<IslandMover> mover;
    private final HytaleLogger log;
    private boolean warned;

    FlightTicker(Supplier<World> parkour, Supplier<IslandMover> mover, HytaleLogger log) {
        this.parkour = parkour;
        this.mover = mover;
        this.log = log;
    }

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        World w = parkour.get();
        IslandMover m = mover.get();
        if (w == null || m == null || store.getExternalData().getWorld() != w) {
            return;
        }
        try {
            m.tick(System.currentTimeMillis());
        } catch (RuntimeException e) {
            // Never let a flight break the world tick.
            if (!warned) {
                warned = true;
                log.at(Level.WARNING).withCause(e).log("Island flight failed");
            }
        }
    }
}
