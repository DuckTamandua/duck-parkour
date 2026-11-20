package dev.endlessparkour;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.CancellableEcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.Supplier;

/** Nobody can break, damage or place blocks in the parkour world (Adventure mode allows mining). */
final class Protection {

    private Protection() {
    }

    static void register(EndlessParkourPlugin plugin, Supplier<World> parkour) {
        // The server keys systems by class, so every event needs its own class.
        plugin.getEntityStoreRegistry().registerSystem(new NoBreak(parkour));
        plugin.getEntityStoreRegistry().registerSystem(new NoDamage(parkour));
        plugin.getEntityStoreRegistry().registerSystem(new NoPlace(parkour));
    }

    static final class NoBreak extends Deny<BreakBlockEvent> {
        NoBreak(Supplier<World> parkour) {
            super(BreakBlockEvent.class, parkour);
        }
    }

    static final class NoDamage extends Deny<DamageBlockEvent> {
        NoDamage(Supplier<World> parkour) {
            super(DamageBlockEvent.class, parkour);
        }
    }

    static final class NoPlace extends Deny<PlaceBlockEvent> {
        NoPlace(Supplier<World> parkour) {
            super(PlaceBlockEvent.class, parkour);
        }
    }

    /** Cancels one kind of block event when it happens in the parkour world. */
    abstract static class Deny<E extends CancellableEcsEvent> extends EntityEventSystem<EntityStore, E> {

        private final Supplier<World> parkour;

        Deny(Class<E> type, Supplier<World> parkour) {
            super(type);
            this.parkour = parkour;
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> buffer, E event) {
            World w = parkour.get();
            if (w != null && store.getExternalData().getWorld() == w) {
                event.setCancelled(true);
            }
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }
}
