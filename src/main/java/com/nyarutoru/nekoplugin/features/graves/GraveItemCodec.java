package com.nyarutoru.nekoplugin.features.graves;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

final class GraveItemCodec {
    private GraveItemCodec() {}

    static byte[] encode(List<ItemStack> items) {
        return ItemStack.serializeItemsAsBytes(items);
    }

    static List<ItemStack> decode(byte[] data) {
        return Arrays.stream(ItemStack.deserializeItemsFromBytes(data)).map(ItemStack::clone).toList();
    }
}
