package dev.endlessparkour;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.endlessparkour.course.Placement;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Flying blocks as Hytale block entities: no gravity (no velocity), nothing collides with them, and
 * they vanish on their own after a while should a flight ever be left behind. World thread only.
 */
final class EntityBlocks implements FlyingBlocks {

    /**
     * Farthest a flying object is ever from its player: hidden at the bottom of the world below an island
     * far ahead while the player runs at the top of the height band is about 185 blocks.
     */
    static final double SEND_RANGE = 256;

    private final World world;
    private final Rotation3f upsideDown;
    /** Objects that were already gone (no longer valid) when they should be removed or moved. */
    long lostOnRemove;
    long lostOnMove;
    long spawnedInvalid;

    EntityBlocks(World world) {
        this.world = world;
        RotationTuple t = RotationTuple.get(RotationTuple.index(Rotation.None, Rotation.OneEighty, Rotation.None));
        this.upsideDown = new Rotation3f((float) t.pitch().getRadians(), (float) t.yaw().getRadians(),
                (float) t.roll().getRadians());
    }

    @Override
    public Object spawn(Placement b, double x, double y, double z, float scale, long lifetimeMs) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Holder<EntityStore> h = EntityStore.REGISTRY.newHolder();
        h.addComponent(BlockEntity.getComponentType(), new BlockEntity(b.block()));
        h.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(new Vector3d(x, y, z), b.flipped() ? upsideDown : new Rotation3f(0f, 0f, 0f)));
        h.ensureComponent(UUIDComponent.getComponentType());
        h.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale));
        double box = sendBoxSize(EntityTrackerSystems.LODCull.ENTITY_LOD_RATIO, SEND_RANGE, IslandMover.MIN_SCALE);
        h.addComponent(BoundingBox.getComponentType(), new BoundingBox(Box.horizontallyCentered(box, box, box)));
        h.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        h.addComponent(DespawnComponent.getComponentType(), DespawnComponent.despawnInMilliseconds(time, lifetimeMs));
        Ref<EntityStore> ref = store.addEntity(h, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            // Outside the area the game keeps active (far from players) the object is put away in its
            // chunk instead; it comes back only when the chunk wakes up, and then vanishes by itself.
            spawnedInvalid++;
            return null;
        }
        return ref;
    }

    /**
     * Size of the box flying objects get instead of their block's hitbox. Hytale leaves out of a player's
     * game every object whose box (scaled with the object, and only from the object's second tick on) is
     * thinner than lodRatio x distance² ("LOD cull"): with the block's own box a tiny object some dozen
     * blocks away would reach the game in its first tick, vanish from it in the next and come back in
     * the middle of its flight, so the game would build it twice. With this box an object stays in the
     * game from its first tick to its last, at any size from minScale up and up to range away. The box
     * is used for nothing else: the objects are intangible, so collisions and aiming ignore them.
     */
    static double sendBoxSize(double lodRatio, double range, double minScale) {
        double size = lodRatio * range * range / minScale * 1.5;
        return Math.max(1.0, Math.min(10_000.0, size));
    }

    @Override
    public Predicate<Object> sentTo(UUID player) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr == null) {
            return null;
        }
        Ref<EntityStore> ref = pr.getReference();
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (ref == null || !ref.isValid() || ref.getStore() != store) {
            return null;
        }
        EntityTrackerSystems.EntityViewer viewer = store.getComponent(ref, EntityTrackerSystems.EntityViewer.getComponentType());
        if (viewer == null || viewer.sent == null) {
            return null;
        }
        // What the tracker sent to this player and has not taken back: what the player's game has.
        var sent = viewer.sent;
        return handle -> handle != null && sent.containsKey(handle);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void move(Object handle, double x, double y, double z, float scale) {
        Ref<EntityStore> ref = (Ref<EntityStore>) handle;
        if (ref == null || !ref.isValid()) {
            lostOnMove++;
            return;
        }
        Store<EntityStore> store = ref.getStore();
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t != null) {
            t.setPosition(new Vector3d(x, y, z));
        }
        EntityScaleComponent s = store.getComponent(ref, EntityScaleComponent.getComponentType());
        if (s != null && s.getScale() != scale) {
            s.setScale(scale);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void remove(Object handle) {
        Ref<EntityStore> ref = (Ref<EntityStore>) handle;
        if (ref != null && ref.isValid()) {
            ref.getStore().removeEntity(ref, RemoveReason.REMOVE);
        } else {
            lostOnRemove++;
        }
    }
}
