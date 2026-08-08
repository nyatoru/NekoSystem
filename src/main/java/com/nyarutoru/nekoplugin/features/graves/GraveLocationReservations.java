package com.nyarutoru.nekoplugin.features.graves;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class GraveLocationReservations {
    private final Set<String> keys = ConcurrentHashMap.newKeySet();

    boolean reserve(GravePosition position) { return keys.add(position.key()); }
    void release(GravePosition position) { keys.remove(position.key()); }
    boolean contains(GravePosition position) { return keys.contains(position.key()); }
}
