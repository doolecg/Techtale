package com.doolecg.techtale.hytale.machine;

import com.doolecg.techtale.core.machine.MachineInventory;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import javax.annotation.Nullable;

/** Exposes a Hytale ItemContainer to the machine logic. */
public final class ContainerInventory implements MachineInventory {
    private final ItemContainer container;

    public ContainerInventory(ItemContainer container) {
        this.container = container;
    }

    @Nullable
    private ItemStack stack(int slot) {
        ItemStack s = container.getItemStack((short) slot);
        return ItemStack.isEmpty(s) ? null : s;
    }

    @Nullable
    @Override
    public String itemId(int slot) {
        ItemStack s = stack(slot);
        return s == null ? null : s.getItemId();
    }

    @Override
    public int count(int slot) {
        ItemStack s = stack(slot);
        return s == null ? 0 : s.getQuantity();
    }

    @Override
    public int roomFor(int slot, String itemId) {
        ItemStack s = stack(slot);
        int max = maxStack(itemId);
        if (s == null) {
            return max;
        }
        return s.getItemId().equals(itemId) ? Math.max(0, max - s.getQuantity()) : 0;
    }

    @Override
    public void remove(int slot, int count) {
        ItemStack s = stack(slot);
        if (s == null) {
            return;
        }
        int left = s.getQuantity() - count;
        container.setItemStackForSlot((short) slot, left > 0 ? s.withQuantity(left) : ItemStack.EMPTY, false);
    }

    @Override
    public void add(int slot, String itemId, int count) {
        ItemStack s = stack(slot);
        ItemStack next = s == null ? new ItemStack(itemId, count) : s.withQuantity(s.getQuantity() + count);
        container.setItemStackForSlot((short) slot, next, false);
    }

    private static int maxStack(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? 0 : item.getMaxStack();
    }
}
