package dev.endlessparkour;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The dash sword every player holds in the parkour: hold attack to charge it, let go to dash forward
 * (its item and interactions are in the plugin's asset pack, Server/Item/.../EndlessParkour). World thread.
 */
final class DashSword {

    static final String ITEM = "EndlessParkour_Dash_Sword";

    private DashSword() {
    }

    /** True when the server knows the sword (the plugin's asset pack is loaded). */
    static boolean available() {
        try {
            return new ItemStack(ITEM, 1).isValid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Puts the sword into the player's hand: the hotbar slot it is in, else the first free one. With a full
     * hotbar it takes the first slot only when takeSlot is set (a parkour-only server). True when it is in hand.
     */
    static boolean give(Ref<EntityStore> ref, boolean takeSlot) {
        Store<EntityStore> store = ref.getStore();
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return false;
        }
        ItemContainer inv = hotbar.getInventory();
        short found = -1;
        short free = -1;
        for (short s = 0; s < inv.getCapacity(); s++) {
            ItemStack st = inv.getItemStack(s);
            if (ItemStack.isEmpty(st)) {
                if (free < 0) {
                    free = s;
                }
            } else if (ITEM.equals(st.getItemId())) {
                found = s;
                break;
            }
        }
        if (found < 0) {
            short slot = free >= 0 ? free : takeSlot ? (short) 0 : (short) -1;
            if (slot < 0) {
                return false;
            }
            inv.setItemStackForSlot(slot, new ItemStack(ITEM, 1));
            found = slot;
        }
        if (hotbar.getActiveSlot() != found) {
            hotbar.setActiveSlot((byte) found, ref, store);
        }
        return true;
    }

    /** Takes every dash sword out of the player's inventory (when they leave the parkour on a lobby server). */
    static void take(Ref<EntityStore> ref) {
        CombinedItemContainer all = InventoryComponent.getCombined(ref.getStore(), ref, InventoryComponent.EVERYTHING);
        if (all == null) {
            return;
        }
        for (short s = 0; s < all.getCapacity(); s++) {
            ItemStack st = all.getItemStack(s);
            if (!ItemStack.isEmpty(st) && ITEM.equals(st.getItemId())) {
                all.removeItemStackFromSlot(s);
            }
        }
    }
}
