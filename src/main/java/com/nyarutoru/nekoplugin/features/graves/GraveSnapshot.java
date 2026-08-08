package com.nyarutoru.nekoplugin.features.graves;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

record GraveSnapshot(
    UUID id,
    UUID ownerId,
    String ownerName,
    GravePosition deathPosition,
    GravePosition gravePosition,
    byte[] items,
    int experience,
    long createdAt,
    long expiresAt,
    Grave.State state,
    Grave.Disposition disposition
) {
    GraveSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(deathPosition, "deathPosition");
        Objects.requireNonNull(gravePosition, "gravePosition");
        items = Arrays.copyOf(items, items.length);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(disposition, "disposition");
    }

    @Override
    public byte[] items() {
        return Arrays.copyOf(items, items.length);
    }
}
