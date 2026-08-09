package com.nyarutoru.nekoplugin.features.graves;

import org.bukkit.inventory.ItemStack;

import java.util.List;

final class GraveInventoryCapacity {
    private GraveInventoryCapacity() {}

    static boolean canFit(ItemStack[] storageContents, int inventoryMaxStackSize, List<ItemStack> items) {
        ItemStack[] simulated = cloneContents(storageContents);
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;
            int remaining = item.getAmount();
            int maxStackSize = Math.min(inventoryMaxStackSize, item.getMaxStackSize());
            for (ItemStack stored : simulated) {
                if (stored == null || stored.isEmpty() || !stored.isSimilar(item)) continue;
                int space = Math.max(0, maxStackSize - stored.getAmount());
                int added = Math.min(space, remaining);
                stored.setAmount(stored.getAmount() + added);
                remaining -= added;
                if (remaining == 0) break;
            }
            for (int slot = 0; remaining > 0 && slot < simulated.length; slot++) {
                if (simulated[slot] != null && !simulated[slot].isEmpty()) continue;
                int added = Math.min(maxStackSize, remaining);
                simulated[slot] = item.clone();
                simulated[slot].setAmount(added);
                remaining -= added;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            copy[slot] = item == null ? null : item.clone();
        }
        return copy;
    }
}
