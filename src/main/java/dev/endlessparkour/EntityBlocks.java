package dev.endlessparkour;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.endlessparkour.course.Placement;
import org.joml.Vector3d;

/**
 * Flying blocks as Hytale block entities: no gravity (no velocity), nothing collides with them, and
 * they vanish on their own after a while should a flight ever be left behind. World thread only.
 */
final class EntityBlocks implements FlyingBlocks {

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
